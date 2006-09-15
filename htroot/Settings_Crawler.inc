<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="admin">Crawler Settings</legend>
<table border="0" cellspacing="5" width="100%">
  <tr><td colspan="3"><p><b>Generic Crawler Settings:</b></p></td></tr>
  <tr valign="top">
    <td>Timeout:</td>
    <td><input name="crawler.clientTimeout" type="text" size="16" maxlength="16" value="#[crawler.clientTimeout]#"></td>
    <td width="100%"><i>Connection timeout in ms. <code>0</code> means unlimited.</i></td>
  </tr>
  <tr><td colspan="3"><hr></td></tr>

  <tr><td colspan="3"><p><b>HTTP Crawler Settings:</b></p></td></tr>
  <tr valign="top">
    <td>Maximum&nbsp;Filesize:</td>
    <td><input name="crawler.http.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.http.maxFileSize]#"></td>
    <td><i>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.<br>
    Please note that if the crawler uses content compression, this limit is used to check the compressed content size.</i></td>
  </tr>
  <tr><td colspan="3"><hr></td></tr>

  <tr><td colspan="3"><p><b>FTP Crawler Settings:</b></p></td></tr>
  <tr valign="top">
    <td>Maximum&nbsp;Filesize:</td>
    <td><input name="crawler.ftp.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.ftp.maxFileSize]#"></td>
    <td><i>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.</i></td>
  </tr>
  <tr><td colspan="3"><hr></td></tr>
  
  <tr valign="top">
    <td>&nbsp;</td>
    <td><input type="submit" name="crawlerSettings" value="Submit"></td>
    <td><i>Changes will take effect immediately.</i></td>
  </tr>  
</table>

</fieldset>
</form><br>