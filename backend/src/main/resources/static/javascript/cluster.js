import { loadIAs } from "./getIAs.js";
import { loadExams } from "./getExams.js";

let cluster = ""
const navigation = document.querySelector(".clusterNav");
const clusterDisplay = document.querySelector(".cluster")
navigation.addEventListener("click", (e) => {
    cluster = e.target.name;
    clusterDisplay.innerHTML = cluster;
    loadExams();
    loadIAs();
})
export function getCluster(){
    if (cluster.length == 0){
        clusterDisplay.innerHTML = "Please select a cluster";
        return null;
    }
    clusterDisplay.innerHTML = `Your selected cluster: ${cluster}`;
    return cluster;
}
