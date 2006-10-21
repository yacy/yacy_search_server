<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="ProxyAccess">Proxy Access Settings</legend>
These settings configure the access method to your own http proxy and server.
All traffic is routed throug one single port, for both proxy and server.<br>
<br><b>Server/Proxy Port Configuration</b><br>
<p>The socket addresses where YaCy should listen for incoming connections from other YaCy peers or http clients.<br>
You have four possibilities to specify the address:<ul>
<li>defining a port only (<i>e.g. 8080</i>)</li>
<li>defining IP address and port (<i>e.g. 192.168.0.1:8080</i>)</li>
<li>defining host name and port (<i>e.g. home:8080</i>)</li>
<li>defining interface name and port (<i>e.g. #eth0:8080</i>)</li>
</ul><br>
<i>Hint: Dont forget to change your firewall configuration after you have changed the port.</i>
</p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Proxy and http-Server Administration Port:</td>
    <td><input name="port" type="text" size="30" maxlength="30" value="#[port]#"></td>
    <td><i>Changes will take effect in 5-10 seconds</i></td>
  </tr> 
</table>

<p><b>Server Access Restrictions</b><br><br>You can restrict the access to this proxy/server using a two-stage security barrier:
<ul>
<li>define an <i>access domain</i> with a list of granted client IP-numbers or with wildcards</li>
<li>define an <i>user account</i> with an user:password - pair</li>
</ul><br>

<p>This is the account that restricts access to the proxy function. You probably don't want to share the proxy to the internet, so you should set the IP-Number Access Domain to a pattern that corresponds to you local intranet. The default setting should be right in most cases. If you want, you can also set a proxy account so that every proxy user must authenticate first, but this is rather unusual.</p>
<p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>IP-Number filter:</td>
    <td colspan ="2"><input name="proxyfilter" type="text" size="50" maxlength="1000" value="#[proxyfilter]#"></td>
  </tr>
  <tr valign="top">
    <td>Accounts:</td>
    <td><input name="use_proxyaccounts" type="checkbox" size="16" maxlength="16" value="on" #(use_proxyAccounts)#::checked#(/use_proxyAccounts)#> Use <a href="User_p.html">Proxy Accounts</a></td>
  </tr>
  <tr valign="top">
    <td colspan="2"><input type="submit" name="proxyaccount" value="Submit"></td>
  </tr>
</table>
</fieldset>
</form><br>