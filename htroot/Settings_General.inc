<p><form action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="general">General Settings</legend>
<p>
<table border="0" cellspacing="5">
  <tr valign="top">
    <td>Peer&nbsp;Name:</td>
    <td><input name="peername" type="text" size="32" maxlength="80" value="#[peerName]#"></td>
    <td><b>Your peer name defines also a new '.yacy' - domain, which can be accessed from every peer running this proxy.</b>
           Using your 'Home Page' and 'File Share' - zones you also have a platform to provide content to your new domain.<br>
    <i>(hint: Choose a name that appears on a web page that tells something about you, visit the page, get the 'senior' status, and you can be found...)</i></td>
  </tr>
  <tr valign="top">
    <td>staticIP (optional):</td>
    <td><input name="staticIP" type="text" size="32" maxlength="80" value="#[staticIP]#"></td>
    <td><b>The staticIP can help that your peer can be reached by other peers in case that your
           peer is behind a firewall or proxy.</b> You can create a tunnel through the firewall/proxy
           (look out for 'tunneling through https proxy with connect command') and create
           an access point for incoming connections.
           This access address can be set here (either as IP number or domain name).
           If the address of outgoing connections is equal to the address of incoming connections,
           you don't need to set anything here, please leave it blank.<br>
  </tr>
  <tr valign="top">
    <td colspan="3"><input type="submit" name="generalsettings" value="submit"></td>
  </tr>
</table>
</fieldset>
</form><br>