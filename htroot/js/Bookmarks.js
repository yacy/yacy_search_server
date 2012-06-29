var AJAX_OFF="/env/grafics/empty.gif";
var AJAX_ON="/env/grafics/ajax.gif";

function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
        tags_field=document.getElementById("tags");
        document.getElementById("title").value=title;
        
        tags=response.getElementsByTagName("tag");
        for(i=0;i<tags.length-1;i++){
        	tags_field.value+=tags[i].getAttribute("name")+",";
        }
        tags_field.value+=tags[tags.length-1].getAttribute("name");
		
		// remove the ajax image
		document.getElementsByName("ajax")[0].setAttribute("src", AJAX_OFF);
    }
}
function loadTitle(){
	// displaying ajax image
	document.getElementsByName("ajax")[0].setAttribute("src",AJAX_ON);
	
	url=document.getElementsByName("url")[0].value;
	if(document.getElementsByName("title")[0].value==""){
		sndReq('/api/getpageinfo_p.xml?actions=title&url='+url);
	}
}

/* Menüs mit aufklappbare Baumstruktur 
 * Autor: Daniel Thoma
 * URL: http://aktuell.de.selfhtml.org/artikel/dhtml/treemenu/
 * eMail: dthoma@gmx.net
 */ 

/* 
 * Fügt den Listeneinträgen Eventhandler und CSS Klassen hinzu,
 * um die Menüpunkte am Anfang zu schließen.
 * 
 * menu: Referenz auf die Liste.
 * data: String, der die Nummern aufgeklappter Menüpunkte enthält.
 */
  function treeMenu_init(menu, data) {
    var array = new Array(0);
    if(data != null && data != "") {
      array = data.match(/\d+/g);
    }
    var items = menu.getElementsByTagName("li");
    for(var i = 0; i < items.length; i++) {
      items[i].onclick = treeMenu_handleClick;
      if(!treeMenu_contains(treeMenu_getClasses(items[i]), "treeMenu_opened")
          && items[i].getElementsByTagName("ul").length
            + items[i].getElementsByTagName("ol").length > 0) {
        var classes = treeMenu_getClasses(items[i]);
        if(array.length > 0 && array[0] == i) {
          classes.push("treeMenu_opened")
        }
        else {
          classes.push("treeMenu_closed")
        }
        items[i].className = classes.join(" ");
        if(array.length > 0 && array[0] == i) {
          array.shift();
        }
      }
    }
  }
  
/*
 * Ändert die Klasse eines angeclickten Listenelements, sodass
 * geöffnete Menüpunkte geschlossen und geschlossene geöffnet
 * werden.
 *
 * event: Das Event Objekt, dass der Browser übergibt.
 */
  function treeMenu_handleClick(event) {
    if(event == null) { //Workaround für die fehlenden DOM Eigenschaften im IE
      event = window.event;
      event.currentTarget = event.srcElement;
      while(event.currentTarget.nodeName.toLowerCase() != "li") {
        event.currentTarget = event.currentTarget.parentNode;
      }
      event.cancelBubble = true;
    }
    else {
      event.stopPropagation();
    }
    var array = treeMenu_getClasses(event.currentTarget);
    for(var i = 0; i < array.length; i++) {
      if(array[i] == "treeMenu_closed") {
        array[i] = "treeMenu_opened";
      }
      else if(array[i] == "treeMenu_opened") {
        array[i] = "treeMenu_closed"
      }
    }
    event.currentTarget.className = array.join(" ");
  }
  
/*
 * Gibt alle Klassen zurück, die einem HTML-Element zugeordnet sind.
 * 
 * element: Das HTML-Element
 * return: Die zugeordneten Klassen.
 */
  function treeMenu_getClasses(element) {
    if(element.className) {
      return element.className.match(/[^ \t\n\r]+/g);
    }
    else {
      return new Array(0);
    }
  }
  
/*
 * Überprüft, ob ein Array ein bestimmtes Element enthält.
 * 
 * array: Das Array
 * element: Das Element
 * return: true, wenn das Array das Element enthält.
 */
  function treeMenu_contains(array, element) {
    for(var i = 0; i < array.length; i++) {
      if(array[i] == element) {
        return true;
      }
    }
    return false;
  }
  
/*
 * Gibt einen String zurück, indem die Nummern aller geöffneten
 * Menüpunkte stehen. 
 *
 * menu: Referenz auf die Liste
 * return: Der String
 */
  function treeMenu_store(menu) {
    var result = new Array();;
    var items = menu.getElementsByTagName("li");
    for(var i = 0; i < items.length; i++) {
      if(treeMenu_contains(treeMenu_getClasses(items[i]), "treeMenu_opened")) {
        result.push(i);
      }
    }
    return result.join(" ");
  }  
