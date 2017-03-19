<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="general">Server Access Settings</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>IP-Number filter:</td>
    <td><input name="serverfilter" type="text" size="32" maxlength="1000" value="#[serverfilter]#" /><br />(requires restart)</td>
    <td><strong>Here you can restrict access to the server.</strong>  By default, the access is not limited,
           because this function is needed to spawn the p2p index-sharing function.
           If you block access to your server (setting anything else than '*'), then you will also be blocked
           from using other peers' indexes for search service.
           However, blocking access may be correct in enterprise environments where you only want to index your
           company's own web pages.<br />
           Filter have to be entered as IP, IP range or first part of allowed IP's separated by comma (e.g. 10.100.0-100.0-100, 127. ) 
           further details on format see Jetty 
           <a href="http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/server/handler/IPAccessHandler.html" target="_blank">IPAccessHandler</a> docu.
    </td>
  </tr>
  <!--
  <tr valign="top">
    <td><input name="serveruser" type="text" size="16" maxlength="16" value="#[serveruser]#" /></td>
    <td></td>
    <td></td>
  </tr>
  <tr valign="top">
    <td>Password:</td>
    <td><input name="serverpw1" type="password" size="16" maxlength="16" value="" /></td>
    <td></td>
  </tr>
  <tr valign="top">
    <td>Password (repeat same as above):</td>
    <td><input name="serverpw2" type="password" size="16" maxlength="16" value="" /></td>
    <td></td>
  </tr>
  -->
  <tr valign="top">
    <td>staticIP (optional):</td>
    <td><input name="staticIP" type="text" size="32" maxlength="80" value="#[staticIP]#" /></td>
    <td><strong>The staticIP can help that your peer can be reached by other peers in case that your
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
    <td>fileHost:</td>
    <td><input name="fileHost" type="text" size="32" maxlength="80" value="#[fileHost]#" /><br />(requires restart)</td>
    <td><strong>Set this to avoid error-messages like 'proxy use not allowed / granted' on accessing your Peer by its hostname.</strong>
			Virtual host for httpdFileServlet access for example http://FILEHOST/ shall access the file servlet and
			return the defaultFile at rootPath either way, http://FILEHOST/ denotes the same as http://localhost:<port>/
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
    <td>Server port:</td>
    <td><input name="port" type="text" size="7" maxlength="80" value="#[port]#" /></td>
    <td>This is the main port for all http communication. A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td>Server ssl port:</td>
    <td><input name="port.ssl" type="text" size="7" maxlength="80" value="#[server.https_port.ssl]#" /></td>
    <td>This is the port to connect via https. A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td>Shutdown port:</td>
    <td><input name="port.shutdown" type="text" size="7" maxlength="80" value="#[port.shutdown]#" /></td>
    <td>This is the local port on the loopback address (127.0.0.1 or :1) to listen for a shutdown signal to stop the YaCy server (-1 disables the shutdown port).A change requires a restart.</td>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="serverports" class="btn btn-primary" value="Submit" /></td>
  </tr>
</table>
</fieldset>

</form>