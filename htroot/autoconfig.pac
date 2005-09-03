function FindProxyForURL(url,host) {
	if ((isPlainHostName(host))||
	    (host=="127.0.0.1"))
	{
		return "DIRECT";
	    
	} else  {
		return "PROXY #[host]#:#[port]#, DIRECT";
	}
}