/**
 * Copyright (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 * first published 07.04.2005 on http://yacy.net
 * Licensed under the GNU GPL-v2 license
 */

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