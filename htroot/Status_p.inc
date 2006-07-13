<table border="0" cellpadding="2" cellspacing="1" width="100%">
<tr class="TableHeader">
	<td colspan="3"><b>Private System Properties</b></td>
</tr>
<tr class="TableCellLight">
	<td>System Resources</td>
	<td>Processors: #[processors]#</td>
	<td width="50">&nbsp;</td>
</tr>
<tr class="TableCellDark">
	<td>Protection</td>
	<td>
		#(protection)#
		<b>Your settings are _not_ protected!</b> Please go to the <a href="Settings_p.html?page=admin">settings</a> page <b>immediately</b> and set an administration password.
		::
		Your settings are protected by a password.
		#(/protection)#
	</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellLight">
	<td>Peer host</td>
	<td>#[host]#:#[port]# #(extPortFormat)#::| (Binding to interface: #[extPort]#)#(/extPortFormat)# #(sslSupport)#::| SSL: enabled#(/sslSupport)#</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellDark">
	<td>Port forwarding host</td>
	<td>#(portForwarding)#not used::#[host]#:#[port]# (#(status)#broken::connected#(/status)#)#(/portForwarding)#</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellLight">
	<td>Remote proxy</td>
	<td>#(remoteProxy)#not used::#[host]#:#[port]# | Used for YaCy -> YaCy communication: #(4Yacy)#Yes::No #(/4Yacy)# #(/remoteProxy)#</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellDark">
	<td>Auto-popup on start-up</td>
	<td>
		#(popup)#
		Disabled. To enable this again please use the <a href="Settings_p.html?page=SystemBehaviour">Settings</a> page.
		::
		Enabled. To disable this please use the <a href="Settings_p.html?page=SystemBehaviour">Settings</a> page.
		#(/popup)#
	</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellLight">
	<td>Memory Usage</td>
	<td>
		<!--
		<form action="Status.html" method="get">free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]# | <input type="submit" name="gc" value="Do Garbage-Collect now"></form>
		-->
		free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]#
	</td>
	<td>&nbsp;</td>
</tr>
<tr class="TableCellDark">
	<td>Traffic</td>
	<td>Proxy: #[trafficOut]# | Crawler: #[trafficCrawler]#</td>
	<td>[<a href="Status.html?ResetTraffic=">Reset</a>]</td>
</tr>
<tr class="TableCellLight">
	<td>Connections Incoming</td>
	<td>Active: #[connectionsActive]# | Idle: #[connectionsIdle]# | Max: #[connectionsMax]#</td>
	<td>[<a href="Connections_p.html">Details</a>]</td>
</tr>
<tr class="TableCellDark">
	<td>Indexing Queue</td>
	<td>
		<table border="0" cellpadding="0" cellspacing="0" width="200" >
		<tr>
			<td width="70">#[indexingQueueSize]#&nbsp;|&nbsp;#[indexingQueueMax]#&nbsp;</td>
			<td>
				<div width="150" height="10" style="border-style:solid; border-width:1px; border-color:#CAD5E3">
				<img src="env/grafics/green_bar.gif" width="#[indexingQueuePercent]#%" height="10" alt="">
				</div>
			</td>
		</tr>
		</table>	
	</td>
	<td>[<a href="IndexCreateIndexingQueue_p.html">Details</a>]</td>
</tr>
<tr class="TableCellLight">
	<td>Loader Queue</td>
	<td>		
		<table border="0" cellpadding="0" cellspacing="0" width="200" >
		<tr>
			<td width="70">#[loaderQueueSize]#&nbsp;|&nbsp;#[loaderQueueMax]#&nbsp;</td>
			<td>
				<div width="150" height="10" style="border-style:solid; border-width:1px; border-color:#CAD5E3">
				<img src="env/grafics/green_bar.gif" width="#[loaderQueuePercent]#%" height="10" alt="">
				</div>
			</td>
		</tr>
		</table>		 
	</td>
	<td>[<a href="IndexCreateLoaderQueue_p.html">Details</a>]</td>
</tr>
<tr class="TableCellDark">
	<td>Crawler Queues</td>
	<td>
		<table>
		<tr>
			<td>Local Crawl</td>
			<td>#[localCrawlQueueSize]#</td>
			<td><a href="Status.html?#(localCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/localCrawlPaused)#=&jobType=localCrawl" title="#(localCrawlPaused)#pause local crawl::continue local crawl#(/localCrawlPaused)#"><img src="env/grafics/#(localCrawlPaused)#stop.gif::start.gif#(/localCrawlPaused)#" border="0" width="12" height="12"></a></td>
			<td>#(localCrawlPaused)#&nbsp;::(paused)#(/localCrawlPaused)#</td>
		</tr>
		<tr>
			<td>Remote triggered Crawl</td>
			<td>#[remoteTriggeredCrawlQueueSize]#</td>
			<td><a href="Status.html?#(remoteTriggeredCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/remoteTriggeredCrawlPaused)#=&jobType=remoteTriggeredCrawl" title="#(remoteTriggeredCrawlPaused)#pause remote triggered crawl::continue remote triggered crawl#(/remoteTriggeredCrawlPaused)#"><img src="env/grafics/#(remoteTriggeredCrawlPaused)#stop.gif::start.gif#(/remoteTriggeredCrawlPaused)#" border="0" width="12" height="12"></a></td>
			<td>#(remoteTriggeredCrawlPaused)#&nbsp;::(paused)#(/remoteTriggeredCrawlPaused)#</td>
		</tr>
		<tr>
			<td>Global Crawl Trigger</td>
			<td>#[globalCrawlTriggerQueueSize]#</td>
			<td><a href="Status.html?#(globalCrawlTriggerPaused)#pauseCrawlJob::continueCrawlJob#(/globalCrawlTriggerPaused)#=&jobType=globalCrawlTrigger" title="#(globalCrawlTriggerPaused)#pause global crawl trigger::continue global crawl trigger#(/globalCrawlTriggerPaused)#"><img src="env/grafics/#(globalCrawlTriggerPaused)#stop.gif::start.gif#(/globalCrawlTriggerPaused)#" border="0" width="12" height="12"></a></td>
			<td>#(globalCrawlTriggerPaused)#&nbsp;::(paused)#(/globalCrawlTriggerPaused)#</td>
		</tr>		
		<tr>
			<td>Pre-Queueing</td>
			<td>#[stackCrawlQueueSize]#</td>
			<td>&nbsp;</td>
			<td>&nbsp;</td>
		</tr>	
		</table>
	</td>
	<td valign="top">[<a href="IndexCreateWWWLocalQueue_p.html">Details</a>]<br>
	    &nbsp;<br>
	   	[<a href="IndexCreateWWWGlobalQueue_p.html">Details</a>]
	</td>
</tr>
<tr class="TableCellLight"><td>Seed server</td><td>
	#(seedServer)#
		Disabled. To enable this you need a FTP account where you can upload files to a web space. If you do that, you become a YaCy root server. You can configure your account details on the <a href="Settings_p.html#seed">Settings page</a>.
		::
			Enabled: Updating periodically to server #[seedServer]#.
			Last upload: #[lastUpload]# ago.
		::
			Enabled: Updating periodically to file #[seedFile]#.
			Last upload: #[lastUpload]# ago.
		#(/seedServer)#
</td></tr>
</table>
