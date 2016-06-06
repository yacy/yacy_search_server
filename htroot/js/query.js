/**
 * Copyright (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 * first published 07.04.2005 on http://yacy.net
 * Licensed under the GNU GPL-v2 license
 */

var query = new Object();

function getQueryProps() {
  var text = "";
  for (property in query) {
    text += property + " = " + query[property] + ";\n";
  }
  return text;
}

function getURLparameters() {
  if (self.location.search.indexOf("=") == -1) {return;}
  var parameterArray = unescape(self.location.search).substring(1).split("&");
  for (var i=0;i<parameterArray.length;i++) {
    parameterArray[i] = parameterArray[i].split("=");
    eval("query." + parameterArray[i][0] + " = \"" + parameterArray[i][1] + "\"");
 }
}