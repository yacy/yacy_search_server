function createRequestObject() {
    var ro;
    var browser = navigator.appName;
    if(browser == "Microsoft Internet Explorer"){
        ro = new ActiveXObject("Microsoft.XMLHTTP");
    }else{
        ro = new XMLHttpRequest();
    }
    return ro;
}
var http = createRequestObject();

function sndReq(action) {
    //http.open('get', 'rpc.php?action='+action);
    http.open('get', action);
    http.onreadystatechange = handleResponse;
    http.send(null);
}
