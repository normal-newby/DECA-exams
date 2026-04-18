const colours = {
    finance: "rgb(147, 255, 143)",
    marketing: "rgb(100,100,100)",
    principles: "rgb(255,255,255)"
}
for (let colour in colours){
    cell = document.querySelector(`.${colour}`);
    console.log(cell.innerHTML);
    cell.style.backgroundColor = colours[colour];
}
