<form id="parsersettings" action="SettingsAck_p.html" method="post" enctype="multipart/form-data">
<fieldset><legend id="parser">Content Parser Settings</legend>
<p>
  With this settings you can activate or deactivate parsing of additional content-types based on their MIME-types.<br />
  For a detailed description of the various MIME-types take a look at
  <a href="http://www.iana.org/assignments/media-types/">http://www.iana.org/assignments/media-types/</a>
</p>
<table border="0" cellpadding="2" cellspacing="1">
  <tr class="TableHeader" valign="bottom">#{parserMode}#
    <td class="small" >#[name]#</td>#{/parserMode}#
    <td class="small" >Mime-Type</td>
    <td class="small" >Parser&nbsp;Usage</td>
  </tr>#{parser}#
  <tr class="TableCellDark">
    <td colspan="#[colspan]#">#[name]# V#[version]#</td>
    <td>&nbsp;</td>
    <td>#[usage]#</td>
  </tr>#{mime}#
  <tr class="TableCellLight">#{parserMode}#
    <td class="small" align="center"><input type="checkbox" name="#[optionName]#" #(status)#::checked="checked" #(/status)#/></td>#{/parserMode}#
    <td class="small">#[mimetype]#</td>
    <td class="small">&nbsp;</td>
  </tr>#{/mime}#
  #{/parser}#
  <tr class="TableCellDark">#{parserMode}#
    <td class="small" align="center">
      <input type="checkbox" name="#[name]#.allParserEnabled" onclick="javascript: ParserCheckboxes(this);" #(allParserEnabled)#::checked="checked" #(/allParserEnabled)#/>
    </td>#{/parserMode}#
	 <td>&nbsp;</td>    
    <td colspan="2" class="small">Enable all parsers</td>
  </tr>
  <tr class="TableCellDark">
    <td colspan="#[parser.colspan]#" class="small" ><input type="submit" name="parserSettings" value="Submit" /> Changes take effect immediately</td>
  </tr>
</table>
</fieldset>
</form>