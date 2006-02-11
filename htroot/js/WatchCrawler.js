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



//window.setInterval("sndReq('/xml/queues/indexing_p.xml')", 5000);


function handleStatus(){
    if(statusRPC.readyState != 4){
		return;
	}
	var statusResponse = statusRPC.responseXML;
	indexingqueue=statusResponse.getElementsByTagName("indexingqueue")[0];
	indexingqueue_size=indexingqueue.firstChild.nextSibling;
	indexingqueue_max=indexingqueue_size.nextSibling.nextSibling;
	ppm=statusResponse.getElementsByTagName("ppm")[0];
	document.getElementById("indexingqueuesize").firstChild.nodeValue=indexingqueue_size.firstChild.nodeValue;
	document.getElementById("indexingqueuemax").firstChild.nodeValue=indexingqueue_max.firstChild.nodeValue;
	document.getElementById("ppm").firstChild.nodeValue=ppm.firstChild.nodeValue;
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
		initiator="";
		depth="";
		modified="";
		anchor="";
		url="";
		size="";
		hash="";
		inProcess=false;
           
        field=entries[i].firstChild;
        while(field != null){
            if(field.nodeType == 1 && field.firstChild!=null){//Element
				if(field.nodeName=="initiator"){
					initiator=field.firstChild.nodeValue;
				}else if(field.nodeName=="depth"){
					depth=field.firstChild.nodeValue;
				}else if(field.nodeName=="modified"){
					modified=field.firstChild.nodeValue;
				}else if(field.nodeName=="anchor"){
					anchor=field.firstChild.nodeValue;
				}else if(field.nodeName=="url"){
					url=field.firstChild.nodeValue;
				}else if(field.nodeName=="size"){
					size=field.firstChild.nodeValue;
				}else if(field.nodeName=="hash"){
					hash=field.firstChild.nodeValue;
				}else if(field.nodeName=="inProcess"){
					if(field.firstChild.nodeValue=="true"){
						inProcess=true;
					}
				}
            }
            field=field.nextSibling;
        }
		/*field=getChild(entries[i], "initiator");
		if(field!=null){
			initiator=field.firstChild.nodeValue;
		}
		//...
		*/
		
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
function getChild(element, childname){
	child=element.firstChild;
	while(child != null){
		if(child.nodeName==childname){
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
