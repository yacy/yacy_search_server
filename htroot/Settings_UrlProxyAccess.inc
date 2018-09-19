
<form id="urlproxysettings" action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
  <input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
  <fieldset><legend id="urlproxy">URL Proxy Settings</legend>
    <p>
    With this settings you can activate or deactivate URL proxy.
    Service call: http://localhost:8090/proxy.html?url=parameter, where parameter is the url of an external web page.
    </p>

    <dl>
        <dt>URL proxy:</dt>
            <dd>
                <input type="checkbox" name="urlproxyenabled" id="urlproxyenabled" #(urlproxyenabled_checked)#:: checked="checked"#(/urlproxyenabled_checked)# />Enabled<br/>
                <p class="help">
                  Globally enables or disables URL proxy via http://yourpeer:yourport/proxy.html?url=http://externalurl/
                </p>
            </dd>
              
        <dt>Show search results via URL proxy:</dt>
            <dd>
                <input type="checkbox" name="urlproxyuseforresults" id="urlproxyuseforresults" #(urlproxyuseforresults_checked)#:: checked="checked"#(/urlproxyuseforresults_checked)# />Enabled<br/>
                <p class="help">
                  Enables or disables URL proxy for all search results. If enabled, all search results will be tunneled through URL proxy.
                </p>
                <p class="help">Alternatively you may add this javascript to your browser favorites/short-cuts, which will reload the current browser address 
                   via the YaCy proxy servlet.<br /> 
                   
                   <code>javascript: window.location.href = ('http://localhost:8090/proxy.html?url=' + location.href);</code>
                   or right-click this link and add to favorites: <code><a class="link" href="javascript: window.location.href = ('http://localhost:8090/proxy.html?url=' + location.href);">YaCy proxy start</a></code>
                </p>
            </dd>
              
        <dt>Restrict URL proxy use:</dt>
            <dd>
                <input type="text" name="urlproxyfilter" value="#[urlproxyfilter]#" size="60" /><br/><br/>
                <p class="help">
                  Define client filter. Default: 127.0.0.1,0:0:0:0:0:0:0:1.
                </p>
            </dd>
        <dt>URL substitution:</dt>
            <dd>
                <input type="text" name="urlproxydomains" value="#[urlproxydomains]#" size="60" /><br/><br/>
                <p class="help">
                  Define URL substitution rules which allow navigating in proxy environment. Possible values: all, domainlist. Default: domainlist.
                </p>
            </dd>
              
    </dl>
    <dl><dt></dt><dd><input type="submit" name="urlproxySettings" value="Submit" class="btn btn-primary"/></dd></dl>
  </fieldset>
</form>

