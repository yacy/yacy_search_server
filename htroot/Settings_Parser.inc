<form id="parsersettings" action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="parser">Content Parser Settings</legend>
<p>
  With this settings you can activate or deactivate parsing of additional content-types based on their MIME-types.<br />
  For a detailed description of the various MIME-types take a look at
  <a href="http://www.iana.org/assignments/media-types/">http://www.iana.org/assignments/media-types/</a>
</p>
<table border="0" cellpadding="2" cellspacing="1">
  <tr class="TableHeader" valign="bottom">
    <td class="small" >enable/disable Parser</td>
    <td class="small" >Mime-Type</td>
  </tr>#{parser}#
  <tr class="TableCellDark">
    <td colspan="2">#[name]#</td>
  </tr>#{mime}#
  <tr class="TableCellLight">
    <td class="small" align="center"><input type="checkbox" name="mimename_#[mimetype]#" #(status)#::checked="checked" #(/status)#/></td>
    <td class="small">#[mimetype]#</td>
  </tr>#{/mime}#
  #{/parser}#
  <tr class="TableCellDark">
    <td class="small" align="center">
      <input type="checkbox" name="#[name]#.allParserEnabled" onclick="javascript: ParserCheckboxes(this);" #(allParserEnabled)#::checked="checked" #(/allParserEnabled)#/>
    </td>
    <td colspan="2" class="small">Enable all parsers</td>
  </tr>
  <tr class="TableCellDark">
    <td colspan="2" class="small" ><input type="submit" name="parserSettings" value="Submit" /> Changes take effect immediately</td>
  </tr>
</table>
</fieldset>
</form>