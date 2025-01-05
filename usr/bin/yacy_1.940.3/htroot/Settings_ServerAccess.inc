<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
<fieldset><legend id="general">Server Access Settings</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="serverfilter">IP-Number filter:</label></td>
    <td><input name="serverfilter" id="serverfilter" aria-describedby="serverfilterInfo" type="text" size="32" maxlength="1000" value="#[serverfilter]#" /><br />(requires restart)</td>
    <td id="serverfilterInfo"><strong>Here you can restrict access to the server.</strong>  By default, the access is not limited,
           because this function is needed to spawn the p2p index-sharing function.
           If you block access to your server (setting anything else than '*'), then you will also be blocked
           from using other peers' indexes for search service.
           However, blocking access may be correct in enterprise environments where you only want to index your
           company's own web pages.<br />
           Filter have to be entered as IP, IP range or using CIDR notation separated by comma (e.g. 192.168.1.1,2001:db8::ff00:42:8329,192.168.1.10-192.168.1.20,192.168.1.30-40,192.168.2.0/24) 
           further details on format see Jetty 
           <a href="http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/util/InetAddressSet.html" target="_blank">InetAddressSet</a> documentation.
    </td>
  </tr>
  <tr valign="top">
    <td><label for="staticIP">staticIP (optional):</label></td>
    <td><input name="staticIP" id="staticIP" aria-describedby="staticIPInfo" type="text" size="32" maxlength="80" value="#[staticIP]#" /></td>
    <td id="staticIPInfo"><strong>The staticIP can help that your peer can be reached by other peers in case that your
           peer is behind a firewall or proxy.</strong> You can create a tunnel through the firewall/proxy
           (look out for 'tunneling through https proxy with connect command') and create
           an access point for incoming connections.
           This access address can be set here (either as IP number or domain name).
           If the address of outgoing connections is equal to the address of incoming connections,
           you don't need to set anything here, please leave it blank.<br />
           ATTENTION: Your current IP is recognized as "#[clientIP]#".
           If the value you enter here does not match with this IP,
           you will not be able to access the server pages anymore.</td>
  </tr>
  <tr valign="top">
      <td><label for="publicPort">publicPort (optional):</label></td>
      <td><input name="publicPort" id="publicPort" aria-describedby="publicPortInfo" type="text" size="32" maxlength="80" value="#[publicPort]#" /></td>
      <td id="publicPortInfo"><strong>The publicPort can help that your peer can be reached by other peers in case that your
             peer is behind a reverse proxy.</strong>
             If the port used to access YaCy is the same port the application is listening on,
             you don't need to set anything here, please leave it blank.<br />
      </td>
  </tr>
  <tr valign="top">
    <td><label for="fileHost">fileHost:</label></td>
    <td><input name="fileHost" id="fileHost" aria-describedby="fileHostInfo" type="text" size="32" maxlength="80" value="#[fileHost]#" /><br />(requires restart)</td>
    <td id="fileHostInfo"><strong>Set this to avoid error-messages like 'proxy use not allowed / granted' on accessing your Peer by its hostname.</strong>
			Virtual host for httpdFileServlet access for example http://FILEHOST/ shall access the file servlet and
			return the defaultFile at rootPath either way, http://FILEHOST/ denotes the same as http://localhost:&lt;port&gt;/
			for the preconfigured value 'localpeer', the URL is: http://localpeer/.</td>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="serveraccount" class="btn btn-primary" value="Submit" /></td>
  </tr>
</table>
</fieldset>

<fieldset><legend id="serverports">Server Port Settings</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="port">Server port:</label></td>
    <td><input name="port" id="port" aria-describedby="portInfo" type="text" size="7" maxlength="6" value="#[port]#" /></td>
    <td id="portInfo">This is the main port for all http communication (default is 8090). A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td><label for="port.ssl">Server ssl port:</label></td>
    <td><input name="port.ssl" id="port.ssl" aria-describedby="port.sslInfo" type="text" size="7" maxlength="6" value="#[server.https_port.ssl]#" /></td>
    <td id="port.sslInfo">This is the port to connect via https (default is 8443). A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td><label for="port.shutdown">Shutdown port:</label></td>
    <td><input name="port.shutdown" id="port.shutdown" aria-describedby="port.shutdownInfo" type="text" size="7" maxlength="6" value="#[port.shutdown]#" /></td>
    <td id="port.shutdownInfo">This is the local port on the loopback address (127.0.0.1 or :1) to listen for a shutdown signal to stop the YaCy server (-1 disables the shutdown port, recommended default is 8005). A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="serverports" class="btn btn-primary" value="Submit" /></td>
  </tr>
</table>
</fieldset>

<fieldset>
	<legend>Compression settings</legend>
	
	<div class="form-group">
		<div class="col-sm-3">
			<div class="checkbox">
				<label>
					<input name="server.response.compress.gzip" id="responseCompressGzip" 
						type="checkbox" #(server.response.compress.gzip)#::checked#(/server.response.compress.gzip)# 
						aria-describedby="responseCompressGzipInfo"/>
					Compress responses with gzip
				</label>
			</div>
		</div>
		<div class="col-sm-9" id="responseCompressGzipInfo">
 			When checked (default), HTTP responses can be compressed using gzip. 
 			The requesting user-agent (a web browser, another YaCy peer or any other tool) uses the header 'Accept-Encoding' to tell whether it accepts gzip compression or not. 
 			This adds some processing overhead, but can significantly reduce the amount of bytes transmitted over the network. 
        </div>
	</div>
	<div class="form-group">
    	<div class="col-sm-12">
    		<input type="submit" class="btn btn-primary" name="serverCompression" value="Submit" aria-describedby="submitInfo"/>
			<em id="submitInfo">Changes need a server restart.</em>
    	</div>
    </div>
</fieldset>

</form>