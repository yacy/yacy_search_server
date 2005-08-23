<table border="0" cellpadding="2" cellspacing="1" width="100%">
<tr class="TableHeader"><td colspan="2"><b>Private System Properties</b></td></tr>
<tr class="TableCellDark"><td width="150">Protection</td><td>
#(protection)#
<b>Your settings are _not_ protected!</b> Please go to the <a href="Settings_p.html#admin">settings</a> page <b>immediately</b> and set an administration password.
::
Your settings are protected by a password.
#(/protection)#
</td></tr>
<tr class="TableCellLight"><td>Proxy host</td><td>#[host]#:#[port]#</td></tr>
<tr class="TableCellDark"><td>Port forwarding host</td><td>#(portForwarding)#not used::#[host]#:#[port]# (#(status)#broken::connected#(/status)#)#(/portForwarding)#</td></tr>
<tr class="TableCellLight"><td>Remote proxy</td><td>#(remoteProxy)#not used::#[host]#:#[port]##(/remoteProxy)#</td></tr>
<tr class="TableCellDark"><td>Auto-popup on start-up</td><td>
#(popup)#
Disabled. To enable this again please use the <a href="Settings_p.html#SystemBehaviour">Settings</a> page
::
Enabled. To disable this please use the <a href="Settings_p.html#SystemBehaviour">Settings</a> page
#(/popup)#
</td></tr>
<tr class="TableCellLight"><td>Memory Usage</td><td>
<!--
<form action="Status.html" method="get">free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]# | <input type="submit" name="gc" value="Do Garbage-Collect now"></form>
-->
free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]#
</td></tr>
<tr class="TableCellDark"><td>Traffic</td><td>
Out: #[trafficIn]# | In: #[trafficOut]#
</td></tr>
<tr class="TableCellLight"><td>System Resources</td><td>
Processors: #[processors]#
</td></tr>
<tr class="TableCellDark"><td>Indexing Queue</td><td>#[indexingQueueSize]# | #[indexingQueueMax]#</td></tr>
<tr class="TableCellLight"><td>Loader Queue</td><td>#[loaderQueueSize]# | #[loaderQueueMax]# #(loaderPaused)#::(paused)#(/loaderPaused)#</td></tr>
<tr class="TableCellDark"><td>Local Crawler Queue</td><td>#[localCrawlQueueSize]#</td></tr>
<tr class="TableCellLight"><td>Remote Crawler Queue</td><td>#[remoteCrawlQueueSize]#</td></tr>


</table>