from google.cloud.sql.connector import Connector
import sqlalchemy

class IAStats:
    def __init__(self, ia):
        self.ia = ia
        self.total = 0
        self.correct = 0
    def addQuestion(self, correct):
        self.total += 1
        if correct:
            self.correct += 1
    def getAccuracy(self):
        if self.total == 0:
            return 0.0
        return self.correct / self.total
    def totalQuestions(self):
        return self.total
iaList = ["business law",
    "communications",
    "customer relations",
    "econ",
    "emotional intelligence",
    "entrepreneurship",
    "financial analysis",
    "financial-information management",
    "human resources management",
    "marketing",
    "information management",
    "operations",
    "professional development",
    "risk management",
    "strategic management",
    "total"]
iaObjects = []
for ia in iaList:
    iaObjects.append(IAStats(ia))
iaObjects = {ia: IAStats(ia) for ia in iaList}
connector = Connector()
def getconn():
    conn = connector.connect(
        "summer-hawk-473921-i2:us-central1:exampractice",
        "pymysql",
        user = "root",
        password = "R/*kT?D3eRm5f:`z",
        db = "exampractice"
    )
    return conn

pool = sqlalchemy.create_engine(
    "mysql+pymysql://",
    creator = getconn,
)
with pool.connect() as db_conn:
    examsets = db_conn.execute(sqlalchemy.text("SELECT * FROM ExamSets")).fetchall()
    numexams = len(examsets)
    for row in examsets:
        print("ID:", row.id, " Cluster:", row.cluster, " Exam Name:", row.exam_name)
    print("ID", (numexams + 1), " Random questions")
    while True:
        try:
            id = int(input(f"Enter the ID of the exam set you want to view (1-{numexams + 1}): "))
            if 1 <= id <= numexams + 1:
                break
            else:
                print("Please enter a number between 1 and", numexams + 1)
        except ValueError:
            print("Invalid input. Please enter a number.")
    if id == numexams + 1:
        try:
            while True:
                numQuestions = int(input("How many random questions do you want (max 100)? "))
                if 1 <= numQuestions <= 100:
                    break
                else:
                    print("Please enter a number between 1 and 100")
        except ValueError:
            print("Invalid input. Defaulting to 20 questions.")
            numQuestions = 20
        result = db_conn.execute(sqlalchemy.text("SELECT * FROM Questions ORDER BY RAND() LIMIT 20")).fetchall()
    else:
        result = db_conn.execute(sqlalchemy.text("SELECT * FROM Questions WHERE examset_id = :id"), {"id": id}).fetchall()
    
    for row in result:
        print("Question number:", iaObjects.get("total").totalQuestions()+1, "\n"
              , "Question:", row.question, "\n"
              , "Answer A:", row.answerA, "\n"
              , "Answer B:", row.answerB, "\n"
              , "Answer C:", row.answerC, "\n"
              , "Answer D:", row.answerD, "\n"
        )
        user_answer = input("Please enter your answer (A, B, C, or D): ").upper()
        ia_obj = iaObjects.get(row.ia)
        total_obj = iaObjects.get("total")
        ia_obj.addQuestion(user_answer == row.correctAnswer)
        total_obj.addQuestion(user_answer == row.correctAnswer)
        print ("Correct the answer is " if user_answer == row.correctAnswer else "Incorrect, the correct answer is ", row.correctAnswer)
        print("Your current accuracy for this IA (", row.ia, ") is: ", f"{ia_obj.getAccuracy()*100:.2f}%", sep="")
        print("Your current overall accuracy is: ", f"{total_obj.getAccuracy()*100:.2f}%", sep="")
        wantToSee = True if input("Want to see the explanation and IA? (Y/N) ").upper() == "Y" else False
        if wantToSee:
                print("Explanation: ", row.description)
                print("IA: ", row.ia)
                print("Specific IA: ", row.specificIA)
        wantNextQuestion = True if input("Want another question? (Y/N)").upper() == "Y" else False
        if not wantNextQuestion:
            break
for ia in iaObjects:
    ia_obj = iaObjects.get(ia)
    print(f"IA: {ia}, Total Questions: {ia_obj.total}, Correct Answers: {ia_obj.correct}, Accuracy: {ia_obj.getAccuracy()*100:.2f}%")
