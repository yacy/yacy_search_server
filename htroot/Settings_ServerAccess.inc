<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="ServerAccess">Server Access Settings</legend>
<p>Here you can restrict access to the server. By default, the access is not limited, because this function is needed to spawn the p2p index-sharing function. If you block access to your server (setting anything else than '*'), then you will also be blocked from using other peers' indexes for search service. However, blocking access may be correct in enterprise environments where you only want to index your company's own web pages.</p>
<p>ATTENTION: Your current IP is recognized as "#[clientIP]#". If the value you enter here does not match with this IP, you will not be able to access the server pages anymore</p>
<p>
<table border="0" cellspacing="5">
<tr valign="top">
  <td>IP-Number filter:</td>
    <td colspan ="2"><input name="serverfilter" type="text" size="50" maxlength="1000" value="#[serverfilter]#"></td>
  </tr>
  <!--
  <tr valign="top">
    <td>Account Name:</td><td><input name="serveruser" type="text" size="16" maxlength="16" value="#[serveruser]#"></td>
  </tr>
  -->
  <tr valign="top">
    <td valign="bottom" rowspan="3"><input type="submit" name="serveraccount" value="Submit"></td>
  </tr>
<!--
<tr valign="top"><td>Password:</td><td><input name="serverpw1" type="password" size="16" maxlength="16" value=""></td></tr>
<tr valign="top"><td>Password (repeat same as above):</td><td><input name="serverpw2" type="password" size="16" maxlength="16" value=""></td></tr>
-->
</table>
</fieldset>
</form><br>