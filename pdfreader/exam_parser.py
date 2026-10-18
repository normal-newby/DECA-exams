"""Parse a DECA cluster exam PDF (questions followed by a descriptive answer key).

Keep this in sync with backend/src/main/java/ca/exampractice/exampractice/ExamPdfParser.java,
which implements the same algorithm for in-site uploads.
"""
import re
from dataclasses import dataclass, field

import pdfplumber

# Instructional-area codes (first two letters of "SOURCE: FI:123") -> IA name stored in the DB.
IAS = {
    "BL": "business law",
    "CC": "compliance",
    "CM": "channel management",
    "CO": "communications",
    "CR": "customer relations",
    "EC": "econ",
    "EI": "emotional intelligence",
    "EN": "entrepreneurship",
    "FI": "financial analysis",
    "FM": "financial-information management",
    "HR": "human resources management",
    "KM": "knowledge management",
    "MK": "marketing",
    "MN": "market planning",
    "MP": "marketing-information management",
    "NF": "information management",
    "OP": "operations",
    "PD": "professional development",
    "PI": "product/service management",
    "PJ": "project management",
    "PM": "pricing",
    "PR": "promotion",
    "QM": "quality management",
    "RM": "risk management",
    "SE": "selling",
    "SM": "strategic management",
}

CLUSTERS = {
    "FINANCE": "Finance",
    "MARKETING": "Marketing",
    "HOSPITALITY AND TOURISM": "Hospitality",
    "BUSINESS MANAGEMENT AND ADMINISTRATION": "Business Management",
    "ENTREPRENEURSHIP": "Entrepreneurship",
    "PERSONAL FINANCIAL LITERACY": "Personal Finance",
    "BUSINESS ADMINISTRATION CORE": "Principles",
}

# Page headers/footers, page numbers and download watermarks that get mixed into the text.
NOISE = re.compile(
    r"^(Test \d+\b.*|.*Copyright ©.*|.*MBA Research and Curriculum Center.*|\d{1,3}"
    r"|Downloaded by .*|lOMoARcPSD.*|.{0,50}(CLUSTER EXAM|Cluster Exam).{0,20})$"
)
KEY_LINE = re.compile(r"^(\d{1,3})\.\s*([A-D])$")
OPTION = re.compile(r"^([A-D])\.\s+(.*)$")
INLINE_OPTION = re.compile(r"(?:^|\s)([A-D])\.\s+")
SOURCE_CODE = re.compile(r"SOURCE:\s*([A-Z]{2}):?\s*(\d{3})")
CLUSTER_HDR = re.compile(r"([A-Z][A-Z ]+?) (?:CLUSTER|CAREER CLUSTER|CORE) EXAM")


@dataclass
class Question:
    number: int
    question: str
    answers: list
    correct_answer: str = ""
    description: str = ""
    specific_ia: str = ""
    ia: str = ""


@dataclass
class ParsedExam:
    cluster: str
    questions: list
    warnings: list = field(default_factory=list)


def _clean_lines(text):
    return [ln.strip() for ln in text.splitlines() if ln.strip() and not NOISE.match(ln.strip())]


def _split_numbered(lines, pattern_for):
    """Split lines into blocks at '1.', '2.', ... in strict order, so numbers inside text are ignored."""
    blocks, current, n = {}, None, 1
    for ln in lines:
        m = pattern_for(n).match(ln)
        if m:
            current = n
            blocks[n] = [m.group(1)] if m.group(1) else []
            n += 1
        elif current is not None:
            blocks[current].append(ln)
    return blocks


def detect_cluster(text):
    for m in CLUSTER_HDR.finditer(text):
        name = m.group(1)
        for key, value in CLUSTERS.items():
            if name.endswith(key):
                return value
    return None


def parse_text(text, cluster=None):
    lines = _clean_lines(text)
    warnings = []

    key_start = next((i for i, ln in enumerate(lines) if KEY_LINE.match(ln) and KEY_LINE.match(ln).group(1) == "1"), None)
    if key_start is None:
        raise ValueError("Could not find the answer key (a line like '1. B').")
    q_lines, k_lines = lines[:key_start], lines[key_start:]
    # Drop the answer key's cover page (starts with a bare "KEY" line) so it doesn't stick to question 100.
    if "KEY" in q_lines:
        q_lines = q_lines[: len(q_lines) - 1 - q_lines[::-1].index("KEY")]

    q_blocks = _split_numbered(q_lines, lambda n: re.compile(rf"^{n}\.\s+(.*)$"))
    k_blocks = {}
    current = None
    expected = 1
    for ln in k_lines:
        m = KEY_LINE.match(ln)
        if m and int(m.group(1)) == expected:
            current = expected
            k_blocks[current] = (m.group(2), [])
            expected += 1
        elif current is not None:
            k_blocks[current][1].append(ln)

    questions = []
    for n, block in q_blocks.items():
        stem, opts, last = [], {}, None
        for ln in block:
            m = OPTION.match(ln)
            if m and m.group(1) not in opts:
                # Two-column layouts put two options on one line: "A. awarding damages C. providing notice"
                parts = INLINE_OPTION.split(ln)
                for label, body in zip(parts[1::2], parts[2::2]):
                    if label in opts:
                        opts[last].append(f"{label}. {body}")
                    else:
                        last = label
                        opts[label] = [body.strip()]
            elif last:
                opts[last].append(ln)
            else:
                stem.append(ln)
        q = Question(n, " ".join(stem), [" ".join(opts.get(l, [])) for l in "ABCD"])

        if n in k_blocks:
            letter, body = k_blocks[n]
            q.correct_answer = letter
            joined = "\n".join(body)
            cut = joined.find("SOURCE:")
            q.description = " ".join((joined[:cut] if cut >= 0 else joined).split())
            sm = SOURCE_CODE.search(joined)
            if sm:
                q.specific_ia = f"{sm.group(1)}:{sm.group(2)}"
                q.ia = IAS.get(sm.group(1), f"unknown ({sm.group(1)})")
        problems = []
        if not q.question:
            problems.append("empty question")
        if any(not a for a in q.answers):
            problems.append("missing option(s) " + "".join(l for l, a in zip("ABCD", q.answers) if not a))
        if not q.correct_answer:
            problems.append("no answer in key")
        if not q.specific_ia:
            problems.append("no SOURCE code")
        if problems:
            warnings.append(f"Q{n}: " + ", ".join(problems))
        questions.append(q)

    if len(questions) != len(k_blocks):
        warnings.append(f"found {len(questions)} questions but {len(k_blocks)} answers in key")
    return ParsedExam(cluster or detect_cluster(text) or "Unknown", questions, warnings)


def parse_pdf(path, cluster=None):
    with pdfplumber.open(path) as pdf:
        text = "\n".join(page.extract_text() or "" for page in pdf.pages)
    return parse_text(text, cluster)
