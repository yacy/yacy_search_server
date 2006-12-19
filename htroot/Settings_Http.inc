<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="http">HTTP Networking</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="trans_proxy">Transparent Proxy</label>:</td>
    <td><input type="checkbox" name="isTransparentProxy" id="trans_proxy" align="top" #(isTransparentProxy)#::checked="checked" #(/isTransparentProxy)#/></td>
    <td>
      With this you can specify if YaCy can be used as transparent proxy.<br />
      <i>Hint: On linux you can configure your firewall to transparently redirect all http traffic through yacy using this iptables rule:</i><br />
      <small><code>iptables -t nat -A PREROUTING -p tcp -s 192.168.0.0/16 --dport 80 -j DNAT --to 192.168.0.1:#[port]#</code></small>
    </td>
  </tr>  
  <tr valign="top">
    <td><label for="conn_keep_alive">Connection Keep-Alive</label>:</td>
    <td><input type="checkbox" name="connectionKeepAliveSupport" id="conn_keep_alive" align="top" #(connectionKeepAliveSupport)#::checked="checked" #(/connectionKeepAliveSupport)#/></td>
    <td>With this you can specify if YaCy should support the HTTP connection keep-alive feature.</td>
  </tr>  
  <tr valign="top">
    <td><label for="via_header">Send "Via" Header</label>:</td>
    <td><input type="checkbox" name="proxy.sendViaHeader" id="via_header" align="top" #(proxy.sendViaHeader)#::checked="checked" #(/proxy.sendViaHeader)#/></td>
    <td>
      Specifies if the proxy should send the <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.45">Via</a>
      http header according to RFC 2616 Sect 14.45.
    </td>
  </tr> 
   <tr valign="top">
    <td><label for="x_forwarded_header">Send "X-Forwarded-For" Header</label>:</td>
    <td><input type="checkbox" name="proxy.sendXForwardedForHeader" id="x_forwarded_header" align="top" #(proxy.sendXForwardedForHeader)#::checked="checked" #(/proxy.sendXForwardedForHeader)#/></td>
    <td>Specifies if the proxy should send the X-Forwarded-For http header.</td>
  </tr> 
  <tr valign="top">
    <td colspan="3"><input type="submit" name="httpNetworking" value="Submit"> <i>Changes will take effect immediately.</i></td>
  </tr>  
</table>
</fieldset>
</form><br />