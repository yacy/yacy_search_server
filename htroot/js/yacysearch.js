function Progressbar(length, parent) {
  // the description (displayed above the progressbar while loading the results), should be translated
  var DESCRIPTION_STRING = "Loading results...";

  // the number of steps of the bar
  this.length = length;
  // the current position, expressed in steps (so 100% = length)
  this.position = 0;
  // the current percentage of the bar
  this.percentage = 0


  // use this function to display the progress, because it updates everything
  this.step = function(count) {
    this.position += count;
    // update the bar
    this.percentage = this.position*(100/this.length);
    this.fill.style.width = this.percentage + "%";

    // if the end is reached, the bar is hidden/removed
    if(this.position==this.length)
      removeAllChildren(this.element);
  }

  // the actual progressbar
  var bar = document.createElement("div");
  bar.className = "ProgressBar";
  bar.style.width = "100%";
  bar.style.height = "20px";
  bar.style.margin = "10px auto";
  bar.style.textAlign = "left";

  // the actual bar
  this.fill = document.createElement("div");
  this.fill.className = "ProgressBarFill";
  this.fill.style.width = "0%"
  bar.appendChild(this.fill);

  // a description for the bar
  var description = document.createTextNode(DESCRIPTION_STRING);
  var textcontainer = document.createElement("strong");
  textcontainer.appendChild(description);

  // the container for the elements used by the Progressbar
  this.element = document.createElement("div");
  this.element.style.textAlign = "center";
  // get hasLayout in IE, needed because of the percentage as width of the bar
  this.element.className = "gainlayout";
  this.element.appendChild(textcontainer);
  this.element.appendChild(bar);
  parent.appendChild(this.element);
}

function addHover() {
  if (document.all&&document.getElementById) {
    var divs = document.getElementsByTagName("div");
    for (i=0; i<divs.length; i++) {
      var node = divs[i];
      if (node.className=="searchresults") {
        node.onmouseover=function() {
          this.className+=" hover";
        }
        node.onmouseout=function() {
          this.className=this.className.replace(" hover", "");
        }
      }
    }
  }
}

function statistics(offset, items, global, total) {
  document.getElementById("offset").firstChild.nodeValue = offset;
  document.getElementById("itemscount").firstChild.nodeValue = items;
  document.getElementById("globalcount").firstChild.nodeValue = global;
  document.getElementById("totalcount").firstChild.nodeValue = total;
}