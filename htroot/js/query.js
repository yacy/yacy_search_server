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