<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="portForwarding">Port Forwarding (optional)</legend>
<p>You can use a remote server running a ssh demon to forward your server/proxy port.
This is useful if you want to tunnel throug a NAT/router.
Alternatively, you can simply set a virtual server port on your NAT/Server to enable connections from outside.</p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Enable&nbsp;port&nbsp;forwarding:</td>
    <td><input type="checkbox" name="portForwardingEnabled" align="top" #(portForwardingEnabled)#::checked#(/portForwardingEnabled)#></td>
    <td>Enabling disabling port forwarding via secure channel.</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;via&nbsp;proxy:</td>
    <td><input type="checkbox" name="portForwardingUseProxy" align="top" #(portForwardingUseProxy)#::checked#(/portForwardingUseProxy)# DISABLED></td>
    <td><!-- Specifies if the connection should be established through the <a href="#proxy">remote proxy</a>.-->
        <i>Function not available at the moment.</i></td>
  </tr>    
  <tr valign="top">
    <td>Forwarding&nbsp;port:</td>
    <td><input name="portForwardingPort" type="text" size="5" maxlength="5" value="#[portForwardingPort]#"></td>
    <td>The port on the remote server that should be forwarded via the secure channel to the local host.</td>
  </tr>  
  <tr valign="top">
    <td>Forwarding&nbsp;host:</td>
    <td><input name="portForwardingHost" type="text" size="32" maxlength="128" value="#[portForwardingHost]#"></td>
    <td>&nbsp;</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;port:</td>
    <td><input name="portForwardingHostPort" type="text" size="5" maxlength="5" value="#[portForwardingHostPort]#"></td>
    <td>&nbsp;</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;user:</td>
    <td><input name="portForwardingHostUser" type="text" size="32" maxlength="128" value="#[portForwardingHostUser]#"></td>
    <td>&nbsp;</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;password:</td>
    <td><input name="portForwardingHostPwd" type="password" size="32" maxlength="128" value="#[portForwardingHostPwd]#"></td>
    <td>&nbsp;</td>
  </tr> 
  <tr valign="top"> 
    <td colspan="3"><input type="submit" name="portForwarding" value="Submit">&nbsp;<i>Changes will take effect immediately.</i></td>
  </tr>  
</table>
</fieldset>
</form><br>