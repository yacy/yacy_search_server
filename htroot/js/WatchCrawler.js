function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        indexingTable=document.getElementById("indexingTable");
        entries=response.getElementsByTagName("entry");
        for(i=0;i<entries.length;i++){
            row=document.createElement("tr");
            
            //simply add all fields chronologically
            //TODO: add them by Name
            field=entries[i].firstChild;
            while(field != null){
                if(field.nodeType == 1 && field.nodeName != "inProcess"){//Element
                    col=document.createElement("td");
                    text=document.createTextNode(field.firstChild.nodeValue);
                    col.appendChild(text);
                    row.appendChild(col);
                }
                field=field.nextSibling;
            }
            
            indexingTable.appendChild(row);
        }
    }
}
sndReq("/xml/queues/indexing_p.xml");
