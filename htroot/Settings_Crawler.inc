<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="admin">Crawler Settings</legend>
<table border="0" cellspacing="5" width="100%">
  <tr><td colspan="3"><p><strong>Generic Crawler Settings</strong>:</p></td></tr>
  <tr valign="top">
    <td>Timeout:</td>
    <td><input name="crawler.clientTimeout" type="text" size="16" maxlength="16" value="#[crawler.clientTimeout]#" /></td>
    <td><em>Connection timeout in ms. <code>0</code> means unlimited.</em></td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>

  <tr><td colspan="3"><p><b>HTTP Crawler Settings:</b></p></td></tr>
  <tr valign="top">
    <td>Maximum Filesize:</td>
    <td><input name="crawler.http.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.http.maxFileSize]#" /></td>
    <td><em>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.<br />
    Please note that if the crawler uses content compression, this limit is used to check the compressed content size.</em></td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>

  <tr><td colspan="3"><p><strong>FTP Crawler Settings</strong>:</p></td></tr>
  <tr valign="top">
    <td>Maximum Filesize:</td>
    <td><input name="crawler.ftp.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.ftp.maxFileSize]#" /></td>
    <td><em>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.</em></td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>
  
  <tr><td colspan="3"><p><strong>SMB Crawler Settings</strong>:</p></td></tr>
  <tr valign="top">
    <td>Maximum Filesize:</td>
    <td><input name="crawler.smb.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.smb.maxFileSize]#" /></td>
    <td><em>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.</em></td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>
  
  <tr><td colspan="3"><p><strong>Local File Crawler Settings</strong>:</p></td></tr>
  <tr valign="top">
    <td>Maximum Filesize:</td>
    <td><input name="crawler.file.maxFileSize" type="text" size="16" maxlength="16" value="#[crawler.file.maxFileSize]#" /></td>
    <td><em>Maximum allowed file size in bytes that should be downloaded. Larger files will be skipped. <code>-1</code> means unlimited.</em></td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>
  
  <tr valign="top">
    <td>&nbsp;</td>
    <td><input type="submit" name="crawlerSettings" value="Submit" /></td>
    <td><em>Changes will take effect immediately.</em></td>
  </tr>  
</table>

</fieldset>
</form>