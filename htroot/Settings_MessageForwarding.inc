<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="messageForwarding">Message Forwarding</legend>
<p>With this settings you can activate or deactivate forwarding of yacy-messages via email.
<p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Enable message forwarding:</td>
    <td><input type="checkbox" name="msgForwardingEnabled" align="top" #(msgForwardingEnabled)#::checked#(/msgForwardingEnabled)#></td>
    <td><i>Enabling/Disabling message forwarding via email.</td>
  </tr>
  <tr valign="top">
    <td>Forwarding Command:</td>
    <td><input name="msgForwardingCmd" type="text" size="32" value="#[msgForwardingCmd]#"></td>
    <td><i>The command-line program that should be used to forward the message.<br>e.g.:</i>&nbsp;<code>/usr/sbin/sendmail</code></td>
  </tr>
  <tr valign="top">
    <td>Forwarding To:</td>
    <td><input name="msgForwardingTo" type="text" size="32" maxlength="128" value="#[msgForwardingTo]#"></td>
    <td><i>The recipient email-address.<br> e.g.:</i>&nbsp;<code>root@localhost</code></td>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="msgForwarding" value="submit">&nbsp;<i>Changes will take effect immediately.</i></td>
  </tr>    
  
</table>
</fieldset>
</form><br>