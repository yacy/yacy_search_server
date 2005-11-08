<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="proxy">Remote Proxy (optional)</legend>
<p>YaCy can use another proxy to connect to the internet. You can enter the address for the remote proxy here:</p>
<p><table border="0" cellspacing="5">

  <!-- enabling/disabling remote proxy usage -->
  <tr valign="top">
    <td>Use remote proxy:</td>
    <td><input type="checkbox" name="remoteProxyUse" align="top" #(remoteProxyUseChecked)#::checked#(/remoteProxyUseChecked)#></td>
    <td>&nbsp;</td>
    <td>Enables the usage of the remote proxy by yacy</td>
  </tr>
  <tr valign="top">
    <td colspan="2">Use remote proxy for yacy <-> yacy communication</td>
    <td><input type="checkbox" name="remoteProxyUse4Yacy" align="top" #(remoteProxyUse4Yacy)#::checked#(/remoteProxyUse4Yacy)#></td>
  	<td>Specifies if the remote proxy should be used for the communication of this peer to other yacy peers.<br>
  	    <i>Hint:</i> Enabling this option could cause this peer to remain in junior status.</td>  
  </tr>
  <tr valign="top">
    <td colspan="2">Use remote proxy for https</td>
    <td><input type="checkbox" name="remoteProxyUse4SSL" align="top" #(remoteProxyUse4SSL)#::checked#(/remoteProxyUse4SSL)#></td>
  	<td>Specifies if YaCy should forward ssl connections to the remote proxy.</td>  
  </tr>  
  <tr><td colspan="4"><hr></td></tr>
  
  <!-- remote proxy hostname + port -->
  <tr valign="top">
    <td>Remote proxy host:</td>
    <td colspan="2"><input name="remoteProxyHost" type="text" size="32" maxlength="128" value="#[remoteProxyHost]#"></td>
    <td>The ip address or domain name of the remote proxy</td>
  </tr>
  <tr valign="top">
    <td>Remote proxy port:</td>
    <td colspan="2"><input name="remoteProxyPort" type="text" size="5" maxlength="5" value="#[remoteProxyPort]#"></td>
    <td>the port of the remote proxy</td>
  </tr>
  
  <!-- remote proxy username + pwd -->
  <tr valign="top">
    <td>Remote proxy user:</td>
    <td colspan="2"><input name="remoteProxyUser" type="text" size="32" maxlength="128" value="#[remoteProxyUser]#"></td>
    <td>&nbsp;</td>
  </tr>
  </tr>
    <tr valign="top">
    <td>Remote proxy pwd:</td>
    <td colspan="2"><input name="remoteProxyPwd" type="password" size="32" maxlength="128" value="#[remoteProxyPwd]#"></td>
    <td>&nbsp;</td>
  </tr>  
  <tr><td colspan="4"><hr></td></tr>
  
  <!-- no remote proxy pattern -->
  <tr valign="top">
    <td>no-proxy adresses:</td>
    <td colspan="2"><input name="remoteProxyNoProxy" type="text" size="32" maxlength="128" value="#[remoteProxyNoProxy]#"></td>
    <td>IP addresses for which the remote proxy should not be used</td>
  </tr>
  
  <!-- submit button -->
  <tr valign="top">
    <td colspan="4"><input type="submit" name="proxysettings" value="submit">&nbsp;<i>Changes will take effect immediately.</i></td>
  </tr>
</table>
</fieldset>
</form><br>