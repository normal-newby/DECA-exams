const dataSection = document.querySelector(".data-information")
class IAData{
    static data = [];
    constructor(ia){
        this.ia = ia;
        this.questionsDone = 0;
        this.questionsCorrect = 0;
    }
    getIa(){
        return this.ia;
    }
    getQuestionsDone(){
        return this.questionsDone;
    }
    getQuestionsCorrect(){
        return this.questionsCorrect;
    }
    getPercentageCorrect(){
        if (this.questionsDone === 0) return -1;
        else return this.questionsCorrect/this.questionsDone;
    }
    static updateQuestion(updatedIA, addOrSubtract){
        for (const iaObj of this.data){
            console.log(iaObj.ia + " " + updatedIA);
            if (iaObj.ia === updatedIA){
                console.log(addOrSubtract);
                iaObj.questionsCorrect += addOrSubtract;
                iaObj.questionsDone += 1;
            }
        }
    }
}
export function createIA(ia, questionsDone, questionsCorrect){
    const newIA = new IAData(ia, questionsDone, questionsCorrect);
    IAData.data.push(newIA);
    let dataPoint = document.createElement("p");
    dataPoint.value = ia;
    dataPoint.innerHTML = `IA: ${ia}, 0/0 = N/A`;
    dataSection.appendChild(dataPoint);
}
export function updateUserQuestionsData(updatedIA, addOrSubtract){
    IAData.updateQuestion(updatedIA, addOrSubtract);
    for (const dataPoint of dataSection.children) {
        if (dataPoint.value === updatedIA) {
            const iaObject = IAData.data.find(d => d.ia === updatedIA);
            if (iaObject) {
                const percentage = iaObject.getPercentageCorrect();
                dataPoint.innerHTML = `IA: ${iaObject.getIa()}, ${iaObject.getQuestionsCorrect()}/${iaObject.getQuestionsDone()} = ${percentage >= 0 ? (percentage * 100).toFixed(1) + '%' : "N/A"}`;
            }
        }
    }
}