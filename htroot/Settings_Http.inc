<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="http">HTTP Networking</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="trans_proxy">Transparent Proxy</label>:</td>
    <td><input type="checkbox" name="isTransparentProxy" id="trans_proxy" #(isTransparentProxy)#::checked="checked" #(/isTransparentProxy)#/></td>
    <td>
      With this you can specify if YaCy can be used as transparent proxy.<br />
      <em>Hint: On linux you can configure your firewall to transparently redirect all http traffic through yacy using this iptables rule</em>:<br />
      <small><code>iptables -t nat -A PREROUTING -p tcp -s 192.168.0.0/16 --dport 80 -j DNAT --to 192.168.0.1:#[port]#</code></small>
    </td>
  </tr>  
  <tr valign="top">
    <td><label for="via_header">Send "Via" Header</label>:</td>
    <td><input type="checkbox" name="proxy.sendViaHeader" id="via_header" #(proxy.sendViaHeader)#::checked="checked" #(/proxy.sendViaHeader)#/></td>
    <td>
      Specifies if the proxy should send the <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.45" target="_blank">Via</a>
      http header according to RFC 2616 Sect 14.45.
    </td>
  </tr> 
   <tr valign="top">
    <td><label for="x_forwarded_header">Send "X-Forwarded-For" Header</label>:</td>
    <td><input type="checkbox" name="proxy.sendXForwardedForHeader" id="x_forwarded_header" #(proxy.sendXForwardedForHeader)#::checked="checked" #(/proxy.sendXForwardedForHeader)#/></td>
    <td>Specifies if the proxy should send the X-Forwarded-For http header.</td>
  </tr> 
  <tr valign="top">
    <td colspan="3"><input type="submit" class="btn-primary btn-sm" name="httpNetworking" value="Submit" /> <em>Changes will take effect immediately.</em></td>
  </tr>  
</table>
</fieldset>
</form><br />
<form action="SettingsAck_p.html">
<fieldset><legend id="httpserver">HTTP Server</legend>
<table border="0" cellspacing="5">
  <tr>
    <td>HTTP Server Version:</td>
    <td><b>#[httpservername]#</b></td>
  </tr>
  <tr>
    <td><a href="ConfigBasic.html">HTTP Server Port</a>:</td>
    <td><b>#[port]#</b></td>
  </tr>
  #(server.https)#::
  <tr>
    <td>HTTPS Port:</td>
    <td><input type="text" width="4" name="port.ssl" value="#[port.ssl]#" size="4" maxlength="6"/>&nbsp;<input class="btn-primary btn-xs" type="submit" value="change"/></td>
  </tr>
  #(/server.https)#
</table>
</fieldset>
</form><br />