<p><fieldset>
<b>Uploading via FTP:</b></p>
<p>This is the account for a FTP server where you can host a seed-list file.
If you set this, you will become a principal peer.
Your peer will then upload the seed-bootstrap information periodically,
but only if there had been changes to the seed-list.
</p>
<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Server:</td>
    <td><input name="seedFTPServer" type="text" size="40" value="#[seedFTPServer]#"></td>
    <td><i>The host where you have a FTP account, like 'ftp.&lt;my-host&gt;.net'</i></td>
  </tr>
  <tr valign="top">
    <td>Path:</td>
    <td><input name="seedFTPPath" type="text" size="40" value="#[seedFTPPath]#"></td>
    <td><i>The remote path on the FTP server, like 'yacy/seed.txt'.
           Missing sub-directories are NOT created automatically.</i></td>
  </tr>
  <tr valign="top">
    <td>Username:</td>
    <td><input name="seedFTPAccount" type="text" size="40" value="#[seedFTPAccount]#"></td>
    <td><i>Your log-in at the FTP server</i></td>
  </tr>
  <tr valign="top">
    <td>Password:</td>
    <td><input name="seedFTPPassword" type="password" size="40" value="#[seedFTPPassword]#"></td>
    <td><i>The password</i></td>
  </tr>
  <tr>
    <td colspan="3"><input type="submit" name="seedFtpSettings" value="submit"></td>
  </tr>
</table>
</form>
</fieldset>