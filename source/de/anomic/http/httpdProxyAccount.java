package de.anomic.http;

import java.net.InetAddress;

public final class httpdProxyAccount {
	String username;
	InetAddress ip;
	int timeLimit; //max. Time for this user
	int timeUsed;
	boolean timeBlock; //count linear or only activity?
	
	public httpdProxyAccount(InetAddress myip){
		ip=myip;
		timeLimit=0;
		timeUsed=0;
		timeBlock=false;
	}
	public httpdProxyAccount(InetAddress myip, int mytimeLimit){
		ip=myip;
		timeLimit=mytimeLimit;
		timeUsed=0;
		timeBlock=false;
	}
}
