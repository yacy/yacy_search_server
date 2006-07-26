<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="portForwarding">Port Forwarding (optional)</legend>
<p>You can use a remote server running a ssh demon to forward your server/proxy port.
This is useful if you want to tunnel throug a NAT/router.
Alternatively, you can simply set a virtual server port on your NAT/Server to enable connections from outside.</p>
#(portForwarding.Available)#<p><b><font color="red">You need to install libx to use this feature</font></b></p>::#(/portForwarding.Available)#
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Enable&nbsp;port&nbsp;forwarding:</td>
    <td><input type="checkbox" name="portForwarding.Enabled" align="top" #(portForwarding.Enabled)#::checked#(/portForwarding.Enabled)##(portForwarding.Available)# disabled="disabled"::#(/portForwarding.Available)#></td>
    <td>Enabling disabling port forwarding</td>
  </tr>
  <tr valign="top">
    <td>Forwarding method:</td>
    <td><select name="portForwarding.Type" size="1">
    	#{forwardingMethods}#
			<option value="#[name]#" #(selected)#::selected#(/selected)#>#[name]#</option>
		#{/forwardingMethods}#
		</select>    
    </td>
    <td>Select the port forwarding method to use.</td>
  </tr>  
  
  <tr><td colspan="3"><hr></td></tr>
  <tr><td colspan="3"><b>Special settings for port forwarding using secure channel</b></td></tr>
  <tr valign="top">
    <td>Forwarding&nbsp;via&nbsp;proxy:</td>
    <td><input type="checkbox" name="portForwarding.sch.UseProxy" align="top" #(portForwarding.sch.UseProxy)#::checked#(/portForwarding.sch.UseProxy)# DISABLED></td>
    <td><!-- Specifies if the connection should be established through the <a href="#proxy">remote proxy</a>.-->
        <i>Function not available at the moment.</i></td>
  </tr>    
  <tr valign="top">
    <td>Forwarding&nbsp;port:</td>
    <td><input name="portForwarding.sch.Port" type="text" size="5" maxlength="5" value="#[portForwarding.sch.Port]#"></td>
    <td>The tcp/ip port on the remote server that should be forwarded via the secure channel to the local host.</td>
  </tr>  
  <tr valign="top">
    <td>Forwarding&nbsp;host:</td>
    <td><input name="portForwarding.sch.Host" type="text" size="32" maxlength="128" value="#[portForwarding.sch.Host]#"></td>
    <td>ip address or name of the remote server</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;port:</td>
    <td><input name="portForwarding.sch.HostPort" type="text" size="5" maxlength="5" value="#[portForwarding.sch.HostPort]#"></td>
    <td>ssh port of the remote server</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;user:</td>
    <td><input name="portForwarding.sch.HostUser" type="text" size="32" maxlength="128" value="#[portForwarding.sch.HostUser]#"></td>
    <td>username to login on the remote server</td>
  </tr>
  <tr valign="top">
    <td>Forwarding&nbsp;host&nbsp;password:</td>
    <td><input name="portForwarding.sch.HostPwd" type="password" size="32" maxlength="128" value="#[portForwarding.sch.HostPwd]#"></td>
    <td>password to login on the remote server</td>
  </tr> 
  <tr><td colspan="3"><hr></td></tr>
  <tr valign="top"> 
    <td colspan="3"><input type="submit" name="portForwarding" value="Submit"#(portForwarding.Available)# disabled="disabled"::#(/portForwarding.Available)#>&nbsp;<i>Changes will take effect immediately.</i></td>
  </tr>  
</table>
</fieldset>
</form><br>