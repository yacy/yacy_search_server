<fieldset>
<p><strong>Store into filesystem:</strong></p>
<p>You must configure this if you want to store the seed-list file onto the file system.</p>
<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="fs_path">File Location</label>:</td>
    <td><input name="seedFilePath" type="text" id="fs_path" size="40" value="#[seedFilePath]#" /></td>
    <td><i>Here you can specify the path within the filesystem where the seed-list file should be stored.</i></td>
  </tr>  
  <tr>
    <td colspan="3"><input type="submit" name="seedFileSettings" value="Submit" /></td>
  </tr>  
</table>
</form>
</fieldset>