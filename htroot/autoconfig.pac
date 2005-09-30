function FindProxyForURL(url,host) {
	if ((isPlainHostName(host))||
	    (host=="127.0.0.1")) {
		return "DIRECT";	    
	} else if (url.substring(0, 4).toLowerCase() == "ftp:") {
        return "DIRECT";
    } else  {
		return "PROXY #[host]#:#[port]#, DIRECT";
	}
}