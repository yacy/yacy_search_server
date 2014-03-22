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

function fadeOutBar() {
	document.getElementById("progressbar").setAttribute('style',"transition:transform 0s;-webkit-transition:-webkit-transform 0s;backgroundColor:transparent;");
}

function statistics(offset, itemscount, itemsperpage, totalcount, localResourceSize, remoteResourceSize, remoteIndexCount, remotePeerCount, navurlbase) {
  if (totalcount == 0) return;
  if (offset >= 0) document.getElementById("offset").innerHTML = offset;
  if (offset >= 0) document.getElementById("startRecord").setAttribute('value', offset - 1);
  if (itemscount >= 0) document.getElementById("itemscount").firstChild.nodeValue = itemscount;
  document.getElementById("totalcount").firstChild.nodeValue = totalcount;
  if (document.getElementById("localResourceSize") != null) document.getElementById("localResourceSize").firstChild.nodeValue = localResourceSize;
  if (document.getElementById("remoteResourceSize") != null) document.getElementById("remoteResourceSize").firstChild.nodeValue = remoteResourceSize;
  if (document.getElementById("remoteIndexCount") != null) document.getElementById("remoteIndexCount").firstChild.nodeValue = remoteIndexCount;
  if (document.getElementById("remotePeerCount") != null) document.getElementById("remotePeerCount").firstChild.nodeValue = remotePeerCount;
  // compose page navigation

  if (document.getElementById("progressbar").getAttribute('class') != "progress-bar progress-bar-success") {
	  var percent = 100 * (itemscount - offset + 1) / itemsperpage;
	  if (percent == 100) {
		  document.getElementById("progressbar").setAttribute('style',"transition:transform 0s;-webkit-transition:-webkit-transform 0s;");
		  document.getElementById("progressbar").setAttribute('class',"progress-bar progress-bar-success");
		  window.setTimeout(fadeOutBar, 500);
	  }
	  document.getElementById("progressbar").setAttribute('style',"width:" + percent + "%");
  }
  var resnavElement = document.getElementById("resNav");
  if (resnavElement != null) {
	  var resnav = "<ul class=\"pagination\">";
	  thispage = Math.floor(offset / itemsperpage);
	  if (thispage == 0) {
	  	resnav += "<li class=\"disabled\"><a href=\"#\">&laquo;</a></li>";
	  } else {
	  	resnav += "<li><a id=\"prevpage\" href=\"";
	    resnav += (navurlbase + "&amp;startRecord=" + ((thispage - 1) * itemsperpage));
	  	resnav += "\">&laquo;</a></li>";
	  }
	  
	  numberofpages = Math.floor(Math.min(10, 1 + ((totalcount.replace(/\./g,'') - 1) / itemsperpage)));
	  if (!numberofpages) numberofpages = 10;
	  for (i = 0; i < numberofpages; i++) {
	      if (i == thispage) {
	         resnav += "<li class=\"active\"><a href=\"#\">";
	         resnav += (i + 1);
	         resnav += "</a></li>";
	      } else {
	         resnav += "<li><a href=\"";
	         resnav += (navurlbase + "&amp;startRecord=" + (i * itemsperpage));
	         resnav += "\">" + (i + 1) + "</a></li>";
	      }
	  }
	  if (thispage >= numberofpages) {
	  	resnav += "<li><a href=\"#\">&raquo;</a></li>";
	  } else {
	      resnav += "<li><a id=\"nextpage\" href=\"";
	      resnav += (navurlbase + "&amp;startRecord=" + ((thispage + 1) * itemsperpage));
	      resnav += "\">&raquo;</a>";
	  }
	  resnav += "</ul>";
	  resnavElement.innerHTML = resnav;
  }
}


