/*    
* Copyright (C) 2005 - 2014 Alexander Schier, Michael Peter Christen, 
* and other YaCy developers (see https://yacy.net/en/Join.html)
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <https://www.gnu.org/licenses/>.
*/

DELETE_STRING="delete";
BAR_IMG1="env/grafics/green-block.png";
BAR_IMG2="env/grafics/red-block.png";
WORDCACHEBAR_LENGTH=1/4;

var statusRPC;
var refreshInterval=2;
var wait=0;
var changing=false; //change the interval
var statusLoaded=true;
/* Running crawls table DOM element */
var crawlsTable;
/* Size of the running crawls table header */
var crawlsHeadLength;
/* Running crawls legend DOM element */
var runningCrawlsLegend;
/* true when debug is enabled */
var debug;

function initCrawler(){
	initCrawlProfiles();
	
    refresh();
    //loadInterval=window.setInterval("refresh()", refreshInterval*1000);
    countInterval=window.setInterval("countdown()", 1000);
}

/**
 * Init variables used to refresh the running crawls table
 */
function initCrawlProfiles() {
	debug = document.getElementById("headerDebug") != null;
	crawlsTable = document.getElementById("crawlProfiles");
	if(crawlsTable != null && crawlsTable.rows != null) {
		crawlsHeadLength = crawlsTable.tHead != null ? crawlsTable.tHead.rows.length : 0;
	}
	runningCrawlsLegend = document.getElementById("runningCrawlsLegend");
}

function changeInterval(){
	if(!changing){
		window.clearInterval(countInterval);
		counter=document.getElementById("nextUpdate");
		//counter.innerHTML='<input type="text" id="newInterval" onblur="newInterval()" size="2" />';
		//document.getElementById("newInterval").focus();
		counter.value=refreshInterval;
		changing=true;
	}
}
function newInterval(){
	var newInterval=document.getElementById("nextUpdate").value;
        // make sure that only positive intervals can be set
        if(newInterval>0){
                refreshInterval=newInterval;
        }
	refresh();
	countInterval=window.setInterval("countdown()", 1000);
	changing=false;
}

function countdown(){
	if(statusLoaded){
        wait--;
		if (wait == 0) {
			refresh();
		}
	}
}

function refresh(){
	wait=refreshInterval;
	statusLoaded=false;
	requestStatus();
	getRSS("api/feed.xml?count=20&set=REMOTEINDEXING,LOCALINDEXING&time=" + (new Date()).getTime());
}

function requestStatus(){
	statusRPC=createRequestObject();
	statusRPC.open('get', 'api/status_p.xml?html=');
	statusRPC.onreadystatechange = handleStatus;
	statusRPC.send(null);
}

function handleStatus(){
    if(statusRPC.readyState != 4){
		return;
	}
	var statusResponse = statusRPC.responseXML;
	var statusTag = getFirstChild(statusResponse, "status");
	
	var ppm = getValue(getFirstChild(statusTag, "ppm"));
	
	var ppmNum = document.getElementById("ppmNum");
	removeAllChildren(ppmNum);
	ppmNum.appendChild(document.createTextNode(ppm));
	
	// ppmBar start
	var ppmBar = document.getElementById("ppmbar");
	var ppmBarMaxRead = document.getElementById("customPPM");
	
    var ppmforppmbar = ppm.replace(/\.*/g,"");	
	ppmBar.setAttribute("value", ppmforppmbar);
    ppmBar.setAttribute("max", ppmBarMaxRead.value);
	// ppmBar end
	
	// traffic output (no bar up to now)
    var traffic = getFirstChild(statusTag, "traffic");
    var trafficCrawlerValue = getValue(getFirstChild(traffic, "crawler"));
    var trafCrawlerSpan = document.getElementById("trafficCrawler");
    removeAllChildren(trafCrawlerSpan);
	trafCrawlerSpan.appendChild(document.createTextNode(Math.round((trafficCrawlerValue) / 1024 / 10.24) / 100));
	
	var dbsize = getFirstChild(statusTag, "dbsize");
	var urlpublictext = getValue(getFirstChild(dbsize, "urlpublictext"));
	var urlpublictextSegmentCount = getValue(getFirstChild(dbsize, "urlpublictextSegmentCount"));
	var webgraph = getValue(getFirstChild(dbsize, "webgraph"));
	var webgraphSegmentCount = getValue(getFirstChild(dbsize, "webgraphSegmentCount"));
	var citation = getValue(getFirstChild(dbsize, "citation"));
	var citationSegmentCount = getValue(getFirstChild(dbsize, "citationSegmentCount"));
	var rwipublictext = getValue(getFirstChild(dbsize, "rwipublictext"));
	var rwipublictextSegmentCount = getValue(getFirstChild(dbsize, "rwipublictextSegmentCount"));
	document.getElementById("urlpublictextSize").firstChild.nodeValue=urlpublictext;
	document.getElementById("urlpublictextSegmentCount").firstChild.nodeValue=urlpublictextSegmentCount;
	document.getElementById("webgraphSize").firstChild.nodeValue=webgraph;
	document.getElementById("webgraphSegmentCount").firstChild.nodeValue=webgraphSegmentCount;
	document.getElementById("citationSize").firstChild.nodeValue=citation;
	document.getElementById("citationSegmentCount").firstChild.nodeValue=citationSegmentCount;
	document.getElementById("rwipublictextSize").firstChild.nodeValue=rwipublictext;
	document.getElementById("rwipublictextSegmentCount").firstChild.nodeValue=rwipublictextSegmentCount;
	
	refreshRunningCrawls(statusTag);

	var postprocessing = getFirstChild(statusTag, "postprocessing");
	document.getElementById("postprocessing_status").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "status"));
	document.getElementById("postprocessing_collection").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "collectionRemainingCount"));
	document.getElementById("postprocessing_webgraph").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "webgraphRemainingCount"));
	document.getElementById("postprocessing_remainingTimeMinutes").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeMinutes"));
	document.getElementById("postprocessing_remainingTimeSeconds").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeSeconds"));
	var postprocessingElapsedTime = parseInt(getValue(getFirstChild(postprocessing, "ElapsedTime")));
	var postprocessingRemainingTime = parseInt(getValue(getFirstChild(postprocessing, "RemainingTime")));
	var p = 100 * postprocessingElapsedTime / (postprocessingElapsedTime + postprocessingRemainingTime) || 0;
	document.getElementById("postprocessing_bar").firstChild.setAttribute("value",  p);
	//document.getElementById("postprocessing_speed").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "speed"));
	
	var load = getFirstChild(statusTag, "load");
	document.getElementById("load").firstChild.nodeValue=getValue(load);
	
	var loaderqueue = getFirstChild(statusTag, "loaderqueue");	
	var loaderqueue_size = getValue(getFirstChild(loaderqueue, "size"));
	var loaderqueue_max = getValue(getFirstChild(loaderqueue, "max"));
	document.getElementById("loaderqueuesize").firstChild.nodeValue=loaderqueue_size;
	document.getElementById("loaderqueuemax").firstChild.nodeValue=loaderqueue_max;
	
	var localcrawlerqueue = getFirstChild(statusTag, "localcrawlerqueue");
	var localcrawlerqueue_size = getValue(getFirstChild(localcrawlerqueue, "size"));
	var localcrawlerqueue_state = getValue(getFirstChild(localcrawlerqueue, "state"));
	document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
	putQueueState("localcrawler", localcrawlerqueue_state);
	
	var limitcrawlerqueue = getFirstChild(statusTag, "limitcrawlerqueue");
	var limitcrawlerqueue_size = getValue(getFirstChild(limitcrawlerqueue, "size"));
	var limitcrawlerqueue_state = getValue(getFirstChild(limitcrawlerqueue, "state"));
	document.getElementById("limitcrawlerqueuesize").firstChild.nodeValue=limitcrawlerqueue_size;
	putQueueState("limitcrawler", limitcrawlerqueue_state);
	
	var remotecrawlerqueue = getFirstChild(statusTag, "remotecrawlerqueue");
	var remotecrawlerqueue_size = getValue(getFirstChild(remotecrawlerqueue, "size"));
	var remotecrawlerqueue_state = getValue(getFirstChild(remotecrawlerqueue, "state"));
	document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
	putQueueState("remotecrawler", remotecrawlerqueue_state);
	
	var noloadcrawlerqueue = getFirstChild(statusTag, "noloadcrawlerqueue");
	var noloadcrawlerqueue_size = getValue(getFirstChild(noloadcrawlerqueue, "size"));
	var noloadcrawlerqueue_state = getValue(getFirstChild(noloadcrawlerqueue, "state"));
	document.getElementById("noloadcrawlerqueuesize").firstChild.nodeValue=noloadcrawlerqueue_size;
	putQueueState("noloadcrawler", noloadcrawlerqueue_state);

	statusLoaded=true;
}

/**
 * Insert a new crawl line to the end of the running crawls table
 * @param table crawls table HTML DOM node
 * @param crawl crawl profile node from status_p.xml
 * @param handle {String} identifier of the running crawl profile
 * @param status {String} running status of the crawl profile
 */
function insertCrawlRaw(table, crawl, handle, status) {
	  /* Insert a row in the table at the end */
	  var newRow = table.insertRow();
	  newRow.className = ((table.rows.length - crawlsHeadLength) % 2) == 0 ? "TableCellLight" : "TableCellDark";
	  newRow.id = handle;

	  /* Insert name cell */
	  var newCell = newRow.insertCell();
	  var newText = document.createTextNode(getValue(getFirstChild(crawl, "name")));
	  newCell.appendChild(newText);
	  
	  if(debug) {
		  /* Insert count cell when debug is enabled */
		  newCell = newRow.insertCell();
		  newCell.textContent = getValue(getFirstChild(crawl, "count"));
	  }
	  
	  /* Insert status cell */
	  newCell = newRow.insertCell();
	  newCell.id = handle + "_status_cell";
	  
	  if(status == "alive") {
		  var newDiv = document.createElement("div");
		  newDiv.id = handle + "_status";
		  newDiv.style = "text-decoration:blink;float:left;";
		  
		  newText = document.createTextNode("Running");
		  newDiv.appendChild(newText);
		  
		  newCell.appendChild(newDiv);
		  
		  var newForm = document.createElement("form");
		  newForm.id = handle + "_terminate";
		  newForm.style = "float:left;";
		  newForm.action = "Crawler_p.html";
		  newForm.method = "get";
		  newForm.enctype="multipart/form-data";
		  newForm["accept-charset"]="UTF-8";
		  
		  newDiv = document.createElement("div");
		  
		  var newInput = document.createElement("input");
		  newInput.type = "hidden";
		  newInput.name = "handle";
		  newInput.value = handle;
		  
		  newDiv.appendChild(newInput);
		  
		  newInput = document.createElement("input");
		  newInput.type = "submit";
		  newInput.name = "terminate";
		  newInput.value = "Terminate";
		  newInput.className = "btn btn-danger btn-xs";
		  
		  newDiv.appendChild(newInput);
		  
		  newForm.appendChild(newDiv);
		  
		  newCell.appendChild(newForm);
	  }
}

/**
 * Refresh status cell text and terminate button presence
 * @param handle name of the crawl
 * @param status current crawl status label
 */
function refreshStatusCell(handle, status) {
	var handleStatus = document.getElementById(handle + "_status");
	if(handleStatus != null) {
		handleStatus.textContent = status;
	}
	var terminateForm = document.getElementById(handle + "_terminate");
	if(terminateForm != null && terminateForm.parentElement) {
		terminateForm.parentElement.removeChild(terminateForm);
	}
}

/**
 * Refresh the count in running crawls legend
 * @param legend the HTML DOM legend element
 * @param crawls crawls node from xml api status_p.xml
 */
function refreshCrawlsLegend(legend, crawls) {
	var count = crawls.getAttribute("count");
	if(count && legend != null) {
		legend.textContent = "Running Crawls (" + count + ")";
	}
}

/**
 * Refresh dark/light rows style
 * @param table running crawls table
 */
function refreshRowsStyle(table, headLength) {
	for(var i = headLength; i < table.rows.length; i++) {
		raw = table.rows[i];
		raw.className = ((i - headLength) % 2) == 0 ? "TableCellLight" : "TableCellDark";
	}
}

/**
 * Refresh running crawls table
 * 
 * @param statusTag
 *            status tag from xml api status_p.xml
 */
function refreshRunningCrawls(statusTag) {
	var crawls = getFirstChild(statusTag, "crawls");
	/* crawls node should be present even when no crawl is running */
	if(crawls != null) {
		/* Update the table when present */
		if(crawlsTable != null && crawlsTable.rows != null) {
			var processedHandles = {}, crawlNode = getFirstChild(crawls, "crawl");
			
			if(crawlNode) {
				var handle, rowIndex, handleCell;
				/* Loop on crawl node elements from xml */
				for(; crawlNode; crawlNode = getNextSibling(crawlNode, "crawl")) {
					handle = getValue(getFirstChild(crawlNode, "handle"));
					if(handle != null) {
						processedHandles[handle] = crawlNode;
						status = getValue(getFirstChild(crawlNode, "status"));
						/* Let's try to get the crawls table cell with id prefixed by this handle */
						handleCell = document.getElementById(handle + "_status_cell");
						if(handleCell == null) {
							insertCrawlRaw(crawlsTable, crawlNode, handle, status);
							refreshCrawlsLegend(runningCrawlsLegend, crawls);
							refreshRowsStyle(crawlsTable, crawlsHeadLength);
						} else if(status != "alive"){
							refreshStatusCell(handle, status);
						}
					}
				}
			}
			
			/* Collect raws to delete */
			var raw, rawsToDelete = [];
			for(var i = crawlsHeadLength; i < crawlsTable.rows.length; i++) {
				raw = crawlsTable.rows[i];
				if(processedHandles[raw.id] == null) {
					rawsToDelete.push(raw);
				}
			}
			
			/* Delete raws */
			for(var i = 0; i < rawsToDelete.length; i++) {
				raw = rawsToDelete[i];
				raw.parentElement.removeChild(raw);
			}
			/* Refresh legend and rows style (dark/light alternate) */
			if(rawsToDelete.length > 0) {
				refreshCrawlsLegend(runningCrawlsLegend, crawls);
				refreshRowsStyle(crawlsTable, crawlsHeadLength);
			}
		}
	}
}

function putQueueState(queue, state) {
	a = document.getElementById(queue + "stateA");
	img = document.getElementById(queue + "stateIMG");
	if (state == "paused") {
		a.href = "Crawler_p.html?continue=" + queue;
		a.title = "Continue this queue (" + state + ")";
		img.src = "env/grafics/start.gif";
		img.alt = "Continue this queue";
	} else {
		a.href = "Crawler_p.html?pause=" + queue;
		a.title = "Pause this queue (" + state + ")";
		img.src = "env/grafics/pause.gif";
		img.alt = "Pause this queue";
	}
}

function shortenURL(url) {
	if (url.length > 80) {
		return url.substr(0, 80) + "...";
	} else {
		return url;
	}
}

function createIndexingRow(queue, profile, initiator, depth, modified, anchor, url, size, deletebutton){
    row=document.createElement("tr");
    row.setAttribute("height", 10);
    row.appendChild(createCol(queue));
    row.appendChild(createCol(profile));
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, shortenURL(url)));
	row.appendChild(createCol(size));
	row.appendChild(deletebutton);
	return row;
}

crawllist_head = "<table cellpadding='2' cellspacing='1' ><tr class='TableHeader'><td width='50%'><strong>Title</strong></td><td width='50%'><strong>URL</strong></td></tr>";
crawllist_body = "";
crawllist_tail = "</table>";
function showRSS(RSS) {
  var doc = document.getElementById("crawllist");
  if (doc != null) {
    if (crawllist_body.length > 100000) crawllist_body = "";
    for (var i=0; i<RSS.items.length; i++) {
      crawllist_body = "<tr class='TableCellLight'><td><a href='ViewFile.html?action=info&urlHash=" + RSS.items[i].guid.value + "' class='small' target='_blank' title='" + RSS.items[i].link + "'>" + RSS.items[i].description + "</a></td><td><a href='" + RSS.items[i].link + "' class='small' target='_blank' title='" + RSS.items[i].link + "'>" + RSS.items[i].link + "</a></td></tr>" + crawllist_body;
    }
    doc.innerHTML = crawllist_head + crawllist_body + crawllist_tail;
  }
  return true;
}
