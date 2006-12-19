<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="portForwarding">Port Forwarding (optional)</legend>
<p>
  You can use a remote server running a ssh demon to forward your server/proxy port.
  This is useful if you want to tunnel throug a NAT/router.
  Alternatively, you can simply set a virtual server port on your NAT/Server to enable connections from outside.
</p>
#(portForwarding.Available)#<p><b><font color="red">You need to install libx to use this feature</font></b></p>::#(/portForwarding.Available)#
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="pfwd">Enable port forwarding</label>:</td>
    <td><input type="checkbox" name="portForwarding.Enabled" id="pfwd" align="top" #(portForwarding.Enabled)#::checked="checked" #(/portForwarding.Enabled)##(portForwarding.Available)#disabled="disabled" ::#(/portForwarding.Available)#/></td>
    <td>Enabling / disabling port forwarding</td>
  </tr>
  <tr valign="top">
    <td><label for="sel_pfwd">Forwarding method</label>:</td>
    <td><select name="portForwarding.Type" id="sel_pfwd" size="1">
    	#{forwardingMethods}#
			<option value="#[name]#" #(selected)#::selected="selected"#(/selected)#>#[name]#</option>
		#{/forwardingMethods}#
		</select>
    </td>
    <td>Select the port forwarding method to use.</td>
  </tr>
  <tr><td colspan="3"><hr /></td></tr>
  <tr><td colspan="3"><b>Special settings for port forwarding using secure channel</b></td></tr>
  <tr valign="top">
    <td><label for="pfwd_proxy">Forwarding via proxy</label>:</td>
    <td><input type="checkbox" name="portForwarding.sch.UseProxy" id="pfwd_proxy" align="top" #(portForwarding.sch.UseProxy)#::checked="checked" #(/portForwarding.sch.UseProxy)#disabled="disabled" /></td>
    <td>
      <!-- Specifies if the connection should be established through the <a href="#proxy">remote proxy</a>.-->
      <i>Function not available at the moment.</i>
    </td>
  </tr>    
  <tr valign="top">
    <td><label for="pfwd_port">Forwarding port</label>:</td>
    <td><input name="portForwarding.sch.Port" id="pfwd_port" type="text" size="5" maxlength="5" value="#[portForwarding.sch.Port]#" /></td>
    <td>The tcp/ip port on the remote server that should be forwarded via the secure channel to the local host.</td>
  </tr>  
  <tr valign="top">
    <td><label for="pfwd_host">Forwarding host</label>:</td>
    <td><input name="portForwarding.sch.Host" id="pfwd_host" type="text" size="32" maxlength="128" value="#[portForwarding.sch.Host]#" /></td>
    <td>IP address or name of the remote server.</td>
  </tr>
  <tr valign="top">
    <td><label for="pfwd_host_port">Forwarding host port</label>:</td>
    <td><input name="portForwarding.sch.HostPort" id="pfwd_host_port" type="text" size="5" maxlength="5" value="#[portForwarding.sch.HostPort]#" /></td>
    <td>SSH port of the remote server.</td>
  </tr>
  <tr valign="top">
    <td><label for="pfwd_host_user">Forwarding host user</label>:</td>
    <td><input name="portForwarding.sch.HostUser" id="pfwd_host_user" type="text" size="32" maxlength="128" value="#[portForwarding.sch.HostUser]#" /></td>
    <td>Username to login on the remote server.</td>
  </tr>
  <tr valign="top">
    <td><label for="pfwd_host_pwd">Forwarding host password</label>:</td>
    <td><input name="portForwarding.sch.HostPwd" id="pfwd_host_pwd" type="password" size="32" maxlength="128" value="#[portForwarding.sch.HostPwd]#" /></td>
    <td>Password to login on the remote server.</td>
  </tr> 
  <tr><td colspan="3"><hr></td></tr>
  <tr valign="top"> 
    <td colspan="3"><input type="submit" name="portForwarding" value="Submit"#(portForwarding.Available)# disabled="disabled"::#(/portForwarding.Available)# /> <i>Changes will take effect immediately.</i></td>
  </tr>  
</table>
</fieldset>
</form><br />