DELETE_STRING="delete"
function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        indexingTable=document.getElementById("indexingTable");
		if(response != null){
	        entries=response.getElementsByTagName("entry");
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
            
            //simply add all fields chronologically
            //TODO: add them by Name
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
			row=createRow(initiator, depth, modified, anchor, url, size, hash);
			//create row
            /*col=document.createElement("td");
            text=document.createTextNode(initiator);
            col.appendChild(text);
            row.appendChild(col);*/

			if(inProcess){
                row.setAttribute("class", "TableCellSummary");
            }else if(dark){
                row.setAttribute("class", "TableCellDark");
            }else{
                row.setAttribute("class", "TableCellLight");
            }
            indexingTable.firstChild.nextSibling.appendChild(row);
            dark=!dark;
        }
    }
}
window.setInterval("sndReq('/xml/queues/indexing_p.xml')", 5000);

function createCol(content){
	col=document.createElement("td");
	text=document.createTextNode(content);
	col.appendChild(text);
	return col;
}
function createRow(initiator, depth, modified, anchor, url, size, hash){
    row=document.createElement("tr");
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createCol(url));
	row.appendChild(createCol(size));

	//create delete link
	col=document.createElement("td");
	link=document.createElement("a");
	link.setAttribute("href", "IndexCreateIndexingQueue_p.html?deleteEntry="+hash);
	text=document.createTextNode(DELETE_STRING);
	link.appendChild(text);
	col.appendChild(link)
	row.appendChild(col);
	return row;
}
