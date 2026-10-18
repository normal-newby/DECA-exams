package ca.exampractice.exampractice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a DECA cluster exam PDF (100 questions followed by a descriptive answer key).
 * Same algorithm as pdfreader/exam_parser.py; keep the two in sync.
 */
@Component
public class ExamPdfParser {

    public record ParsedQuestion(int number, String question, List<String> answers, String correctAnswer,
                                 String description, String specificIA, String ia) {}

    public record ParsedExam(String cluster, List<ParsedQuestion> questions, List<String> warnings) {}

    public static final Map<String, String> IAS = Map.ofEntries(
            Map.entry("BL", "business law"), Map.entry("CC", "compliance"),
            Map.entry("CM", "channel management"), Map.entry("CO", "communications"),
            Map.entry("CR", "customer relations"), Map.entry("EC", "econ"),
            Map.entry("EI", "emotional intelligence"), Map.entry("EN", "entrepreneurship"),
            Map.entry("FI", "financial analysis"), Map.entry("FM", "financial-information management"),
            Map.entry("HR", "human resources management"), Map.entry("KM", "knowledge management"),
            Map.entry("MK", "marketing"), Map.entry("MN", "market planning"),
            Map.entry("MP", "marketing-information management"), Map.entry("NF", "information management"),
            Map.entry("OP", "operations"), Map.entry("PD", "professional development"),
            Map.entry("PI", "product/service management"), Map.entry("PJ", "project management"),
            Map.entry("PM", "pricing"), Map.entry("PR", "promotion"),
            Map.entry("QM", "quality management"), Map.entry("RM", "risk management"),
            Map.entry("SE", "selling"), Map.entry("SM", "strategic management"));

    // Header text in the PDF -> cluster name used by the site. Longest names first so suffix matching is unambiguous.
    private static final Map<String, String> CLUSTERS = new LinkedHashMap<>();
    static {
        CLUSTERS.put("BUSINESS MANAGEMENT AND ADMINISTRATION", "Business Management");
        CLUSTERS.put("BUSINESS ADMINISTRATION CORE", "Principles");
        CLUSTERS.put("PERSONAL FINANCIAL LITERACY", "Personal Finance");
        CLUSTERS.put("HOSPITALITY AND TOURISM", "Hospitality");
        CLUSTERS.put("ENTREPRENEURSHIP", "Entrepreneurship");
        CLUSTERS.put("MARKETING", "Marketing");
        CLUSTERS.put("FINANCE", "Finance");
    }

    // Page headers/footers, page numbers and download watermarks that get mixed into the text.
    private static final Pattern NOISE = Pattern.compile(
            "^(Test \\d+\\b.*|.*Copyright ©.*|.*MBA Research and Curriculum Center.*|\\d{1,3}"
                    + "|Downloaded by .*|lOMoARcPSD.*|.{0,50}(CLUSTER EXAM|Cluster Exam).{0,20})$");
    private static final Pattern KEY_LINE = Pattern.compile("^(\\d{1,3})\\.\\s*([A-D])$");
    private static final Pattern OPTION = Pattern.compile("^([A-D])\\.\\s+(.*)$");
    private static final Pattern INLINE_OPTION = Pattern.compile("(?:^|\\s)([A-D])\\.\\s+");
    private static final Pattern SOURCE_CODE = Pattern.compile("SOURCE:\\s*([A-Z]{2}):?\\s*(\\d{3})");
    private static final Pattern CLUSTER_HDR = Pattern.compile("([A-Z][A-Z ]+?) (?:CLUSTER|CAREER CLUSTER|CORE) EXAM");

    public ParsedExam parse(byte[] pdf, String clusterOverride) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return parseText(stripper.getText(doc), clusterOverride);
        }
    }

    public ParsedExam parseText(String text, String clusterOverride) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String ln = raw.replaceAll("\\s+", " ").strip();
            if (!ln.isEmpty() && !NOISE.matcher(ln).matches()) lines.add(ln);
        }

        int keyStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = KEY_LINE.matcher(lines.get(i));
            if (m.matches() && m.group(1).equals("1")) { keyStart = i; break; }
        }
        if (keyStart < 0) {
            throw new IllegalArgumentException("Couldn't find the answer key (a line like \"1. B\"). Is this a DECA cluster exam with its key?");
        }
        List<String> qLines = lines.subList(0, keyStart);
        // Drop the answer key's cover page (starts with a bare "KEY" line) so it doesn't stick to question 100.
        int keyCover = qLines.lastIndexOf("KEY");
        if (keyCover >= 0) qLines = qLines.subList(0, keyCover);
        List<String> kLines = lines.subList(keyStart, lines.size());

        // Split at "1.", "2.", ... in strict order, so numbers inside question text are ignored.
        Map<Integer, List<String>> qBlocks = new LinkedHashMap<>();
        int n = 1;
        Integer current = null;
        for (String ln : qLines) {
            Matcher m = Pattern.compile("^" + n + "\\.\\s+(.*)$").matcher(ln);
            if (m.matches()) {
                current = n++;
                qBlocks.put(current, new ArrayList<>(List.of(m.group(1))));
            } else if (current != null) {
                qBlocks.get(current).add(ln);
            }
        }

        Map<Integer, String> keyLetters = new HashMap<>();
        Map<Integer, List<String>> keyBodies = new HashMap<>();
        int expected = 1;
        current = null;
        for (String ln : kLines) {
            Matcher m = KEY_LINE.matcher(ln);
            if (m.matches() && Integer.parseInt(m.group(1)) == expected) {
                current = expected++;
                keyLetters.put(current, m.group(2));
                keyBodies.put(current, new ArrayList<>());
            } else if (current != null) {
                keyBodies.get(current).add(ln);
            }
        }

        List<ParsedQuestion> questions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (var entry : qBlocks.entrySet()) {
            int num = entry.getKey();
            List<String> stem = new ArrayList<>();
            Map<String, List<String>> opts = new LinkedHashMap<>();
            String last = null;
            for (String ln : entry.getValue()) {
                Matcher m = OPTION.matcher(ln);
                if (m.matches() && !opts.containsKey(m.group(1))) {
                    // Two-column layouts put two options on one line: "A. awarding damages C. providing notice"
                    Matcher im = INLINE_OPTION.matcher(ln);
                    List<int[]> marks = new ArrayList<>();
                    while (im.find()) marks.add(new int[]{im.start(1), im.end()});
                    for (int i = 0; i < marks.size(); i++) {
                        String label = ln.substring(marks.get(i)[0], marks.get(i)[0] + 1);
                        int bodyEnd = i + 1 < marks.size() ? marks.get(i + 1)[0] : ln.length();
                        String body = ln.substring(marks.get(i)[1], bodyEnd).strip();
                        if (opts.containsKey(label)) {
                            opts.get(last).add(label + ". " + body);
                        } else {
                            last = label;
                            opts.put(label, new ArrayList<>(List.of(body)));
                        }
                    }
                } else if (last != null) {
                    opts.get(last).add(ln);
                } else {
                    stem.add(ln);
                }
            }
            List<String> answers = new ArrayList<>();
            for (String l : List.of("A", "B", "C", "D")) answers.add(String.join(" ", opts.getOrDefault(l, List.of())).strip());

            String correct = keyLetters.getOrDefault(num, "");
            String description = "", specificIA = "", ia = "";
            if (keyBodies.containsKey(num)) {
                String joined = String.join("\n", keyBodies.get(num));
                int cut = joined.indexOf("SOURCE:");
                description = (cut >= 0 ? joined.substring(0, cut) : joined).replaceAll("\\s+", " ").strip();
                Matcher sm = SOURCE_CODE.matcher(joined);
                if (sm.find()) {
                    specificIA = sm.group(1) + ":" + sm.group(2);
                    ia = IAS.getOrDefault(sm.group(1), "unknown (" + sm.group(1) + ")");
                }
            }
            String question = String.join(" ", stem).strip();

            List<String> problems = new ArrayList<>();
            if (question.isEmpty()) problems.add("empty question");
            StringBuilder missing = new StringBuilder();
            for (int i = 0; i < 4; i++) if (answers.get(i).isEmpty()) missing.append("ABCD".charAt(i));
            if (!missing.isEmpty()) problems.add("missing option(s) " + missing);
            if (correct.isEmpty()) problems.add("no answer in key");
            if (specificIA.isEmpty()) problems.add("no SOURCE code");
            if (!problems.isEmpty()) warnings.add("Q" + num + ": " + String.join(", ", problems));

            questions.add(new ParsedQuestion(num, question, answers, correct, description, specificIA, ia));
        }
        if (questions.size() != keyLetters.size()) {
            warnings.add("found " + questions.size() + " questions but " + keyLetters.size() + " answers in key");
        }
        String cluster = clusterOverride != null && !clusterOverride.isBlank()
                ? clusterOverride.strip()
                : Optional.ofNullable(detectCluster(text)).orElse("Unknown");
        return new ParsedExam(cluster, questions, warnings);
    }

    static String detectCluster(String text) {
        Matcher m = CLUSTER_HDR.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            for (var e : CLUSTERS.entrySet()) if (name.endsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}
