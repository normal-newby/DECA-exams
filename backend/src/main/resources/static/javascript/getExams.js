import { getCluster } from "./cluster.js";

const examsSelection = document.getElementById("exams");
const selectedExam = document.getElementById("selectedExam");
export function loadExams(){
    const cluster = getCluster();
    fetch(`http://localhost:8080/api/getExams/${cluster}`) //initial fetch from server when page loads
        .then(response => response.json())
        .then(examNames => {
            examsSelection.innerHTML = "";
            let option = document.createElement("option");
            option.value = "all";
            option.textContent = "all";
            examsSelection.append(option);
            examNames.forEach(examName => {
                option = document.createElement("option");
                option.value = examName;
                option.textContent = examName;
                examsSelection.appendChild(option);
            })
            selectedExam.innerHTML = examsSelection.value;
        })
        .catch(error => console.log(error)
    );
}
examsSelection.addEventListener("change", (e) => {
    selectedExam.innerHTML = examsSelection.value;
})

export function getExamsSelectionValue(){
    return examsSelection.value;
}