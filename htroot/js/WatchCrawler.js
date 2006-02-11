DELETE_STRING="delete"

var statusRPC;
var indexingQueueRPC;
function requestStatus(){
	statusRPC=createRequestObject()
	statusRPC.open('get', '/xml/status_p.xml');
	statusRPC.onreadystatechange = handleStatus;
	statusRPC.send(null)
}
function requestIndexingQueue(){
	indexingQueueRPC=createRequestObject()
	indexingQueueRPC.open('get', '/xml/queues/indexing_p.xml');
	indexingQueueRPC.onreadystatechange = handleIndexingQueue;
	indexingQueueRPC.send(null);

}
window.setInterval("requestStatus()", 5000);
window.setInterval("requestIndexingQueue()", 5000);


function handleStatus(){
    if(statusRPC.readyState != 4){
		return;
	}
	var statusResponse = statusRPC.responseXML;
	/*indexingqueue=statusResponse.getElementsByTagName("indexingqueue")[0];
	indexingqueue_size=indexingqueue.firstChild.nextSibling;
	indexingqueue_max=indexingqueue_size.nextSibling.nextSibling;
	ppm=statusResponse.getElementsByTagName("ppm")[0];*/
	status=getFirstChild(statusResponse.firstChild, "status")
	indexingqueue=getFirstChild(status, "indexingqueue");

	indexingqueue_size=getValue(getFirstChild(indexingqueue, "size"));
	indexingqueue_max=getValue(getFirstChild(indexingqueue, "max"));
	ppm=getValue(getFirstChild(status, "ppm"));
	
	document.getElementById("indexingqueuesize").firstChild.nodeValue=indexingqueue_size;
	document.getElementById("indexingqueuemax").firstChild.nodeValue=indexingqueue_max;
	document.getElementById("ppm").firstChild.nodeValue=ppm;
}


function handleIndexingQueue(){
    if(indexingQueueRPC.readyState != 4){
		return;
	}
	var indexingQueueResponse = indexingQueueRPC.responseXML;
	indexingTable=document.getElementById("indexingTable");
	if(indexingQueueResponse != null){
		entries=indexingQueueResponse.getElementsByTagName("entry");
	}
    
    //skip the Tableheade
    row=indexingTable.firstChild.nextSibling.firstChild.nextSibling.nextSibling;

    while(row != null){ //delete old entries
        indexingTable.firstChild.nextSibling.removeChild(row);
        row=indexingTable.firstChild.nextSibling.firstChild.nextSibling.nextSibling;
    }
        
    dark=false;
    for(i=0;i<entries.length;i++){
		initiator=getValue(getFirstChild(entries[i], "initiator"));
		depth=getValue(getFirstChild(entries[i], "depth"));
		modified=getValue(getFirstChild(entries[i], "modified"));
		anchor=getValue(getFirstChild(entries[i], "anchor"));
		url=getValue(getFirstChild(entries[i], "url"));
		size=getValue(getFirstChild(entries[i], "size"));
		hash=getValue(getFirstChild(entries[i], "hash"));
		inProcess=false;
		if(getValue(getFirstChild(entries[i], "inProcess"))=="true"){
			inProcess=true;
		}
		
		row=createIndexingRow(initiator, depth, modified, anchor, url, size, hash);
		//create row
		if(inProcess){
            row.setAttribute("class", "TableCellActive");
        }else if(dark){
            row.setAttribute("class", "TableCellDark");
        }else{
            row.setAttribute("class", "TableCellLight");
        }
        indexingTable.firstChild.nextSibling.appendChild(row);
        dark=!dark;
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
	child=element.firstChild;
	while(child != null){
		if(child.nodeType!=3 && (child.nodeName==childname || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}
function getNextSibling(element, childname){
	child=element.nextSibling;
	while(child != null){
		if(child.nodeType==1 && (child.nodeName==childname || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}


function createCol(content){
	col=document.createElement("td");
	text=document.createTextNode(content);
	col.appendChild(text);
	return col;
}
function createIndexingRow(initiator, depth, modified, anchor, url, size, hash){
    row=document.createElement("tr");
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	row.appendChild(createCol(size));
	row.appendChild(createLinkCol("IndexCreateIndexingQueue_p.html?deleteEntry="+hash, DELETE_STRING));
	return row;
}
function createLinkCol(url, linktext){
	col=document.createElement("td");
	link=document.createElement("a");
	link.setAttribute("href", url);
	link.setAttribute("target", "_blank");
	text=document.createTextNode(linktext);
	link.appendChild(text);
	col.appendChild(link)
	return col
}
