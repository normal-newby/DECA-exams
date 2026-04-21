import { getCluster } from "./cluster.js";

const iasSelection = document.querySelector(".ia-selector-selectors");
const selectedIAs = document.getElementById("selectedIAs");
let selectedIAsList = new Set();

export function loadIAs(){
    const cluster = getCluster();
    console.log(cluster);
    fetch(`http://localhost:8080/api/${cluster}/getIAs/`)
        .then(response => response.json())
        .then(iaNames => {
            iasSelection.innerHTML = "";
            let option = document.createElement("button");
            option.classList.add("ia-select-button");
            option.value = "all";
            option.textContent = "all";
            iasSelection.append(option);
            iaNames.forEach(iaName => {
                option = document.createElement("button");
                option.classList.add("ia-select-button");
                option.value = iaName;
                option.textContent = iaName;
                iasSelection.appendChild(option);
            })
        })
        .catch(error => console.log(error)
    );
}

iasSelection.addEventListener("click", (e) => {
    const value = e.target.value;
    if (!selectedIAsList.has(value)){
        selectedIAsList.add(value);
    } else {
        selectedIAsList.delete(value);
    }
    selectedIAs.innerHTML = Array.from(selectedIAsList).join(" ");
})

export function getIAsSelectionValue(){
    return selectedIAsList;
}