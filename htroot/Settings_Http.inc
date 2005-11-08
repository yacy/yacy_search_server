<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="http">HTTP Networking</legend>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Transparent&nbsp;Proxy:</td>
    <td><input type="checkbox" name="isTransparentProxy" align="top" #(isTransparentProxy)#::checked#(/isTransparentProxy)#></td>
    <td>With this you can specify if YaCy can be used as transparent proxy.<br>
    <i>Hint: On linux you can configure your firewall to transparently redirect all http traffic through yacy using this iptables rule:</i><br>     
			    <small><code>iptables -t nat -A PREROUTING -p tcp -s 192.168.0.0/16 --dport 80 -j DNAT --to 192.168.0.1:#[port]#</code></small>
  </td>
  </tr>  
  <tr valign="top">
    <td>Connection&nbsp;Keep-Alive:</td>
    <td><input type="checkbox" name="connectionKeepAliveSupport" align="top" #(connectionKeepAliveSupport)#::checked#(/connectionKeepAliveSupport)#></td>
    <td>With this you can specify if YaCy should support the HTTP connection keep-alive feature.</td>
  </tr>  
  <tr valign="top">
    <td colspan="3"><input type="submit" name="httpNetworking" value="submit">&nbsp;<i>Changes will take effect immediately.</i></td>
  </tr>  
</table>
</fieldset>
</form><br>