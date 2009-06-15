function FindProxyForURL(url,host) {
#(yacy)#
    // .yacy only
    if(shExpMatch(host,"*.yacy")) {
        return "PROXY #[host]#:#[port]#, DIRECT";
    }
    return "DIRECT";
::
    // http only
    if (!(url.substring(0, 5).toLowerCase() == "http:")) {
        return "DIRECT";
    }
    
    // not the proxy itself
    if (host == "#[host]#") return "DIRECT";
    
    // no local adresses
    ip = dnsResolve(host);
    if (   isInNet(ip, "127.0.0.0", "255.0.0.0")
        || isInNet(ip, "192.168.0.0", "255.255.0.0")
        || isInNet(ip, "10.0.0.0", "255.0.0.0")
        || isInNet(ip, "172.16.0.0", "255.240.0.0") ) {
        return "DIRECT";
     }
     
     // not the proxy itself (better: dnsResolve(#[host]#), but an additional lookup)
     if (ip == "#[host]#") return "DIRECT";
     
     // then
     return "PROXY #[host]#:#[port]#, DIRECT";
#(/yacy)#
}