package ca.exampractice.exampractice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExamPdfParserTest {

    private final ExamPdfParser parser = new ExamPdfParser();

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    @Test
    void parsesTwoColumnOptionsAndKey() {
        String text = """
                Test 1123 FINANCE CLUSTER EXAM 1
                1. A basic principle of procedural due process involves __________ before taking action.
                A. awarding damages C. providing notice
                B. obtaining witnesses D. creating evidence
                2. Which is 1 percent
                of the total?
                A. One
                B. Two
                C. Three
                D. Four
                Copyright © 2016 by MBA Research and Curriculum Center®, Columbus, Ohio
                KEY
                Test 1123 FINANCE CLUSTER EXAM—KEY 2
                1. C
                Providing notice. Due process requires notice.
                SOURCE: BL:069
                SOURCE: Some textbook.
                2. A
                One. Because.
                SOURCE: CC:003
                """;
        var exam = parser.parseText(text, null);
        assertEquals("Finance", exam.cluster());
        assertEquals(List.of(), exam.warnings());
        var q1 = exam.questions().get(0);
        assertEquals(List.of("awarding damages", "obtaining witnesses", "providing notice", "creating evidence"), q1.answers());
        assertEquals("C", q1.correctAnswer());
        assertEquals("BL:069", q1.specificIA());
        assertEquals("business law", q1.ia());
        assertEquals("Providing notice. Due process requires notice.", q1.description());
        var q2 = exam.questions().get(1);
        assertEquals("Which is 1 percent of the total?", q2.question());
        assertEquals("compliance", q2.ia());
    }

    @Test
    void rejectsPdfWithoutKey() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseText("1. Question\nA. a\nB. b", null));
    }

    /** Runs on the local exam PDFs (gitignored); skipped when they aren't present. */
    @Test
    void parsesLocalExamPdfs() throws Exception {
        List<Path> pdfs = new ArrayList<>();
        for (String dir : List.of("../pdfreader/practice exams", "../pdfreader/new ICDC")) {
            Path p = Path.of(dir);
            if (Files.isDirectory(p)) try (Stream<Path> s = Files.list(p)) {
                s.filter(f -> f.toString().endsWith(".pdf")).forEach(pdfs::add);
            }
        }
        assumeTrue(!pdfs.isEmpty(), "no local exam PDFs");

        int total = 0, matched = 0;
        List<String> problems = new ArrayList<>();
        for (Path pdf : pdfs) {
            var exam = parser.parse(Files.readAllBytes(pdf), null);
            if (exam.questions().size() != 100 || !exam.warnings().isEmpty()) {
                problems.add(pdf.getFileName() + ": " + exam.questions().size() + " questions, " + exam.warnings());
            }
            for (var q : exam.questions()) {
                total++;
                // The key's explanation starts by restating the correct option, which checks options + key alignment.
                String opt = norm(q.answers().get("ABCD".indexOf(q.correctAnswer())));
                if (!opt.isEmpty() && norm(q.description()).startsWith(opt.substring(0, Math.min(25, opt.length())))) matched++;
                else problems.add(pdf.getFileName() + " Q" + q.number() + ": option '" + q.answers().get("ABCD".indexOf(q.correctAnswer()))
                        + "' vs key '" + q.description().substring(0, Math.min(80, q.description().length())) + "'");
            }
        }
        System.out.printf("Parsed %d PDFs, %d questions, %d match their key explanation%n", pdfs.size(), total, matched);
        problems.forEach(System.out::println);
        assertEquals(pdfs.size() * 100, total);
        assertTrue(matched >= total * 0.99, "too many option/key mismatches");
    }
}
