$(document).ready(function() {
	
	$.ajaxSetup({
		timeout: 10000,
		cache: false
	})
	
	// apply default properties
	yconf = $.extend({
		url : 'is a mandatory property - no default',
		theme : 'start',
		title : 'YaCy P2P Web Search',
		width : 420,
		height : 500,
		position: ['top',50],
		modal: false,			
		resizable: true	
	}, yconf);
	
	$('<div id="ypopup" class="classic"></div>').appendTo("#yacy");	
	
	var style1 = yconf.url + '/yacy/ui/css/yacyui-portalsearch.css';
	var style2 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.all.css';
	
	var head = document.getElementsByTagName('head')[0];
	
	$(document.createElement('link'))
    	.attr({type:'text/css', href: style1, rel:'stylesheet', media:'screen'})
    	.appendTo(head);
    $(document.createElement('link'))
    	.attr({type:'text/css', href: style2, rel:'stylesheet', media:'screen'})
    	.appendTo(head);
    	
	var script1 = yconf.url + '/yacy/ui/js/jquery.query.js';
	var script2 = yconf.url + '/yacy/ui/js/jquery.form.js';
	var script3 = yconf.url + '/yacy/ui/js/jquery.field.min.js';
	var script4 = yconf.url + '/yacy/ui/js/jquery-faviconize-1.0.js';
	var script5 = yconf.url + '/yacy/ui/js/jquery.ui.all.min.js';
	
	$.getScript(script1, function(){});
	$.getScript(script2, function(){});
	$.getScript(script3, function(){});
	$.getScript(script4, function(){});
	
	$.getScript(script5, function(){
		startRecord = 0;
		maximumRecords = parseInt($("#ysearch input[name='maximumRecords']").getValue());
		
		$("#ypopup").dialog({			
			autoOpen: false,
			height: yconf.height,
			width: yconf.width,
			minWidth: yconf.width,			
			position: yconf.position,
			modal: yconf.modal,			
			resizable: yconf.resizable,
		  	title: yconf.title,
		  	buttons: {
        		Next: function() {
        			startRecord = startRecord + maximumRecords;
        			$('#ysearch').trigger('submit');        		
        		},
        		Prev: function() {
        			startRecord = startRecord - maximumRecords;
        			if(startRecord < 0) startRecord = 0;
        			$('#ysearch').trigger('submit');        		
        		}
    		}  
		});	
	});	
	
	$('#ysearch').keyup(function() {
		startRecord = 0;
		$('#ysearch').trigger('submit');
		return false;		
	});

	$('#ysearch').submit(function() {				
	
		var url = yconf.url + '/yacysearch.json?callback=?'
		
		$('#ypopup').empty();
		$('#ypopup').append("<div class='yloading'><h3 class='linktitle'><em>Loading: "+yconf.url+"</em><br/><img src='"+yconf.url+"/yacy/ui/img/loading2.gif' align='absmiddle'/></h3></div>");
		
		if (!$("#ypopup").dialog('isOpen')) {			
			$("#ypopup").dialog('open');
		}					
		$("#yquery").focus();
				
		var param = [];		
		$("#ysearch input").each(function(i){
			var item = { name : $(this).attr('name'), value : $(this).attr('value') };		
			param[i] = item;
		});	
	
		$.getJSON(url, param,
	        function(json, status){
				if (json[0]) data = json[0];
				else data = json;						
				
				$('#ypopup').empty();
				
				var total = data.channels[0].totalResults.replace(/[,.]/,"");  		
		   		var page = (data.channels[0].startIndex / data.channels[0].itemsPerPage) + 1;		
				var start = startRecord + 1;				
				var end = startRecord + maximumRecords;

				$("div .ybpane").remove();
				var ylogo = "<div class='ybpane'><a href='http://www.yacy.net' target='_blank'><img src='"+yconf.url+"/yacy/ui/img/yacy-logo.png' alt='www.yacy.net' title='www.yacy.net' /></a></div>";
				var yresult = "<div class='ybpane'><em>Displaying result "+start+" to "+end+"<br/> of "+total+" total results.</em></div>";				
				$("div .ui-dialog-buttonpane").prepend(ylogo+yresult);

			   	$.each (
					data.channels[0].items,
					function(i,item) {
						if (item) {
							var title = "<h3 class='linktitle'><a href='"+item.link+"' target='_blank'>"+item.title+"</a></h3>";
							var url = "<p class='url'><a href='"+item.link+"' target='_blank'>"+item.link+"</a></p>"
							var desc = "<p class='desc'>"+item.description+"</p>";
							var date = "<p class='date'>"+item.pubDate.substring(0,16);
							var size = " | "+item.sizename+"</p>";
							$(title+desc+url+date+size).appendTo("#ypopup");	
						}								
					}
				);
				$(".linktitle a").faviconize({
					position: "before",
					defaultImage: yconf.url + "/yacy/ui/img-2/article.png",
					className: "favicon"
				});
	        }
	    );
		return false;
	});
});