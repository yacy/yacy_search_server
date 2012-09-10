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
        $('#bm_tags').importTags('');
		$("#bm_url").removeAttr("disabled");
		$("#bm_url").blur(function() { 
			var url = $("#bm_url").getValue();
			$.ajax({
				type: "GET",
				url: "/api/ymarks/get_metadata.xml?url="+url,			
				dataType: "xml",
				success: function(xml) {					
					var title = "";
					var desc = "";
					var tags = "";
					if ($(xml).find('info').attr('status') === "error") {
						$("#bmaddimg").attr("src","/yacy/ui/img-1/Smiley Star Sad.png");
					} else {
						var autotags = $(xml).find('autotags')						
						title = $(xml).find('title').text();						
						desc = $(xml).find('desc').text();						
						tags = "";
						$(autotags).find('tag').each(function(){
							tags = tags + "," + $(this).attr('name');
						});
						$("#bmaddimg").attr("src","/yacy/ui/img-1/Smiley Star.png");						
					}
					$("#bm_title").setValue(title);
					$("#bm_desc").setValue(desc);
					/* $("#bm_tags").setValue(tags); */
			        $('#bm_tags').importTags(tags);
				}					
			});
		});						
		$("#ymarks_add_dialog").dialog('open');
	} else if (com=='Edit') {
		if ($('.trSelected',grid).length > 1) {
			alert("Editing of more than one selected bookmark is currently not supportet!");
			return false;
		}
		$("#bm_url").attr("disabled","disabled"); 
		$("#bm_url").setValue($('.trSelected',grid).find('.url').text());
        $("#bm_title").setValue($('.trSelected',grid).find('h3.linktitle').text().trim());
        $("#bm_desc").setValue($('.trSelected',grid).find('p.desc').text().trim());            		
        $('#bm_tags').importTags($('.trSelected',grid).find('p.tags').text().trim().replace(/,\s/g,","));
        /* $("#bm_tags").setValue($('.trSelected',grid).find('p.tags').text().trim().replace(/,\s/g,",")); */                 
        $("#bm_path").setValue($('.trSelected',grid).find('p.folders').text().replace(/, \s/g,","));
        $("#bm_public").setValue($('.trSelected',grid).find('img').attr('alt'));
        $("#ymarks_add_dialog").dialog('open');
	} else if (com=='Crawl') {
		if ($('.trSelected',grid).length == 1 && $(this).find('.apicall_pk').text() == "") {
			var pk = $(this).find('.apicall_pk').text();
			$("input[name='crawlingURL']").setValue($('.trSelected',grid).find('.url').text());
			$("#ymarks_crawlstart").dialog('open');
		} else {			
			var param = [];
			var i = 0;
			$('.trSelected',grid).each(function() {
				var pk = $(this).find('.apicall_pk').text();

				if (pk == "") {
					/*
					if (crawl_param.length == 0) {
						$('<td colspan="2">You have selected one or more bookmarks without a crawl start entry in the API table. You can define a default profile which will be used instead.</td>').appendTo("#ymarks_crawlstart_msg");
						$("input[name='crawlingURL']").attr("disabled","disabled");
						$("input[name='crawlingURL']").setValue("Default profile");
						$("#ymarks_crawlstart").dialog('open');
					}
					*/
					alert("Multiple selection currently only supports bookmarks"+"\n"+"with an existing crawl profile in the API table.");
				} else {
					var item = {name : 'item_'+i, value : "mark_"+pk};	
					param[i] = item;
					i++;
				}			
			});
			param[param.length] = { name : 'execrows', value : 'true' };
			$.ajax({
				type: "POST",
				data: param,
				url: "Table_API_p.html",			
				dataType: "html",
				success: function() {					
					$('#ymarks_flexigrid').flexReload();
				}					
			});	

		}
	} else if (com=='XBEL') {
		window.open("/api/ymarks/get_xbel.xml","_blank");
		return false;
	} else {
		alert("Sorry, the function you have requested is not yet available!");
		return false;
	}
}

function bm_dialog() {
	/* Init Tag Input */
    $('#bm_tags').tagsInput({
  	   'height':'105px',
 	   'width':'270px',
 	   'interactive':true,
 	   'removeWithBackspace' : true,
 	   'minChars' : 0,
 	   'maxChars' : 0,
 	   'placeholderColor' : '#666666'       	   
     }); 
    
	/* Initialize Bookmark Dialog */		
	$("#ymarks_add_dialog").dialog({			
		autoOpen: false,
		height: 500,
		width: 340,
		position: ['top',100],
		modal: true,			
		resizable: false,
		buttons: { 
    		OK: function() { 
				var param = [];
    			var i = 0;
    			$("#bmaddform input,#bmaddform select,#bm_desc").each(function() {
					var item = {name : $(this).attr("name"), value : $(this).attr("value")};	
					param[i] = item;
					i++;
    			});
    	        $.ajax({
    				type: "POST",
    				url: "/api/ymarks/add_ymark.xml",
    				data: param,						
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
	/* Initialize Crawl Start Dialog */		
	$("#ymarks_crawlstart").dialog({			
		autoOpen: false,
		height: 450,
		width: 470,
		position: ['top',100],
		modal: true,			
		resizable: false,
		buttons: { 
    		OK: function() { 
				var param = [];
    			var i = 0;
				$("#ymarks_crawler input[type='text'],#ymarks_crawler input:checked,#ymarks_crawler select,#ymarks_crawler input[type='hidden']").each(function() {
					var item = {name : $(this).attr("name"), value : $(this).attr("value")};	
					param[i] = item;
					i++;
				});
				$.ajax({
					type: "POST",
					data: param,
					url: "Crawler_p.html",			
					dataType: "html",
					success: function() {					
					}					
				});
				$('#ymarks_flexigrid').flexReload();
				$("#ymarks_crawlstart").dialog("close");
    		} ,
    		Cancel: function() { $("#ymarks_crawlstart").dialog("close"); }
		} 
	});
}