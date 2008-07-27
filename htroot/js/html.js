function createCol(content){
	col=document.createElement("td");
	text=document.createTextNode(content);
	col.appendChild(text);
	return col;
}

function clearTable(table, numSkip){
	if(numSkip==null){
		numSkip=0;
	}
	row=getFirstChild(getFirstChild(table, "tbody"), "tr");
	//skip numSkip rows
	for(i=0;i<numSkip;i++){
		row=getNextSibling(row, "tr");
	}
    while(row != null){ //delete old entries
        getFirstChild(table, "tbody").removeChild(row);
   		row=getFirstChild(getFirstChild(table, "tbody"), "tr");
		//skip numSkip rows
		for(i=0;i<numSkip;i++){
			row=getNextSibling(row, "tr");
		}
    }
}

function createLinkCol(url, linktext){
	col=document.createElement("td");
	link=document.createElement("a");
	link.setAttribute("href", url);
	link.setAttribute("target", "_blank");
	text=document.createTextNode(linktext);
	link.appendChild(text);
	col.appendChild(link);
	return col;
}

function radioValue(inputs) {
  for (var i=0; i<inputs.length; i++) if (inputs[i].checked) return inputs[i].value;
  return false;
}

function hide(id) {
	document.getElementById(id).style.display = "none";
}

function show(id) {
	document.getElementById(id).style.display = "inline";
}
