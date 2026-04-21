import { getCluster } from "./cluster.js";
import { getExamsSelectionValue } from "./getExams.js";
import { getIAsSelectionValue } from "./getIAs.js";
import {createIA, updateUserQuestionsData} from "./saveData.js";

const getQuestionsButton = document.getElementById("getQuestions");
const messages = document.getElementById("messages");

//for display
const questionDisplay = document.getElementById("question");
const answers = document.querySelectorAll(".answerButton");
const ia = document.getElementById("ia");
const specificIA = document.getElementById("specificIA");
const correctAnswer = document.getElementById("correctAnswer");
const description = document.getElementById("description");
const nextButton = document.getElementById("nextButton");
const previousButton = document.getElementById("prevButton");
let questions = [];
let curQuestion = 0;

getQuestionsButton.addEventListener("click", (e) => {
    e.preventDefault();
    const cluster = getCluster();
    if (cluster == null){
        return;
    }
    const exam = getExamsSelectionValue();
    const ias = Array.from(getIAsSelectionValue())
    if (ias.length == 0){
        messages.innerHTML = "Please select IA(s)";
        return;
    } else {
        fetch(`http://localhost:8080/api/getExams/${cluster}/${exam}/questions`,
            {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({ias: ias})
            }
        )
            .then(response => response.json())
            .then(q => {
                if (q.length == 0){
                    messages.innerHTML = "None available";
                    return;
                }
                messages.innerHTML = `Questions retrieved ${q.length}`;
                let allIAs = new Set();
                for (const question of q){
                    const newq = new Question(
                        question["question"], 
                        question["answerA"], question["answerB"], question["answerC"], question["answerD"],
                        question["correctAnswer"],
                        question["description"],
                        question["specificIA"],
                        question["ia"],
                        question["cluster"]
                    );
                    questions.push(newq);
                    allIAs.add(question["ia"]);
                }
                allIAs.forEach(individualIA => {
                    createIA(individualIA, 0, 0);
                })
                updateUserQuestionsData();
                curQuestion = 0;
                updateAnswerButtons();
                setHTML(curQuestion);
            })
            .catch(e => console.log(e));
        }
})

function handleAnswerClick(e) {
    questions[curQuestion]["questionDone"] = true;
    questions[curQuestion]["userAnswer"] = e.target.value;
    const correctAnswer = questions[curQuestion]["correct"];
    const questionIA = questions[curQuestion]["ia"];
    if (e.target.value === correctAnswer){
        e.target.style.backgroundColor = "rgba(135, 224, 139, 1)";
        updateUserQuestionsData(questionIA, 1);
    } else {
        e.target.style.backgroundColor = "rgba(235, 118, 118, 1)";
        const correct = Array.from(answers).find(a => a.value === correctAnswer);
        correct.style.backgroundColor = "rgba(135, 224, 139, 1)";
        updateUserQuestionsData(questionIA, 0);
    }
    changeInformation(false);
    answers.forEach(a => a.disabled = true);
}

function updateAnswerButtons(){ //respond to clicks on questions a, b, c, d
    answers.forEach(answer => {
        answer.removeEventListener("click", handleAnswerClick);
        answer.disabled = false;
        answer.style.backgroundColor = "rgb(255,255,255)";
        answer.addEventListener("click", handleAnswerClick);
    });
}
function setHTML(curQuestion){ //dynamically changes html
    console.log(curQuestion);
    questionDisplay.innerHTML = "Question: " + questions[curQuestion].question;
    let cnt = 0;
    console.log(questions[curQuestion]["answer"]);

    answers.forEach(answer => {
        answer.innerHTML = questions[curQuestion]["answer"][cnt++];
    })
    changeInformation(true);
    ia.innerHTML = "IA: " + questions[curQuestion].ia;
    specificIA.innerHTML = "Specific IA: " + questions[curQuestion].specificIA;
    correctAnswer.innerHTML = "Correct Answer: " + questions[curQuestion].correct;
    description.innerHTML = "Description: " + questions[curQuestion].description;
}
function changeInformation(hidden){
    ia.hidden = hidden;
    specificIA.hidden = hidden;
    correctAnswer.hidden = hidden;
    description.hidden = hidden;
}
nextButton.addEventListener("click", () => {
    answers.forEach(a => {
        a.disabled = true
        a.style.backgroundColor = "rgba(255, 255, 255, 1)";
    });
    setHTML(++curQuestion);
    console.log(questions[curQuestion]);
    if (questions[curQuestion]["questionDone"]) setInfoIfDone();
    else updateAnswerButtons();
});
previousButton.addEventListener("click", () => {
    if (curQuestion > 0){
        answers.forEach(a => {
            a.disabled = true
            a.style.backgroundColor = "rgba(255, 255, 255, 1)";
        });
        setHTML(--curQuestion);
        setInfoIfDone();
    }
})

function setInfoIfDone(){
    const status = questions[curQuestion]["questionDone"];
    const userAnswer = questions[curQuestion]["userAnswer"];
    const correctAnswer = questions[curQuestion]["correct"];
    if (status){
        if (userAnswer === correctAnswer){
            for (const answer of answers){
                if (answer.value === userAnswer){
                    console.log(answer.value + " " + userAnswer);
                    answer.style.backgroundColor = "rgba(135, 224, 139, 1)";
                }
            }
        } else {
            for (const answer of answers){
                console.log(answer.value + " " + userAnswer + " " + correctAnswer);
                if (answer.value === correctAnswer) answer.style.backgroundColor = "rgba(135, 224, 139, 1)"; 
                else if (answer.value === userAnswer) answer.style.backgroundColor = "rgba(235, 118, 118, 1)";
            }
        }
        changeInformation(false);
    } else {
        for (const answer of answers){
            if (answer.value === correctAnswer) answer.style.backgroundColor = "rgb(255,255,255)";
            answer.disabled = false;
        }
    }
}
class Question{
    constructor(question, a, b, c, d, correct, description, specificIA, ia, cluster){
        this.question = question;
        this.answer =[a,b,c,d];
        this.correct = correct;
        this.description = description;
        this.specificIA = specificIA;
        this.ia = ia;
        this.cluster = cluster;
        this.questionDone = false;
        this.userAnswer = null;
    }
}