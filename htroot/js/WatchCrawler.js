function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        indexingTable=document.getElementById("indexingTable");
        entries=response.getElementsByTagName("entry");
    
        //skip the Tableheade
        row=indexingTable.firstChild.nextSibling.nextSibling;

        while(row != null){ //delete old entries
            indexingTable.removeChild(row);
            row=indexingTable.firstChild.nextSibling.nextSibling;
        }
        
        dark=false;
        for(i=0;i<entries.length;i++){
            row=document.createElement("tr");
            
            //simply add all fields chronologically
            //TODO: add them by Name
            field=entries[i].firstChild;
            while(field != null){
                if(field.nodeType == 1 && field.firstChild!=null && field.nodeName != "inProcess"){//Element
                    col=document.createElement("td");
                    text=document.createTextNode(field.firstChild.nodeValue);
                    col.appendChild(text);
                    row.appendChild(col);
                }
                field=field.nextSibling;
            }
            if(dark){
                row.setAttribute("class", "TableCellDark");
            }else{
                row.setAttribute("class", "TableCellLight");
            }
            indexingTable.appendChild(row);
            dark=!dark;
        }
    }
}
window.setInterval("sndReq('/xml/queues/indexing_p.xml')", 5000);
