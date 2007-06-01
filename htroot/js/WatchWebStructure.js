function changeHost(){
	window.location.replace("http://"+window.location.host+":"+window.location.port+"/WatchWebStructure_p.html?host="+document.getElementById("host").value);
}