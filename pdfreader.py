import pdfplumber
from dotenv import load_dotenv
import os
from google.cloud.sql.connector import Connector
import sqlalchemy
from pathlib import Path
import re

# load environment variables
load_dotenv()
connection = os.getenv("CONNECTION")
username = os.getenv("USER")
password = os.getenv("PASSWORD")
db = os.getenv("DB")

# dependencies: pdfplumber, cloud-sql-python-connector, sqlalchemy, pymysql
folder = Path("./practice exams")

ias = {
        "bl": "business law",
        "co": "communications",
        "cr": "customer relations",
        "ec": "econ",
        "ei": "emotional intelligence",
        "en": "entrepreneurship",
        "fi": "financial analysis",
        "fm": "financial-information management",
        "hr": "human resources management",
        "mk": "marketing",
        "nf": "information management",
        "op": "operations",
        "pd": "professional development",
        "rm": "risk management",
        "sm": "strategic management",
    }
class Question:
        def __init__(self, question, answers, correctAnswer, description, specificIA, ia, cluster):
            self.question = question
            self.answers = answers
            self.correctAnswer = correctAnswer
            self.description = description
            self.specificIA = specificIA
            self.ia = ia
            self.cluster = cluster
def extractQuestions(pages):
    num  = 0
    returnList = []
    for i in range(1, 101):
        search = str(i) + "."
        for j in range(num, len(pages)):
            page = pages[j]
            if search in page:
                if not (str(i+1) + "." in page) or i==100:
                    returnList.append(page[page.find(search) : len(page)].strip())
                else:
                    returnList.append(page[page.find(search) : page.find(str(i+1) + ".")].strip())
                num = j
                break
    return returnList
def extractAnswers(pages):
    returnList = []
    for i in range(1, 101):
        search = str(i) + "."
        for page in pages:
            if ((search + " A") in page) or ((search + " B") in page) or ((search + " C") in page) or ((search + " D") in page):
                returnList.append(page[page.find(search) : page.find(str(i+1) + ".") if (page.find(str(i+1) + ".") != -1) and (page[page.find(str(i+1) + ".")+ 2].isalpha()) else None].strip())
                break
    return returnList
def extract_answers(question): #this functions credit goes to copilot
    text = question
    matches = list(re.finditer(r'([A-D])\.\s*', text))
    if not matches:
        return ["", "", "", ""]

    answer_map = {}
    for i, match in enumerate(matches):
        label = match.group(1)
        start = match.end()
        if i + 1 < len(matches):
            end = matches[i + 1].start()
        else:
            # Look for common end markers after the last answer
            tail = text[start:]
            stop = re.search(r'Copyright', tail)
            end = start + stop.start() if stop else len(text)
        answer_map[label] = text[start:end].strip()

    # Return answers in A, B, C, D order (empty string if missing)
    return [answer_map.get(letter, "") for letter in "ABCD"]
for file in folder.iterdir():
    questionsPages = []
    answersPages = []
    cluster = "Finance"
    exam_name = file.name
    with pdfplumber.open(f"./practice exams/{exam_name}") as pdf: #pdf file
        for page in pdf.pages:
            if ("FINANCE CLUSTER EXAM—KEY" in page.extract_text()):
                curPage = page.page_number-1
                break
            questionsPages.append(page.extract_text())
        for i in range(curPage, len(pdf.pages)):
            answersPages.append(pdf.pages[i].extract_text())

    questions = extractQuestions(questionsPages)
    answers = extractAnswers(answersPages)

    #dont touch above code it works lol###

    questionsList = []
    for question, answer in zip(questions, answers):
        try:
            qText = question[question.find(".")+1 : question.find("A.")].strip()
            aText = extract_answers(question)
            correctAnswer = answer[answer.find(".") + 2]
            description = answer[answer.find(".") + 3 : answer.find("SOURCE:")].strip() #error?
            location = answer.find("SOURCE:") + 8
            specificIA = answer[location: location + 7].strip()
            ia = ias.get(specificIA[0:2].lower(), "Unknown IA")

            q = Question(qText, aText, correctAnswer, description, specificIA, ia, cluster)
            questionsList.append(q)
            print("question: ", q.question)
            for i, a in enumerate(q.answers):
                print(f"answer {i}: ", a)
            print("correctAnswer: ", q.correctAnswer)
            print("specificIA: ", q.specificIA)
            print("description: ", q.description)
            print("ia: ", q.ia)
            print("cluster: ", q.cluster)
            print("\n\n")
        except Exception as e:
            print("error on question: ", question)
            print(e)
            print("\n\n")
            continue

    # add everything to database below

    connector = Connector()
    def getconn():
        conn = connector.connect(
            connection,
            "pymysql",
            user = username,
            password = password,
            db = db
        )
        return conn

    pool = sqlalchemy.create_engine(
        "mysql+pymysql://",
        creator = getconn,
    )
    with pool.connect() as db_conn:
        if not db_conn.execute(sqlalchemy.text("SELECT 1 FROM exam_sets WHERE cluster = :cluster AND exam_name = :exam_name"), {"cluster": cluster, "exam_name": exam_name}).fetchone():
            result = db_conn.execute(
                sqlalchemy.text("INSERT INTO exam_sets (cluster, exam_name) VALUES (:cluster, :exam_name)"),
                {"cluster": cluster, "exam_name": exam_name}
            )
            examset_id = result.lastrowid

            for q in questionsList:
                existing = db_conn.execute(
                    sqlalchemy.text("SELECT 1 FROM questions WHERE question = :question LIMIT 1"),
                    {"question": q.question}
                ).fetchone()
                if existing:
                    print("wowee this is a duplicate question \n", q.question)
                db_conn.execute(
                    sqlalchemy.text("""
                        INSERT INTO questions 
                        (question, answera, answerb, answerc, answerd, correct_answer, description, specificIA, ia, cluster, examset_id)
                        VALUES (:question, :a, :b, :c, :d, :correctAnswer, :description, :specificIA, :ia, :cluster, :examset_id)
                    """),
                    {
                        "question": q.question,
                        "a": q.answers[0],
                        "b": q.answers[1],
                        "c": q.answers[2],
                        "d": q.answers[3],
                        "correctAnswer": q.correctAnswer,
                        "specificIA": q.specificIA,
                        "description": q.description,
                        "ia": q.ia,
                        "cluster": q.cluster,
                        "examset_id": examset_id
                    }
                )

            db_conn.commit()
    print("done with ", exam_name)