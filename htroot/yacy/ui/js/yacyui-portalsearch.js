$(document).ready(function() {
	$.ajaxSetup({
		timeout: 15000,
		cache: true
	})	
	// apply default properties
	startRecord = 0;
	maximumRecords = 10;	
	submit = false;	
	yconf = $.extend({
		url      : 'is a mandatory property - no default',
		global   : false,		
		theme    : 'start',
		title    : 'YaCy P2P Web Search',
		logo     : yconf.url + '/yacy/ui/img/yacy-logo.png',
		link     : 'http://yacy.net',
		width    : 420,
		height   : 640,
		position : ['top',50],
		modal    : false,			
		resizable: true,
		show     : '',
		hide     : '',
		load_js	 : true,
		load_css : true	
	}, yconf);
	
	$.extend($.ui.accordion.defaults, {
		autoHeight: false,
		clearStyle: true,
		collapsible: true,
		header: "h3"
	});
	
	$('<div id="ypopup" class="classic"></div>').appendTo("#yacylivesearch");
	
	if(yconf.load_css) {	
		var style1 = yconf.url + '/yacy/ui/css/yacyui-portalsearch.css';
		var style2 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.core.css';
		var style3 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.dialog.css';
		var style4 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.theme.css';
		var style5 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.resizable.css';
		var style6 = yconf.url + '/yacy/ui/css/themes/'+yconf.theme+'/ui.accordion.css';
	
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
	    $(document.createElement('link'))
	    	.attr({type:'text/css', href: style4, rel:'stylesheet', media:'screen'})
	    	.appendTo(head);
	    $(document.createElement('link'))
	    	.attr({type:'text/css', href: style5, rel:'stylesheet', media:'screen'})
	    	.appendTo(head);
	    $(document.createElement('link'))
	    	.attr({type:'text/css', href: style6, rel:'stylesheet', media:'screen'})
	    	.appendTo(head);
	}
    
    if(yconf.load_js) {
	    var script0 = yconf.url + '/yacy/ui/js/jquery.dimensions.min.js';	
		var script1 = yconf.url + '/yacy/ui/js/jquery.query.js';
		var script2 = yconf.url + '/yacy/ui/js/jquery.form.js';
		var script3 = yconf.url + '/yacy/ui/js/jquery.field.min.js';
		var script4 = yconf.url + '/yacy/ui/js/jquery-faviconize-1.0.js';
		var script5 = yconf.url + '/yacy/ui/js/jquery-ui-1.7.2.min.js';
		
		$.getScript(script0, function(){});
		$.getScript(script1, function(){});
		$.getScript(script2, function(){});
		$.getScript(script3, function(){});
		$.getScript(script4, function(){});
		$.getScript(script5, function(){
			yrun();
		});
    } else {
    	yrun();
    }
});

function yrun() {
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
	  	show: yconf.show,
	  	hide: yconf.hide,
		close: function(event, ui) { 
			$("#yquery").setValue('');		
		},  
	  	buttons: {
			'@': function() {
				if ($("#yside").dialog('isOpen')) {
					$("#yside").dialog('close');
				} else {
					$("#yside").dialog('open');
				}
			},
    		Next: function() {
    			startRecord = startRecord + maximumRecords;
    			$('#ysearch').trigger('submit');        		
    		},
    		Prev: function() {
    			startRecord = startRecord - maximumRecords;
    			if(startRecord < 0) startRecord = 0;
    			$('#ysearch').trigger('submit');        		
    		}
		},
		drag: function(event, ui) {
			var position = $(".ui-dialog").position();
			var left = $(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'position', [left,position.top+32]);
		},
		dragStop: function(event, ui) {
			var position = $(".ui-dialog").position();
			var left = $(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'position', [left,position.top+32]);
		},
		resizeStop: function(event, ui) {
			var position = $(".ui-dialog").position();
			var height = $(".ui-dialog").height()-85;
			var left = $(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'height', height);
			$("#yside").dialog('option', 'position', [left,position.top+32]);
        },
		close: function(event, ui) {
			$("#yside").dialog('destroy');
			$('#yside').remove();
		},
		open: function(event, ui) {
			$('<div id="yside" style="padding:0px;"></div>').insertAfter(".ui-dialog-content");
			var position = $(".ui-dialog").position();
			$("#yside").dialog({
				title: 'Navigation',
				autoOpen: false,
				draggable: false,
				resizable: false,
				width: 220,
				height: yconf.height-85,
				minHeight: yconf.height-85,
				show: 'slide',
				hide: 'slide',
				position : [position.left+yconf.width+5,position.top+32],
				open: function(event, ui) {
					$('div.ui-widget-shadow').remove();
					$('ypopup').dialog( 'moveToTop' );
				}
			});
			$('.ui-widget-shadow').remove();
			$('div[aria-labelledby="ui-dialog-title-yside"] div.ui-dialog-titlebar').remove();
		}  
	});
	
	$('#ysearch').keyup(function(e) {
		if(e.which == 27) {	// ESC
			$("#ypopup").dialog('close');
		} else if(e.which == 34) {	// PageDown
			startRecord = startRecord + maximumRecords;
		} else if(e.which == 33) {	// PageUp
			startRecord = startRecord - maximumRecords;
			if(startRecord < 0) startRecord = 0;			  
		} else {
			startRecord = 0;		
		}
		if ($("#yquery").getValue() == '') {
			$("#ypopup").dialog('close');
		} else {
			if(!submit) yacysearch(false);
			else submit = false;
		}		
		return false;		
	});
	
	$('#ysearch').submit(function() {
		submit = true;		
		yacysearch(yconf.global);		
		return false;
	});	
}

function yacysearch(global) {
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
		if(item.name == 'resource') {
			if(item.value == 'global') global = true;
			if(global) item.value = 'global';
		}
		param[i] = item;
	});
	param[param.length] = { name : 'startRecord', value : startRecord };
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
			if(global) var result = 'global';
			else var result = 'local';
			var ylogo = "<div class='ybpane'><a href='"+yconf.link+"' target='_blank'><img src='"+yconf.logo+"' alt='"+yconf.logo+"' title='"+yconf.logo+"' /></a></div>";
			var yresult = "<div class='ybpane'><em>Displaying result "+start+" to "+end+"<br/> of "+total+" "+result+" results.</em></div>";				
			$('div[aria-labelledby="ui-dialog-title-ypopup"] div.ui-dialog-buttonpane').prepend(ylogo+yresult);

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
			$("#ypopup .linktitle a").faviconize({
				position: "before",
				defaultImage: yconf.url + "/yacy/ui/img-2/article.png",
				className: "favicon"
			});
			
			$('#yside').empty();
			$.each (
				data.channels[0].navigation,
				function(i,facet) {
					if (facet) {
						var acc = '#ynav'+i;
						$(acc).accordion('destroy');
						$('<div id="ynav'+i+'" style="margin0px; padding:0px;"></div>').appendTo('#yside');
						var id = "#y"+facet.facetname;
						$('<h3 style="padding-left:25px;">'+facet.displayname+'</h3>').appendTo(acc);
						$('<div id="y'+facet.facetname+'"></div>').appendTo(acc);
						$("<ul class='nav' style='padding-left: 0px; margin-left: -5px; font-size:85%;'></ul>").appendTo(id);
						$.each (
							facet.elements,
							function(j,element) {
								$("<li><a href='#' modifier='"+element.modifier+"'>"+element.name+" ("+element.count+")</a></li>").appendTo(id+" .nav");
							}	
						)
						$(acc).accordion({});
					}								
				}
			);
			$('#ynav1').accordion('activate', false);
			$('#yside a').click(function() {
				var modifier = $(this).attr('modifier');
				modifier = modifier.replace(/^#/,'');
				var query = $("#yquery").getValue() + " " +modifier;
				$("#yquery").setValue(query);
				$("#yquery").trigger('keyup');
			});
        }
    );
}