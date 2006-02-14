<STYLE type="text/css">
<!--
.hostPort { 
	color: green;
	font-weight: bold;
}
-->
</STYLE>
<hr>
<p>The server <b>#[hostName]#</b> could not be found.</p>
Did you mean:
<table>
	#{list}#
	<tr>
		<td><a class="hostPort" href="http://#[hostName]#:#[hostPort]##[hostPath]##[hostArgs]#">#[hostName]#</a></td>
	</tr>
	#{/list}#
</table>

