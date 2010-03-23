function createRequestObject() {
    var ro;
    if (window.XMLHttpRequest) {
        ro = new XMLHttpRequest();
    } else if (window.ActiveXObject) {
        ro = new ActiveXObject("Microsoft.XMLHTTP");
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
