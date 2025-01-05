<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
<fieldset><legend id="proxy">Remote Proxy (optional)</legend>
<p>YaCy can use another proxy to connect to the internet. You can enter the address for the remote proxy here:</p>

<table border="0" cellspacing="5">
  <!-- enabling/disabling remote proxy usage -->
  <tr valign="top">
    <td><label for="rem_prxy">Use remote proxy</label>:</td>
    <td><input type="checkbox" name="remoteProxyUse" id="rem_prxy"#(remoteProxyUseChecked)#:: checked="checked"#(/remoteProxyUseChecked)# /></td>
    <td>Enables the usage of the remote proxy by yacy</td>
  </tr>
  <tr valign="top">
    <td><label for="prxy4ssl">Use remote proxy for HTTPS</label></td>
    <td><input type="checkbox" name="remoteProxyUse4SSL" id="prxy4ssl"#(remoteProxyUse4SSL)#:: checked="checked"#(/remoteProxyUse4SSL)# /></td>
    <td>Specifies if YaCy should forward ssl connections to the remote proxy.</td>  
  </tr>  
  <tr><td colspan="3"><hr /></td></tr>
  
  <!-- remote proxy hostname + port -->
  <tr valign="top">
    <td><label for="host">Remote proxy host</label>:</td>
    <td><input name="remoteProxyHost" id="host" type="text" size="32" maxlength="128" value="#[remoteProxyHost]#" /></td>
    <td>The ip address or domain name of the remote proxy</td>
  </tr>
  <tr valign="top">
    <td><label for="port">Remote proxy port</label>:</td>
    <td><input name="remoteProxyPort" id="port" type="text" size="5" maxlength="5" value="#[remoteProxyPort]#" /></td>
    <td>the port of the remote proxy</td>
  </tr>
  
  <!-- remote proxy username + pwd -->
  <tr valign="top">
    <td><label for="user">Remote proxy user</label>:</td>
    <td><input name="remoteProxyUser" type="text" id="user" size="32" maxlength="128" value="#[remoteProxyUser]#" /></td>
    <td>&nbsp;</td>
  </tr>
    <tr valign="top">
    <td><label for="pwd">Remote proxy password</label>:</td>
    <td><input name="remoteProxyPwd" id="pwd" type="password" size="32" maxlength="128" value="#[remoteProxyPwd]#" /></td>
    <td>&nbsp;</td>
  </tr>  
  <tr><td colspan="3"><hr /></td></tr>
  
  <!-- no remote proxy pattern -->
  <tr valign="top">
    <td><label for="no_prxy">No-proxy adresses</label>:</td>
    <td><input name="remoteProxyNoProxy" type="text" id="no_prxy" size="32" maxlength="5000" value="#[remoteProxyNoProxy]#" /></td>
    <td>IP addresses for which the remote proxy should not be used</td>
  </tr>
  
  <!-- submit button -->
  <tr valign="top">
    <td colspan="3"><input type="submit" name="proxysettings" class="btn btn-primary" value="Submit" /> <em>Changes will take effect immediately.</em></td>
  </tr>
</table>
</fieldset>
</form><br />