function changeHost(){
	window.location.replace("http://"+window.location.host+":"+window.location.port+"/WatchWebStructure_p.html?host="+document.getElementById("host").value);
}
function keydown(ev){
        if(ev.which==13){
                changeHost();
        }
}