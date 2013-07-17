<div class="sidebar">
<fieldset style="width: 230px;">
  <legend>System Status</legend>
  <dl>

    <dt>System</dt>
    <dd>YaCy version #[versionpp]#
    #(peerStatistics)#
      Unknown
      ::
      <div>Uptime: #[uptime]#</div>
    #(/peerStatistics)#
    <div>Processors: #[processors]#</div>
    <div>Load: #[load]#</div>
    
    </dd>
    <dt>Protection</dt>
    <dd>#(protection)#
      <strong>Password is missing.</strong>
      ::
      password-protected
      #(/protection)#

      #(unrestrictedLocalAccess)#
      ::
      <br />Unrestricted access from localhost.
      #(/unrestrictedLocalAccess)#
      <a href="ConfigAccounts_p.html">[Configure]</a>
    </dd>
    
    <dt>Address</dt>
    <dd>Host: #[host]#:#[port]# #(extPortFormat)#::| (Binding to interface: #[extPort]#)#(/extPortFormat)# #(sslSupport)#::| SSL: enabled#(/sslSupport)#<br />
    #(peerAddress)#
      peer address not assigned
      ::
      Public Address: http://#[address]#<br/>
      YaCy Address: http://#[peername]#.yacy
      #(/peerAddress)#</dd>
    
    #(portForwarding)#::
    <dt>Port Forwarding Host</dt>
    <dd>#[host]#:#[port]# (#(status)#broken::connected#(/status)#)</dd>
    #(/portForwarding)#
    
    <dt>Remote Proxy</dt>
    <dd>#(remoteProxy)#not used::#[host]#:#[port]# | Used for YaCy -> YaCy communication: #(4Yacy)#Yes::No #(/4Yacy)# #(/remoteProxy)#</dd>
    
    <dt>Auto-popup on start-up</dt>
    <dd>
    #(popup)#
    Disabled <a href="Status.html?popup=true">[Enable]</a>
    ::
    Enabled <a href="Status.html?popup=false">[Disable]</a>
    #(/popup)#
    </dd>
    
    <dt>Tray-Icon</dt>
    <dd>
    #(tray)#
    Disabled <a href="Status.html?tray=true">[Enable]</a>
    ::
    Enabled <a href="Status.html?tray=false">[Disable]</a>
    ::
    <a href="ConfigProperties_p.html" onclick="alert('Set\ntray.icon.force = true\n\nWARNING:\nYou do this on your own risk. If you do this without YaCy running on a desktop-pc, this will possibly break startup. In this case, you will have to edit the configuration manually in DATA/SETTINGS/yacy.conf');">Experimental</a>
    #(/tray)#
    </dd>
      
    <dt><a href="PerformanceMemory_p.html">Memory Usage</a></dt>
    <dd>
    free: #[freeMemory]#<br/>
    total: #[totalMemory]#<br/>
    max: #[maxMemory]#</dd>
    
    <dt>Traffic [<a href="Status.html?ResetTraffic=">Reset</a>]</dt>
    <dd>Proxy: #[trafficProxy]#<br/>Crawler: #[trafficCrawler]#</dd>
    
    <dt><a href="Connections_p.html">Incoming Connections</a></dt>
    <dd>Active: #[connectionsActive]# | Max: #[connectionsMax]#</dd>
    
    <dt>Queues</dt>
    <dd>
    <a href="IndexCreateLoaderQueue_p.html">Loader Queue</a>:
    <table>
      <tr>
        <td><div class="ProgressBar"><div class="ProgressBarFill" style="width:#[loaderQueuePercent]#%;" ></div></div></td>
        <td><div class="ProgressBarCaption">#[loaderQueueSize]#&nbsp;|&nbsp;#[loaderQueueMax]#</div></td>
      </tr>
      <tr>
        <td><a href="IndexCreateQueues_p.html?stack=LOCAL">Local Crawl</a></td>
        <td>#[localCrawlQueueSize]#</td>
        <td><a href="Status.html?#(localCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/localCrawlPaused)#=&amp;jobType=localCrawl" title="#(localCrawlPaused)#pause local crawl::continue local crawl#(/localCrawlPaused)#"><img src="env/grafics/#(localCrawlPaused)#stop.gif::start.gif#(/localCrawlPaused)#" alt="#(localCrawlPaused)#pause local crawl::continue local crawl#(/localCrawlPaused)#" style="width:12px;height:12px;" /></a></td>
        <td>#(localCrawlPaused)#&nbsp;::(paused)#(/localCrawlPaused)#</td>
      </tr>
      <tr>
        <td><a href="IndexCreateQueues_p.html?stack=REMOTE">Remote triggered Crawl</a></td>
        <td>#[remoteTriggeredCrawlQueueSize]#</td>
        <td><a href="Status.html?#(remoteTriggeredCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/remoteTriggeredCrawlPaused)#=&amp;jobType=remoteTriggeredCrawl" title="#(remoteTriggeredCrawlPaused)#pause remote triggered crawl::continue remote triggered crawl#(/remoteTriggeredCrawlPaused)#"><img src="env/grafics/#(remoteTriggeredCrawlPaused)#stop.gif::start.gif#(/remoteTriggeredCrawlPaused)#" alt="#(remoteTriggeredCrawlPaused)#pause remote triggered crawl::continue remote triggered crawl#(/remoteTriggeredCrawlPaused)#" style="width:12px;height:12px;" /></a></td>
        <td>#(remoteTriggeredCrawlPaused)#&nbsp;::(paused)#(/remoteTriggeredCrawlPaused)#</td>
      </tr>
      <tr>
        <td>Pre-Queueing</td>
        <td>#[stackCrawlQueueSize]#</td>
        <td></td>
        <td></td>
      </tr>  
      </table>
    </dd>
    
    <dt>Seed server</dt>
    <dd>
    #(seedServer)#
      Disabled.
      <a href="Settings_p.html?page=seed">[Configure]</a>
      ::
      Enabled: Updating to server #[seedServer]#.
      Last upload: #[lastUpload]# ago.
      ::
      Enabled: Updating to file #[seedFile]#.
      Last upload: #[lastUpload]# ago.
    #(/seedServer)#
    </dd>
    
  </dl>
</fieldset>
</div>