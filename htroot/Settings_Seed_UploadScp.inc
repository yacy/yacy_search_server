<fieldset>
<p><strong>Uploading via SCP:</strong></p>
<p>This is the account for a server where you are able to login via ssh.</p>
<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="scp_serv">Server</label>:</td>
    <td><input name="seedScpServer" type="text" id="scp_serv" size="40" value="#[seedScpServer]#" /></td>
    <td><i>The host where you have an account, like 'my.host.net'</i></td>
  </tr>
  <tr valign="top">
    <td><label for="scp_port">Server&nbsp;Port</label>:</td>
    <td><input name="seedScpServerPort" type="text" id="scp_port" size="40" value="#[seedScpServerPort]#" /></td>
    <td><i>The sshd port of the host, like '22'</i></td>
  </tr>  
  <tr valign="top">
    <td><label for="scp_path">Path</label>:</td>
    <td><input name="seedScpPath" type="text" id="scp_path" size="40" value="#[seedScpPath]#" /></td>
    <td>
      <i>The remote path on the server, like '~/yacy/seed.txt'. Missing sub-directories are NOT created automatically.</i>
    </td>
  </tr>
  <tr valign="top">
    <td><label for="scp_user">Username</label>:</td>
    <td><input name="seedScpAccount" type="text" id="scp_user" size="40" value="#[seedScpAccount]#" /></td>
    <td><i>Your log-in at the server</i></td>
  </tr>
  <tr valign="top">
    <td><label for="scp_pwd">Password</label>:</td>
    <td><input name="seedScpPassword" type="password" id="scp_pwd" size="40" value="#[seedScpPassword]#" /></td>
    <td><i>The password</i></td>
  </tr>
  <tr>
    <td colspan="3"><input type="submit" name="seedScpSettings" value="Submit" /></td>
  </tr>
</table>
</form>
</fieldset>