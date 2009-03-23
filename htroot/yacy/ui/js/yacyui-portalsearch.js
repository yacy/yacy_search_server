$(document).ready(function() {
	
	$.ajaxSetup({
		timeout: 10000,
		cache: false
	})

	$('<div id="ypopup" class="classic"></div>').appendTo("#yacy");	
	
	startRecord = 0;
	
	var style1 = yurl + '/yacy/ui/css/yacyui-portalsearch.css';
	var style2 = yurl + '/yacy/ui/css/themes/'+ytheme+'/ui.base.css';
	var style3 = yurl + '/yacy/ui/css/themes/'+ytheme+'/ui.theme.css';	
	
	var head = document.getElementsByTagName('head')[0];
	
	$(document.createElement('link'))
    	.attr({type:'text/css', href: style1, rel:'stylesheet', media:'screen'})
    	.appendTo(head);
    $(document.createElement('link'))
    	.attr({type:'text/css', href: style2, rel:'stylesheet', media:'screen'})
    	.appendTo(head);
    $(document.createElement('link'))
    	.attr({type:'text/css', href: style3, rel:'stylesheet', media:'screen'})
    	.appendTo(head);
    	
	var script1 = yurl + '/yacy/ui/js/jquery.query.js';
	var script2 = yurl + '/yacy/ui/js/jquery.form.js';
	var script3 = yurl + '/yacy/ui/js/jquery.field.min.js';
	var script4 = yurl + '/yacy/ui/js/jquery-faviconize-1.0.js';
	var script5 = yurl + '/yacy/ui/js/jquery.ui.all.min.js';
	
	$.getScript(script1, function(){});
	$.getScript(script2, function(){});
	$.getScript(script3, function(){});
	$.getScript(script4, function(){});
	$.getScript(script5, function(){
		$("#ypopup").dialog({			
			autoOpen: false,
			height: 500,
			width: 420,
			minWidth: 420,			
			position: ['top',50],
			modal: false,			
			resizable: true,
		  	title: "YaCy P2P Web Search",
		  	buttons: {
        		Next: function() {
        			startRecord = startRecord + 10;
        			$('#ysearch').trigger('submit');        		
        		},
        		Prev: function() {
        			startRecord = startRecord - 10;
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
	
		var query = $('#yquery').getValue();			
		var url = yurl + '/yacysearch.json?callback=?'
		
		$('#ypopup').empty();
		$('#ypopup').append("<div class='yloading'><h3 class='linktitle'><em>Loading: "+yurl+"</em><br/><img src='"+yurl+"/yacy/ui/img/loading2.gif' align='absmiddle'/></h3></div>");
		
		if (!$("#ypopup").dialog('isOpen')) {			
			$("#ypopup").dialog('open');
		}					
		$("#yquery").focus();
		
		var param = [
			 { name : 'startRecord', value : startRecord }
			,{ name : 'maximumRecords', value : 10 }		
			,{ name : 'query', value : query}
		];	
		
		if (yparam) {
			for (var pi = 0; pi < yparam.length; pi++) param[param.length] = yparam[pi];
		}
	
		$.getJSON(url, param,
	        function(json, status){
				if (json[0]) data = json[0];
				else data = json;						
				
				$('#ypopup').empty();
				
				var total = data.channels[0].totalResults.replace(/[,.]/,"");  		
		   		var page = (data.channels[0].startIndex / data.channels[0].itemsPerPage) + 1;		
				var start = startRecord + 1;				
				var end = startRecord + 10;

				$("div .ybpane").remove();
				var ylogo = "<div class='ybpane'><a href='http://www.yacy.net' target='_blank'><img src='"+yurl+"/yacy/ui/img/yacy-logo.png' alt='www.yacy.net' title='www.yacy.net' /></a></div>";
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
					defaultImage: yurl + "/yacy/ui/img-2/article.png",
					className: "favicon"
				});
	        }
	    );
		return false;
	});
});