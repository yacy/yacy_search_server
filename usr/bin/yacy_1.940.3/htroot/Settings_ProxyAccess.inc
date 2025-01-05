<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
<fieldset><legend id="http">Proxy Settings</legend>
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
    <td><label for="via_header">Always Fresh</label>:</td>
    <td><input type="checkbox" name="proxyAlwaysFresh" id="proxyAlwaysFresh" #(proxyAlwaysFresh)#::checked="checked" #(/proxyAlwaysFresh)#/></td>
    <td>
      If unchecked, the proxy will act using Cache Fresh / Cache Stale rules. If checked, the cache is always fresh which means
      that a page is never loaded again if it was already stored in the cache. However, if the page does not exist in the cache, it will be loaded in any case.
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
    <td colspan="3"><input type="submit" class="btn btn-primary btn-sm" name="httpNetworking" value="Submit" /></td>
  </tr>  
</table>
</fieldset>
</form>
<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
<fieldset><legend id="ProxyAccess">Proxy Access Settings</legend>
<p>
  These settings configure the access method to your own http proxy and server.
  All traffic is routed throug one single port, for both proxy and server.<br />
</p>
<table border="0" cellspacing="5">
  <tr>
    <td><a href="ConfigBasic.html">HTTP Server Port</a>:</td>
    <td><b>#[port]#</b></td>

  #(server.https)#::
    <td style="width:10%">&nbsp;</td>
    <td>HTTPS Server Port:</td>
    <td><input type="text" width="4" name="port.ssl" value="#[port.ssl]#" size="4" maxlength="6"/>&nbsp;<input class="btn btn-primary btn-xs" type="submit" value="change"/></td>
  </tr>
  #(/server.https)#
</table>
<br />
<p>
  <strong>Server Access Restrictions</strong><br />
  <br />
  You can restrict the access to this proxy/server using a two-stage security barrier:
</p>
<ul>
  <li>define an <em>access domain</em> with a list of granted client IP-numbers or with wildcards</li>
  <li>define an <em>user account</em> with an user:password - pair</li>
</ul>
<p>
  This is the account that restricts access to the proxy function.
  You probably don't want to share the proxy to the internet, so you should set the
  IP-Number Access Domain to a pattern that corresponds to you local intranet.
  The default setting should be right in most cases. If you want, you can also set a proxy account
  so that every proxy user must authenticate first, but this is rather unusual.
</p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td><label for="filter">IP-Number filter</label>:</td>
    <td colspan ="2">
      <input name="proxyfilter" id="filter" type="text" size="50" maxlength="1000" value="#[proxyfilter]#" />
    </td>
  </tr>
  <tr valign="top">
    <td><label for="accounts">Accounts</label>:</td>
    <td>
      <input name="use_proxyaccounts" id="accounts" type="checkbox" size="16" maxlength="16" value="on"#(use_proxyAccounts)#:: checked="checked"#(/use_proxyAccounts)# />
      Use <a href="ConfigAccounts_p.html">Proxy Accounts</a>
    </td>
  </tr>
  <tr valign="top">
    <td colspan="2"><input class="btn btn-primary btn-sm" type="submit" name="proxyaccount" value="Submit" /></td>
  </tr>
</table>
</fieldset>
</form><br />
