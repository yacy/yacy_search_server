<table class="StatusTable">
  <tr class="TableHeader">
    <td colspan="3"><strong>Private System Properties</strong></td>
  </tr>
  <tr class="TableCellLight">
    <td>System Resources</td>
    <td>Processors: #[processors]#</td>
    <td style="width:50px"></td>
  </tr>
  <tr class="TableCellDark">
    <td>Protection</td>
    <td>
      #(protection)#
      <strong>Your settings are _not_ protected!</strong> Please go to the <a href="ConfigBasic.html">settings</a> page <strong>immediately</strong> and set an administration password.
      ::
      Your settings are protected by a password.
      #(/protection)#
    </td>
    <td></td>
  </tr>
  <tr class="TableCellLight">
    <td>Peer host</td>
    <td>#[host]#:#[port]# #(extPortFormat)#::| (Binding to interface: #[extPort]#)#(/extPortFormat)# #(sslSupport)#::| SSL: enabled#(/sslSupport)#</td>
    <td></td>
  </tr>
  <tr class="TableCellDark">
    <td>Port forwarding host</td>
    <td>#(portForwarding)#not used::#[host]#:#[port]# (#(status)#broken::connected#(/status)#)#(/portForwarding)#</td>
    <td></td>
  </tr>
  <tr class="TableCellLight">
    <td>Remote proxy</td>
    <td>#(remoteProxy)#not used::#[host]#:#[port]# | Used for YaCy -> YaCy communication: #(4Yacy)#Yes::No #(/4Yacy)# #(/remoteProxy)#</td>
    <td></td>
  </tr>
  <tr class="TableCellDark">
    <td>Auto-popup on start-up</td>
      #(popup)#
      <td>
      Disabled
      </td>
      <td>
      <a href="Status.html?popup=true">[Enable]</a>
      </td>
      ::
      <td>
      Enabled
      </td>
      <td>
      <a href="Status.html?popup=false">[Disable]</a>
      </td>
      #(/popup)#
    </td>
  </tr>
  <tr class="TableCellLight">
    <td>Memory Usage</td>
    <td>
      <!--
      <form action="Status.html" method="get">free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]# | <input type="submit" name="gc" value="Do Garbage-Collect now" /></form>
      -->
      free: #[freeMemory]# | total: #[totalMemory]# | max: #[maxMemory]#
    </td>
    <td>[<a href="PerformanceMemory_p.html">Configure</a>]</td>
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
      <div class="ProgressBarCaption">#[indexingQueueSize]#&nbsp;|&nbsp;#[indexingQueueMax]#</div>
      <div class="ProgressBar">
        <div class="ProgressBarFill" style="width:#[indexingQueuePercent]#%;" />
      </div>
    </td>
    <td>[<a href="IndexCreateIndexingQueue_p.html">Details</a>]</td>
  </tr>
  <tr class="TableCellLight">
    <td>Loader Queue</td>
    <td>    
      <div class="ProgressBarCaption">#[loaderQueueSize]#&nbsp;|&nbsp;#[loaderQueueMax]#</div>
      <div class="ProgressBar">
        <div class="ProgressBarFill" style="width:#[loaderQueuePercent]#%;" />
      </div>
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
          <td><a href="Status.html?#(localCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/localCrawlPaused)#=&amp;jobType=localCrawl" title="#(localCrawlPaused)#pause local crawl::continue local crawl#(/localCrawlPaused)#"><img src="env/grafics/#(localCrawlPaused)#stop.gif::start.gif#(/localCrawlPaused)#" alt="#(localCrawlPaused)#pause local crawl::continue local crawl#(/localCrawlPaused)#" style="width:12px;height:12px;" /></a></td>
          <td>#(localCrawlPaused)#&nbsp;::(paused)#(/localCrawlPaused)#</td>
        </tr>
        <tr>
          <td>Remote triggered Crawl</td>
          <td>#[remoteTriggeredCrawlQueueSize]#</td>
          <td><a href="Status.html?#(remoteTriggeredCrawlPaused)#pauseCrawlJob::continueCrawlJob#(/remoteTriggeredCrawlPaused)#=&amp;jobType=remoteTriggeredCrawl" title="#(remoteTriggeredCrawlPaused)#pause remote triggered crawl::continue remote triggered crawl#(/remoteTriggeredCrawlPaused)#"><img src="env/grafics/#(remoteTriggeredCrawlPaused)#stop.gif::start.gif#(/remoteTriggeredCrawlPaused)#" alt="#(remoteTriggeredCrawlPaused)#pause remote triggered crawl::continue remote triggered crawl#(/remoteTriggeredCrawlPaused)#" style="width:12px;height:12px;" /></a></td>
          <td>#(remoteTriggeredCrawlPaused)#&nbsp;::(paused)#(/remoteTriggeredCrawlPaused)#</td>
        </tr>
        <tr>
          <td>Global Crawl Trigger</td>
          <td>#[globalCrawlTriggerQueueSize]#</td>
          <td><a href="Status.html?#(globalCrawlTriggerPaused)#pauseCrawlJob::continueCrawlJob#(/globalCrawlTriggerPaused)#=&amp;jobType=globalCrawlTrigger" title="#(globalCrawlTriggerPaused)#pause global crawl trigger::continue global crawl trigger#(/globalCrawlTriggerPaused)#"><img src="env/grafics/#(globalCrawlTriggerPaused)#stop.gif::start.gif#(/globalCrawlTriggerPaused)#" alt="#(globalCrawlTriggerPaused)#pause global crawl trigger::continue global crawl trigger#(/globalCrawlTriggerPaused)#" style="width:12px;height:12px;" /></a></td>
          <td>#(globalCrawlTriggerPaused)#&nbsp;::(paused)#(/globalCrawlTriggerPaused)#</td>
        </tr>    
        <tr>
          <td>Pre-Queueing</td>
          <td>#[stackCrawlQueueSize]#</td>
          <td></td>
          <td></td>
        </tr>  
      </table>
    </td>
    <td valign="top">[<a href="IndexCreateWWWLocalQueue_p.html">Details</a>]<br />
        &nbsp;<br />
        [<a href="IndexCreateWWWGlobalQueue_p.html">Details</a>]
    </td>
  </tr>
  <tr class="TableCellLight"><td>Seed server</td><td>
    #(seedServer)#
      Disabled. To enable this you need a FTP account where you can upload files to a web space. If you do that, you become a YaCy root server.
      </td>
      <td>
      <a href="Settings_p.html#seed">[Configure]</a>.
      ::
      Enabled: Updating periodically to server #[seedServer]#.
      Last upload: #[lastUpload]# ago.
      ::
      Enabled: Updating periodically to file #[seedFile]#.
      Last upload: #[lastUpload]# ago.
    #(/seedServer)#
  </td></tr>
</table>
