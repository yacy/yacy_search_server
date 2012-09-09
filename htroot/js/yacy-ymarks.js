HTMLenc = function(s) {
	return $('<div/>').text(s).html();			
}
$(document).ready(function() {							

	height=document.documentElement.clientHeight - 200;    			
	qtag = "";
	
	/* Initialize Bookmark Dialog */
	bm_dialog();
		
	/* Initialize Flexigrid */
	$('#ymarks_flexigrid').flexigrid({
 		url: '/api/ymarks/get_ymark.json',
		dataType: 'json',
 		method: 'GET',
 		colModel: [	
			{display: 'Hash', name : 'hash', width : 85, sortable : false, align: 'left', hide: true},
			{display: 'Public', name : 'public', width : 20, sortable : true, align: 'center'},
			{display: 'Crawl start', name : 'crawl_start', width : 20, sortable : false, align: 'center'},
			{display: 'Title', name : 'title', width : 400, sortable : true, align: 'left'},
			{display: 'Tags', name : 'tags', width : 160, sortable : false, align: 'left'},
			{display: 'Folders', name : 'folders', width : 160, sortable : true, align: 'left'},
			{display: 'Date added', name : 'date_added', width : 100, sortable : true, align: 'left'},
			{display: 'Date modified', name : 'date_modified', width : 100, sortable : true, align: 'left'},
			{display: 'Date visited', name : 'date_visited', width : 100, sortable : true, align: 'left', hide: true},			
			{display: 'API PK', name : 'apicall_pk', width : 85, sortable : true, align: 'left', hide: true},
			{display: 'Date recording', name : 'date_recording', width : 100, sortable : false, align: 'left', hide: true},
			{display: 'Date next exec', name : 'date_next_exec', width : 100, sortable : false, align: 'left', hide: true},
			{display: 'Date last exec', name : 'date_last_exec', width : 100, sortable : false, align: 'left', hide: true}
		],
		buttons: [				
			{name: '...', bclass: 'refresh', onpress: function() {
				$('#ymarks_flexigrid').flexOptions({
					sortname: "title",
					sortorder: "asc",	
					query: ".*",
				 	qtype: "title"								
				});
				$('#ymarks_flexigrid').flexReload();
				loadTreeView();
				
			}},
			{separator: true},
			{name: 'Add', bclass: 'bookmark', onpress: bm_action},
			{name: 'Edit', bclass: 'edit', onpress: bm_action},
			{name: 'Delete', bclass: 'delete', onpress: bm_action},
			{separator: true},
			{name: 'Crawl', bclass: 'crawl', onpress: bm_action},
			{name: 'Schedule', bclass: 'calendar', onpress: bm_action},
			{separator: true},
			{name: 'XBEL', bclass: 'xml', onpress: bm_action},
			{separator: true},
			{name: 'Help', bclass: 'help', onpress: bm_action}
		],
		searchitems : [
			{display: 'Full text (regexp)', name : ''},
			{display: 'Tags (comma seperated)', name : '_tags'},
			{display: 'Tags (regexp)', name : 'tags'},
			{display: 'Folders (comma seperated)', name : '_folder'},
			{display: 'Folders (regexp)', name : 'folders'},
			{display: 'Title (regexp)', name : 'title'},
			{display: 'Description (regexp)', name : 'desc'}
		],													
		useRp: true,
		rp: 15,
		sortname: "title",
		sortorder: "asc",
		usepager: true,					
		striped: true,
		nowrap: false,			 									    				
 		height: height,
 		query: ".*",
 		qtype: "title"									    				
	});
	
	/* Initialize Sidebar */
	$('#ymarks_sidebar').height(height+90);
	$tabs = $('#ymarks_sidebar').tabs({
		// tabs options
	});

	$tabs.bind('tabsselect', function(event, ui) {
		/* 
	    Objects available in the function context:
		    ui.tab     - anchor element of the selected (clicked) tab
		    ui.panel   - element, that contains the selected/clicked tab contents
		    ui.index   - zero-based index of the selected (clicked) tab
	    */
    	tabid = "#"+ui.panel.id;
		if (tabid == "#ymarks_tags_tab") {
			loadTagCloud();
		}
		return true;
	});

	loadTreeView();
	
	$('input[name=importer]').change(function() {
	     if ($("input[name=importer]:checked").val() == 'crawls') {
		    $("input[name='root']").setValue("/Crawl Start");
	    	$("input[name='bmkfile']").attr("disabled","disabled");
	    	$("input[name='root']").attr("disabled","disabled");
	     } else if ($("input[name=importer]:checked").val() == 'bmks') {             
		    	$("input[name='bmkfile']").attr("disabled","disabled");
	     } else if ($("input[name=importer]:checked").val() == 'dmoz') {             
		    	$("input[name='bmkfile']").attr("disabled","disabled");
		    	$("input[name='root']").setValue("/DMOZ");
		    	$("input[name='source']").removeAttr("disabled");
		    	$("input[name='source']").setValue("Top/");
		    	alert("The DMOZ RDF dump is exspected on your YaCy peer at DATA/WORK/content.rdf.u8.gz" +
		    			"\nYou can download the file from http://rdf.dmoz.org/rdf/content.rdf.u8.gz (ca. 320 MB)." +
		    			"\n\nPlease check http://www.dmoz.org/license.html before you import any DMOZ data into YaCy!" +
		    			"\n\nDue to the large number of links contained in the dmoz file it is recommended" +
		    			"\nto limit the import volume with an appropriate value for the source folder (e.g. Top/Games).")
	     } else {
	    	 $("input[name='bmkfile']").removeAttr("disabled");
	    	 $("input[name='root']").removeAttr("disabled");
	     	 $("input[name='root']").setValue("/Imported Bookmarks");
	     	 $("input[name='source']").attr("disabled","disabled");
	     	 $("input[name='source']").setValue("");
	     }
	  });

	$("#tag_include").multiselect({
		noneSelectedText: "Select (multiple) tags ...",
		height: height-50,
		minWidth: 200,
		maxWidth: 200,
		selectedList: 4,
		header: "",
		click: function(event, ui) {
			if(ui.checked) {						
				qtag = qtag + "," + ui.value;
			}
		},
		close: function() {
			$('#ymarks_flexigrid').flexOptions({
				query: qtag,
				qtype: "_tags",
				newp: 1
			});
			$('#ymarks_flexigrid').flexReload();
		},
		beforeopen: function() {
			loadTags("#tag_include", "alpha", "");
		},
		open: function() {
			qtag = "";
		}
	}).multiselectfilter();	
	
	$("#tag_select").multiselect({
		noneSelectedText: "Select tags to remove ...",
		minWidth: 200,
		maxWidth: 200,
		header: "",
		selectedList: 4,
		height: height - 540,
		beforeopen: function() {
			loadTags("#tag_select", "alpha", "");
		}
	}).multiselectfilter();

	$("#ymarks_qtype").multiselect({
		noneSelectedText: "Select query type ...",
		minWidth: 200,
		maxWidth: 200,
		header: "",
		multiple: false,
		selectedList: 1
	});
	
	$("#ymarks_importer").multiselect({
		noneSelectedText: "Select an Importer ...",
		minWidth: 200,
		maxWidth: 200,
		header: "",
		multiple: false,
		selectedList: 1
	});
	
	$("#ymarks_autotag").multiselect({
		noneSelectedText: "Select an option ...",
		minWidth: 200,
		maxWidth: 200,
		header: "",
		multiple: false,
		selectedList: 1
	});
	
	$("#ymarks_indexing").multiselect({
	   position: {
		      my: 'left bottom',
		      at: 'left top'
		   },
		noneSelectedText: "Select an option ...",
		minWidth: 200,
		maxWidth: 200,
		header: "",
		multiple: false,
		selectedList: 1
	});
	
	$('#ymarks_tagmanager').submit(function() {
		var param = [];
		$('#ymarks_tagmanager input[type="text"],#ymarks_tagmanager input[type="radio"]:checked').each(function(i){
			param[i] = { name : $(this).attr('name'), value : $(this).attr('value') };
		});		
		var tags = "";
		var ta = $("#tag_select").val();
		var i = 0;
		if(ta !== null) {			
			while (i<ta.length) {
				tags = tags + ta[i] + ",";
				i++;
			}
		}
		param[param.length] = { name : 'tags', value : tags };
		$.ajax({
			type: "POST",
			url: "/api/ymarks/manage_tags.xml",
			data: param,
			dataType: "xml",
			cache: false,
			success: function(xml) {			
				/*
				$(xml).find('status').each(function(){					
					var code = $(this).attr('code');								
					alert("Request returned status: "+code);
				}); //close each(
				*/
				loadTagCloud()
				loadTags("#tag_select", "alpha", "");
				loadTags("#tag_include", "alpha", "");
				/*
				$('#ymarks_flexigrid').flexOptions({
					sortname: "title",
					sortorder: "asc",	
					query: query,
				 	qtype: "title"								
				});
				*/
				$('#ymarks_flexigrid').flexReload();
			}
		}); //close $.ajax(			
		return false;
	});
	
});

function loadTags(select, sortorder, tags) {
	$(select).empty();	
	$.ajax({
		type: "GET",
		url: "/api/ymarks/get_tags.xml?sort="+sortorder+"&tag="+tags,			
		dataType: "xml",
		cache: false,
		success: function(xml) {			
			$(xml).find('tag').each(function(){					
				var count = $(this).attr('count');
				var tag = $(this).attr('tag');									
				$('<option value="'+tag+'">'+HTMLenc(tag)+' ['+count+']</option>').appendTo(select);
			}); //close each(			
			$(select).multiselect('refresh');
		}
	}); //close $.ajax(
}

function loadTagCloud() {		
	$("#ymarks_tagcloud *").remove();
	$.ajax({
		type: "GET",
		url: "/api/ymarks/get_tags.xml?top=25&sort=alpha",			
		dataType: "xml",
		cache: false,
		success: function(xml) {			
			$(xml).find('tag').each(function(){					
				var count = $(this).attr('count');
				var tag = $(this).attr('tag');										
				var size = ((count/20)+0.15);
				if (size < 1) {size = 1;}					
				$('<a style="font-size:'+size+'em"></a>')
					.html(HTMLenc(tag)+' ')						
					.appendTo('#ymarks_tagcloud')
					.bind('click', function() {
						var qtag = $(this).text().replace(/\s+$/g,"");								
						$('#ymarks_flexigrid').flexOptions({
							query: qtag,
							qtype: "_tags",
							newp: 1
						});
						$('#ymarks_flexigrid').flexReload();					
					});																									
			}); //close each(							
		}
	}); //close $.ajax(
};

function loadTreeView() {
	$("#ymarks_treeview").empty();	
	$("#ymarks_treeview").treeview({
		url: "/api/ymarks/get_treeview.json?bmtype=href",
		unique: false,
		persist: "location"
	});

	$("#ymarks_treeview").bind("click", function(event) {
		if ($(event.target).is("li") || $(event.target).parents("li").length) {
			var folder = $(event.target).parents("li").filter(":first").attr("id");
			$('#ymarks_flexigrid').flexOptions({
				query: folder,
				qtype: "_folder",
				newp: 1
			});
			$('#ymarks_flexigrid').flexReload();
		}
		return false;
	});
	return false;
}
