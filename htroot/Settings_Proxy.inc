<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="proxy">Remote Proxy (optional)</legend>
<p>YaCy can use another proxy to connect to the internet. You can enter the address for the remote proxy here:</p>

<table border="0" cellspacing="5">
  <!-- enabling/disabling remote proxy usage -->
  <tr valign="top">
    <td><label for="rem_prxy">Use remote proxy</label>:</td>
    <td><input type="checkbox" name="remoteProxyUse" id="rem_prxy"#(remoteProxyUseChecked)#:: checked="checked"#(/remoteProxyUseChecked)# /></td>
    <td>&nbsp;</td>
    <td>Enables the usage of the remote proxy by yacy</td>
  </tr>
  <tr valign="top">
    <td colspan="2"><label for="prxy4yacy">Use remote proxy for yacy &lt;-&gt; yacy communication</label></td>
    <td><input type="checkbox" name="remoteProxyUse4Yacy" id="prxy4yacy"#(remoteProxyUse4Yacy)#:: checked="checked"#(/remoteProxyUse4Yacy)# /></td>
  	<td>
  	  Specifies if the remote proxy should be used for the communication of this peer to other yacy peers.<br />
  	  <em>Hint:</em> Enabling this option could cause this peer to remain in junior status.
  	</td>
  </tr>
  <tr valign="top">
    <td colspan="2"><label for="prxy4ssl">Use remote proxy for HTTPS</label></td>
    <td><input type="checkbox" name="remoteProxyUse4SSL" id="prxy4ssl"#(remoteProxyUse4SSL)#:: checked="checked"#(/remoteProxyUse4SSL)# /></td>
  	<td>Specifies if YaCy should forward ssl connections to the remote proxy.</td>  
  </tr>  
  <tr><td colspan="4"><hr /></td></tr>
  
  <!-- remote proxy hostname + port -->
  <tr valign="top">
    <td><label for="host">Remote proxy host</label>:</td>
    <td colspan="2"><input name="remoteProxyHost" id="host" type="text" size="32" maxlength="128" value="#[remoteProxyHost]#" /></td>
    <td>The ip address or domain name of the remote proxy</td>
  </tr>
  <tr valign="top">
    <td><label for="port">Remote proxy port</label>:</td>
    <td colspan="2"><input name="remoteProxyPort" id="port" type="text" size="5" maxlength="5" value="#[remoteProxyPort]#" /></td>
    <td>the port of the remote proxy</td>
  </tr>
  
  <!-- remote proxy username + pwd -->
  <tr valign="top">
    <td><label for="user">Remote proxy user</label>:</td>
    <td colspan="2"><input name="remoteProxyUser" type="text" id="user" size="32" maxlength="128" value="#[remoteProxyUser]#" /></td>
    <td>&nbsp;</td>
  </tr>
    <tr valign="top">
    <td><label for="pwd">Remote proxy password</label>:</td>
    <td colspan="2"><input name="remoteProxyPwd" id="pwd" type="password" size="32" maxlength="128" value="#[remoteProxyPwd]#" /></td>
    <td>&nbsp;</td>
  </tr>  
  <tr><td colspan="4"><hr /></td></tr>
  
  <!-- no remote proxy pattern -->
  <tr valign="top">
    <td><label for="no_prxy">No-proxy adresses</label>:</td>
    <td colspan="2"><input name="remoteProxyNoProxy" type="text" id="no_prxy" size="32" maxlength="5000" value="#[remoteProxyNoProxy]#" /></td>
    <td>IP addresses for which the remote proxy should not be used</td>
  </tr>
  
  <!-- submit button -->
  <tr valign="top">
    <td colspan="4"><input type="submit" name="proxysettings" value="Submit" /> <em>Changes will take effect immediately.</em></td>
  </tr>
</table>
</fieldset>
</form><br />