<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="messageForwarding">Message Forwarding</legend>
<p>With this settings you can activate or deactivate forwarding of yacy-messages via email.</p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="msg_forwarding">Enable message forwarding</label>:</td>
    <td><input type="checkbox" name="msgForwardingEnabled" id="msg_forwarding" #(msgForwardingEnabled)#::checked="checked" #(/msgForwardingEnabled)#/></td>
    <td><i>Enabling/Disabling message forwarding via email.</i></td>
  </tr>
  <tr valign="top">
    <td><label for="fwd_cmd">Forwarding Command</label>:</td>
    <td><input name="msgForwardingCmd" id="fwd_cmd" type="text" size="32" value="#[msgForwardingCmd]#" /></td>
    <td>
      <i>The command-line program that should be used to forward the message.<br />
      e.g.:</i> <code>/usr/sbin/sendmail</code>
    </td>
  </tr>
  <tr valign="top">
    <td><label for="fwd_to">Forwarding To</label>:</td>
    <td><input name="msgForwardingTo" id="fwd_to" type="text" size="32" maxlength="128" value="#[msgForwardingTo]#" /></td>
    <td>
      <i>The recipient email-address.<br />
      e.g.:</i> <code>root@localhost</code>
    </td>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="msgForwarding" value="Submit" /> <em>Changes will take effect immediately.</em></td>
  </tr>
</table>
</fieldset>
</form><br />