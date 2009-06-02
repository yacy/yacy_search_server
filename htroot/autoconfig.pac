function FindProxyForURL(url,host) {
    // http only
    if (!(url.substring(0, 5).toLowerCase() == "http:")) {
        return "DIRECT";
    }
    
    // no local adresses
    ip = dnsResolve(host);
    if (   isInNet(ip, "127.0.0.0", "255.0.0.0")
        || isInNet(ip, "192.168.0.0", "255.255.0.0")
        || isInNet(ip, "10.0.0.0", "255.0.0.0")
        || isInNet(ip, "172.16.0.0", "255.240.0.0") ) {
        return "DIRECT";
     }
     
     // then
     return "PROXY #[host]#:#[port]#, DIRECT";
}