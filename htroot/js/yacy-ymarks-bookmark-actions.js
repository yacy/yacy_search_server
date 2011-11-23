/* Initialize Bookmark Actions */
function bm_action(com,grid) {
	if (com=='Delete') {
		var check = confirm('Delete ' + $('.trSelected',grid).length + ' bookmark(s)?');
		if(check == true) {				
			$('.trSelected',grid).each(function(){
				var url = "/api/ymarks/delete_ymark.xml?id="+$(this).find('td :first').text();					
				$.ajax({
					type: 'POST',
					url: url,			
					dataType: 'xml',
					success: function(xml) {
						$('#ymarks_flexigrid').flexReload();
						loadTreeView();
					}
				}); // close $.ajax(					
			}); //close each(						
		}
	}
	else if (com=='Add') {			
		$('#bmaddform').resetForm();
		$("input[name='bm_url']").removeAttr("disabled");
		$("#bm_url").blur(function() { 
			var url = $("input[name='bm_url']").getValue();
			$.ajax({
				type: "GET",
				url: "/api/ymarks/get_metadata.xml?url="+url,			
				dataType: "xml",
				success: function(xml) {					
					var title = $(xml).find('title').text();
					$("input[name='bm_title']").setValue(title);
					var desc = $(xml).find('desc').text();					
					$("textarea[name='bm_desc']").setValue(desc);					
					
					var autotags = $(xml).find('autotags')
					var tags = "";
					$(autotags).find('tag').each(function(){
						tags = tags + "," + $(this).attr('name');
					});
					$("input[name='bm_tags']").setValue(tags);
				}					
			});
		});						
		$("#ymarks_add_dialog").dialog('open');
	} else if (com=='Edit') {
		if ($('.trSelected',grid).length > 1) {
			alert("Editing of more than one selected bookmark is currently not supportet!");
			return false;
		}
		$("input[name='bm_url']").attr("disabled","disabled"); 
		$("input[name='bm_url']").setValue($('.trSelected',grid).find('.url').text());
        $("input[name='bm_title']").setValue($('.trSelected',grid).find('h3.linktitle').text().trim());
        $("textarea[name='bm_desc']").setValue($('.trSelected',grid).find('p.desc').text().trim());            		
        $("input[name='bm_tags']").setValue($('.trSelected',grid).find('p.tags').text().trim().replace(/,\s/g,","));            
        $("input[name='bm_path']").setValue($('.trSelected',grid).find('p.folders').text().replace(/,\s/g,","));
        $("select[name='bm_public']").setValue($('.trSelected',grid).find('img').attr('alt'));
        $("#ymarks_add_dialog").dialog('open');
	} else if (com=='Crawl') {
		var param = [];
		var count = 0;
		var i = 0;
		var err = "";
		var succ = "";
		var msg = "";
		$('.trSelected',grid).each(function() {
			var pk = $(this).find('.apicall_pk').text();			
			if (pk == "") {
				count++;
				err = err + "\n" + $(this).find('.url').text();
			}
			succ = succ  + "\n" + $(this).find('.url').text();
			var item = {name : 'item_'+count, value : "mark_"+pk};	
			param[i] = item;
			i++;
		});
		if(i-count > 0)
			msg = msg + "Success:"+succ;
		if(count > 0)
			msg = msg + "\n\nError: No entry in API Table found:"+err;
		alert(msg);
		param[param.length] = { name : 'execrows', value : 'true' };
		$.ajax({
			type: "POST",
			data: param,
			url: "Table_API_p.html",			
			dataType: "html",
			success: function() {					
			}					
		});		
	} else if (com=='XBEL') {
		window.open("/api/ymarks/get_xbel.xml","_blank");
		return false;
	} else {
		alert("Sorry, the function you have requested is not yet available!");
		return false;
	}
}

function bm_dialog() {
	/* Initialize Bookmark Dialog */		
	$("#ymarks_add_dialog").dialog({			
		autoOpen: false,
		height: 420,
		width: 340,
		position: ['top',100],
		modal: true,			
		resizable: false,
		buttons: { 
    		OK: function() { 
    			var url = $("input[name='bm_url']").getValue();
    	        var title = $("input[name='bm_title']").getValue();
    	        var desc = $("textarea[name='bm_desc']").getValue();            		
    	        var tags = $("input[name='bm_tags']").getValue()
    	        var path = $("input[name='bm_path']").getValue();
    	        var pub = $("select[name='bm_public']").getValue();
    	        $.ajax({
    				type: "POST",
    				url: "/api/ymarks/add_ymark.xml",
    				data: "url="+url+"&title="+title+"&desc="+desc+"&tags="+tags+"&folders="+path+"&public="+pub,						
    				dataType: "xml",
    				success: function(xml) {
    	         		$('#bmaddform').resetForm();
    	         		$("#bm_url").unbind('blur');
    	         		$("#ymarks_add_dialog").dialog("close");
						$('#ymarks_flexigrid').flexReload();
						loadTreeView();
						return false;
    	   			}
    	   		});	
    		} ,
    		Cancel: function() { $("#ymarks_add_dialog").dialog("close"); }
		} 
	});
}