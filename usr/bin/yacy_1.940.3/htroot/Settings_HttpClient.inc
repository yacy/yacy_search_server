<h3>HTTP client settings</h3>

<p>
	You can configure here some advanced settings of the clients used by YaCy to handle outgoing HTTP connections.
<p>

<p id="sniExtensionInfo">
	About Server Name Indication (SNI) :
	this extension to the <abbr title="Transport Layer Security">TLS</abbr> protocol must be enabled to load some https URLs (for websites deployed with different certificates and host names on the same shared IP address), otherwise loading fails with errors such as <samp>Received fatal alert: handshake_failure</samp>.
	But it can be necessary to disable it in order to load some https URLs served by old and misconfigured web servers, otherwise loading fails with the exception <samp>javax.net.ssl.SSLProtocolException: "handshake alert:  unrecognized_name"</samp>. 
	Controlling <abbr title="Server Name Indication">SNI</abbr> extension activation can also be done with the JVM option <var>jsse.enableSNIExtension</var>, but in that case a server restart is required when you want to modify the setting and it is not customizable per http client (general or for remote Solr).
</p>

<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data" class="form-horizontal">
	<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
	<fieldset>
		<legend>General HTTP client</legend>
		
		<p>Configuration settings for the main HTTP client, used notably to crawl websites and communicate with other YaCy peers.</p>
		
		#(http.outgoing.general.tls.sniExtension.enabled.readonly)#::
		<p class="alert alert-info" role="alert">
			SNI extension support can not be defined here as it is currently configured by the JVM option <code>-Djsse.enableSNIExtension=#[jvmSettingValue]#</code>.
		</p>
		#(/http.outgoing.general.tls.sniExtension.enabled.readonly)#
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="http.outgoing.general.tls.sniExtension.enabled" id="http.outgoing.general.tls.sniExtension.enabled" 
							type="checkbox" #(http.outgoing.general.tls.sniExtension.enabled)#::checked="checked"#(/http.outgoing.general.tls.sniExtension.enabled)# 
							#(http.outgoing.general.tls.sniExtension.enabled.readonly)#::disabled="disabled"#(/http.outgoing.general.tls.sniExtension.enabled.readonly)#
							aria-describedby="sniExtensionInfo"/>
						Enable <abbr title="Server Name Indication">SNI</abbr> extension to <abbr title="Transport Layer Security">TLS</abbr>
					</label>
				</div>
			</div>
		</div>
	</fieldset>
	
	<fieldset>
		<legend>Remote Solr HTTP client</legend>
		
		<p>Configuration settings for the specific HTTP client dedicated to communications with remote Solr servers (located on other YaCy peers or eventually owned by this one when it is configured to use a remote Solr index).</p>
		
		#(http.outgoing.remoteSolr.tls.sniExtension.enabled.readonly)#::
		<p class="alert alert-info" role="alert">
			SNI extension support can not be defined here as it is currently configured by the JVM option <code>-Djsse.enableSNIExtension=#[jvmSettingValue]#</code>.
		</p>
		#(/http.outgoing.remoteSolr.tls.sniExtension.enabled.readonly)#
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="http.outgoing.remoteSolr.tls.sniExtension.enabled" id="http.outgoing.remoteSolr.tls.sniExtension.enabled" 
							type="checkbox" #(http.outgoing.remoteSolr.tls.sniExtension.enabled)#::checked="checked"#(/http.outgoing.remoteSolr.tls.sniExtension.enabled)#
							#(http.outgoing.remoteSolr.tls.sniExtension.enabled.readonly)#::disabled="disabled"#(/http.outgoing.remoteSolr.tls.sniExtension.enabled.readonly)# 
							aria-describedby="sniExtensionInfo"/>
						Enable <abbr title="Server Name Indication">SNI</abbr> extension to <abbr title="Transport Layer Security">TLS</abbr>
					</label>
				</div>
			</div>
		</div>
	</fieldset>
	
    <div class="col-sm-6">
    	<input type="submit" class="btn btn-primary" name="httpClientSettings" value="Submit" aria-describedby="submitInfo"/>
		<em id="submitInfo">Changes will take effect immediately.</em>
    </div>
</form>
