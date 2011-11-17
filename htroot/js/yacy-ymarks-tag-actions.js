/* Initialize Tag Actions */
function tag_action(com,grid) {
	alert("Sorry, the function you have requested is not yet available!");
	if (com=='Add') {
		flex = grid;			
		$('#tagaddform').resetForm();
		$("#tagadd").dialog('open');
	} else 	{
		$('#tageditform').resetForm();
		$("#tagedit").dialog('open');
	}				
};