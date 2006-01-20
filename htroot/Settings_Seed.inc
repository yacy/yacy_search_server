<p>
<fieldset>
<legend id="seed">Seed Upload Settings</legend>
<p>With these settings you can configure if you have an account on a public accessible 
server where you can host a seed-list file.</p>
<p><b>General Settings:</b></p>
If you enable one of the available uploading methods, you will become a principal peer.
Your peer will then upload the seed-bootstrap information periodically,
but only if there have been changes to the seed-list.
</p>
<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Upload Method:</td>
    <td><select name="seedUploadMethod" size="1">
#{seedUploadMethods}#
		<option value="#[name]#" #(selected)#::selected#(/selected)#>#[name]#</option>
#{/seedUploadMethods}#
		</select>
		<input type="submit" name="seedUploadRetry" value="Retry Uploading"></td>
    <td><i>Here you can specify which upload method should be used. Select 'none' to deactivate uploading.</i></td>
  </tr>
  <tr valign="top">
    <td>URL:</td>
    <td><input name="seedURL" type="text" size="40" value="#[seedURL]#"></td>
    <td><i>The URL that can be used to retrieve the uploaded seed file, like
           http://www.&lt;my-host&gt;.net/yacy/seed.txt'</i></td>
  </tr>  
  <tr>
    <td colspan="3"><input type="submit" name="seedSettings" value="submit"></td>
  </tr>  
</table>
</form>

#{seedUploadMethods}#
#%[file]%#		
#{/seedUploadMethods}#
</fieldset>