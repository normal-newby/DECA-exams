"""Import every exam PDF in a folder into the SQLite question bank.

Usage:
    python pdfreader.py [FOLDER] [--cluster Finance] [--dry-run]

FOLDER defaults to FOLDER_PATH and the database to SQLITE_DB_PATH (both read from pdfreader/.env).
Exams already in the DB (same cluster + file name) are skipped.
"""
import argparse
import os
from pathlib import Path

import sqlalchemy
from dotenv import load_dotenv

from exam_parser import parse_pdf

SCHEMA = [
    """CREATE TABLE IF NOT EXISTS exam_sets (
        id integer, cluster varchar(255), exam_name varchar(255), primary key (id))""",
    """CREATE TABLE IF NOT EXISTS questions (
        id integer, answera varchar(255), answerb varchar(255), answerc varchar(255), answerd varchar(255),
        cluster varchar(255), correct_answer varchar(255), description TEXT, ia TEXT, question TEXT,
        specificia varchar(255), examset_id integer, primary key (id))""",
]


def import_exam(conn, exam_name, exam):
    exists = conn.execute(
        sqlalchemy.text("SELECT 1 FROM exam_sets WHERE cluster = :c AND exam_name = :n"),
        {"c": exam.cluster, "n": exam_name},
    ).fetchone()
    if exists:
        return False
    examset_id = conn.execute(
        sqlalchemy.text("INSERT INTO exam_sets (cluster, exam_name) VALUES (:c, :n)"),
        {"c": exam.cluster, "n": exam_name},
    ).lastrowid
    conn.execute(
        sqlalchemy.text("""
            INSERT INTO questions
            (question, answera, answerb, answerc, answerd, correct_answer, description, specificia, ia, cluster, examset_id)
            VALUES (:question, :a, :b, :c, :d, :correct, :description, :specific_ia, :ia, :cluster, :examset_id)
        """),
        [
            {
                "question": q.question, "a": q.answers[0], "b": q.answers[1], "c": q.answers[2], "d": q.answers[3],
                "correct": q.correct_answer, "description": q.description, "specific_ia": q.specific_ia,
                "ia": q.ia, "cluster": exam.cluster, "examset_id": examset_id,
            }
            for q in exam.questions
        ],
    )
    return True


def main():
    load_dotenv(Path(__file__).with_name(".env"))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", nargs="?", default=os.getenv("FOLDER_PATH"))
    ap.add_argument("--cluster", help="override the cluster detected from the PDF header")
    ap.add_argument("--db", default=os.getenv("SQLITE_DB_PATH"))
    ap.add_argument("--dry-run", action="store_true", help="parse and report only; don't write to the DB")
    args = ap.parse_args()
    if not args.folder or (not args.db and not args.dry_run):
        ap.error("set FOLDER_PATH and SQLITE_DB_PATH in pdfreader/.env or pass FOLDER and --db")

    engine = None if args.dry_run else sqlalchemy.create_engine(f"sqlite:///{args.db}")
    if engine:
        with engine.begin() as conn:
            for stmt in SCHEMA:
                conn.execute(sqlalchemy.text(stmt))

    for pdf in sorted(Path(args.folder).glob("*.pdf")):
        try:
            exam = parse_pdf(pdf, args.cluster)
        except Exception as e:
            print(f"FAILED   {pdf.name}: {e}")
            continue
        status = "parsed  "
        if engine:
            with engine.begin() as conn:
                status = "imported" if import_exam(conn, pdf.name, exam) else "skipped "
        print(f"{status} {pdf.name}: {len(exam.questions)} questions ({exam.cluster}), {len(exam.warnings)} warnings")
        for w in exam.warnings:
            print(f"         - {w}")


if __name__ == "__main__":
    main()
