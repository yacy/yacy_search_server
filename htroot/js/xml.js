function removeAllChildren(element){
	if(element==null){
		return;
	}
	child=element.firstChild;
	while(child!=null){
		element.removeChild(child);
		child=element.firstChild;
	}
}

function getValue(element){
	if(element == null){
		return "";
	}else if(element.nodeType == 3){ //Textnode
		return element.nodeValue;
	}else if(element.firstChild != null && element.firstChild.nodeType == 3){
		return element.firstChild.nodeValue;
	}
	return "";
}
function getFirstChild(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.firstChild;
	while(child != null){
		if(child.nodeType!=3 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}
function getNextSibling(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.nextSibling;
	while(child != null){
		if(child.nodeType==1 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}