const slides=[...document.querySelectorAll(".slide")];
const dots=[...document.querySelectorAll(".dots button")];
let current=0;
function show(index){
  current=(index+slides.length)%slides.length;
  slides.forEach((slide,i)=>slide.classList.toggle("active",i===current));
  dots.forEach((dot,i)=>dot.classList.toggle("active",i===current));
}
document.querySelector(".prev").addEventListener("click",()=>show(current-1));
document.querySelector(".next").addEventListener("click",()=>show(current+1));
dots.forEach(dot=>dot.addEventListener("click",()=>show(Number(dot.dataset.slide))));
setInterval(()=>show(current+1),5500);
