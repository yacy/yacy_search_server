/* Initialize Tag Actions */
function tag_action(com,grid) {
	if (com=='Add') {
		flex = grid;			
		$('#tagaddform').resetForm();
		$("#tagadd").dialog('open');
	} else 	{
		$('#tageditform').resetForm();
		$("#tagedit").dialog('open');
	}				
};