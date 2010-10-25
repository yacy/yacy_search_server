function statuscheck() {
	if(load_status < 4) {
		return;
	} else {
		window.clearInterval(loading);
		yrun();
	}
}
function openNavigator(modifier) {
	var query = $("#yquery").getValue() + " " +modifier;
	$("#yquery").setValue(query);
	$("#yquery").trigger('keyup');		
}
$(document).ready(function() {
	$.ajaxSetup({
		timeout: 5000,
		cache: true
	})
	// apply default properties
	ycurr = '';
	startRecord = 0;
	maximumRecords = 10;	
	submit = false;	
	yconf = $.extend({
		url      : 'is a mandatory property - no default',
		'global' : false,		
		theme    : 'start',
		title    : 'YaCy P2P Web Search',
		logo     : yconf.url + '/yacy/ui/img/yacy-logo.png',
		link     : 'http://yacy.net',
		width    : 640,
		height   : 640,
		position : [150,50],
		modal    : false,			
		resizable: true,
		show     : '',
		hide     : '',
		load_js	 : true,
		load_css : true	
	}, yconf);
	
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

	load_status = 0;
	loading = window.setInterval("statuscheck()", 200);    
    if(yconf.load_js) {
		var script1 = yconf.url + '/yacy/ui/js/jquery.query.js';
		var script2 = yconf.url + '/yacy/ui/js/jquery.form.js';
		var script3 = yconf.url + '/yacy/ui/js/jquery.field.min.js';
		var script4 = yconf.url + '/yacy/ui/js/jquery-ui-1.7.2.min.js';
		
		$.getScript(script1, function(){ load_status++; });
		$.getScript(script2, function(){ load_status++; });
		$.getScript(script3, function(){ load_status++; });
		$.getScript(script4, function(){ load_status++; });
    } else {
    	yrun();
    }
});
function yrun() {
	
	$.extend($.ui.accordion.defaults, {
		autoHeight: false,
		clearStyle: true,
		collapsible: true,
		header: "h3"
	});	
	
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
			var height = $(".ui-dialog").height()-55;
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
				height: $(".ui-dialog").height()-55,
				minHeight: $(".ui-dialog").height()-55,
				show: 'slide',
				hide: 'slide',
				position : [position.left+$(".ui-dialog").width()+5,position.top+32],
				open: function(event, ui) {
					$('div.ui-widget-shadow').remove();
					$('ypopup').dialog( 'moveToTop' );
				}
			});
			$('.ui-widget-shadow').remove();
			$('div[aria-labelledby="ui-dialog-title-yside"] div.ui-dialog-titlebar').remove();

			$("#ypopup").bind("scroll", function(e){
				p1 = $("#ypopup h3 :last").position().top;
				if(p1-$("#ypopup").dialog( "option", "height" ) < 0) {
					startRecord = startRecord + maximumRecords;
					yacysearch(submit, false);
				}
			});


		}  
	});
	
	$('#ysearch').keyup(function(e) {		

		if(e.which == 27) {						// ESC
			$("#ypopup").dialog('close');
			$("#yquery").setValue("");
		} else if(e.which == 39) {				// Right
			startRecord = startRecord + maximumRecords;
			yacysearch(submit, false);					
		}
		if(ycurr == $("#yquery").getValue()) {		
			return false;
		} 

		if ($("#yquery").getValue() == '') {
			if($("#ypopup").dialog('isOpen'))
				$("#ypopup").dialog('close');
		} else {
			ycurr = $("#yquery").getValue();
			if(!submit) yacysearch(false, true);
			else submit = false;
		}		
		return false;		
	});
	
	$('#ysearch').submit(function() {
		submit = true;
		ycurr = $("#yquery").getValue();

		if (!$("#ypopup").dialog('isOpen'))			
			$("#ypopup").dialog('open');
		else	
			if ($("#yside").dialog('isOpen'))
				$("#yside").dialog('close');					
	
		$("#yquery").focus();	
		
		yacysearch(yconf.global, true);		
		return false;
	});	
}

function yacysearch(global, clear) {	
	var url = yconf.url + '/yacysearch.json?callback=?'

	if(clear) {
		$('#ypopup').empty();

		var loading = "<div class='yloading'><h3 class='linktitle'><em>Loading: "+yconf.url+"</em><br/>";
		var loadimg = "<img src='"+yconf.url+"/yacy/ui/img/loading2.gif' align='absmiddle'/></h3></div>";
		$('#ypopup').append(loading+loadimg);

		if (!$("#ypopup").dialog('isOpen'))			
			$("#ypopup").dialog('open');
		else	
			if ($("#yside").dialog('isOpen'))
				$("#yside").dialog('close');					

		$("#yquery").focus();
	} 


	var param = [];		
	$("#ysearch input").each(function(i){
		var item = { name : $(this).attr('name'), value : $(this).attr('value') };		
		if(item.name == 'resource') {
			if(item.value == 'global') global = true;
			if(global) item.value = 'global';
		}
		if(item.name == 'query' || item.name == 'search') {
			if(item.value != ycurr)				
				ycurr = item.value;
		}
		param[i] = item;
	});
	param[param.length] = { name : 'startRecord', value : startRecord };
	$.ajaxSetup({ 
        timeout: 10000,
        error: function() {if (clear) $('#ypopup').empty();}
    }); 
	$.getJSON(url, param,
        function(json, status) {	
			if (json[0]) data = json[0];
			else data = json;
			var searchTerms = data.channels[0].searchTerms.replace(/\+/g," ");			
			if(ycurr != searchTerms)
				return false;
			if(clear)	
				$('#ypopup').empty();			
			var total = data.channels[0].totalResults.replace(/[,.]/,"");
			if(global) var result = 'global';
			else var result = 'local';

			var count = 0;
		   	$.each (
				data.channels[0].items,
				function(i,item) {
					if (item) {
						var favicon = "<img src='"+yconf.url+"/ViewImage.png?width=16&amp;height=16&amp;code="+item.faviconCode+"' class='favicon'/>";
						var title = "<h3 class='linktitle'>"+favicon+"<a href='"+item.link+"' target='_blank'>"+item.title+"</a></h3>";						
						var url = "<p class='url'><a href='"+item.link+"' target='_blank'>"+item.link+"</a></p>"
						var desc = "<p class='desc'>"+item.description+"</p>";
						var date = "<p class='date'>"+item.pubDate.substring(0,16);
						var size = " | "+item.sizename+"</p>";
						$(title+desc+url+date+size).appendTo("#ypopup");	
					}
					count++;								
				}
			);
			if(clear) {		
				$('#yside').empty();
				var yglobal = "local";
				if(global)
					yglobal = "global";			
				$('<div id="ylogo" style="margin0px; padding:0px;"></div>').appendTo('#yside');
				$('<h3 style="padding-left:25px;">'+yconf.title+'</h3>').appendTo('#ylogo');
				var ylogo = "<a href='"+yconf.link+"' target='_blank'><img src='"+yconf.logo+"' alt='"+yconf.logo+"' title='"+yconf.logo+"' /></a>";
				var ymsg= "Total "+yglobal+" results: "+total;
				$("<div class='ymsg'><table><tr><td width='55px'>"+ylogo+"</td><td id='yresult'>"+ymsg+"</td></tr></div").appendTo('#ylogo');
				$('#ylogo').accordion({
					collapsible: false					
				});
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
							$("<ul class='nav'></ul>").appendTo(id);
							$.each (
								facet.elements,
								function(j,element) {
									var mod = element.modifier.replace(/'/g,"%27");
									$("<li><a href='javascript:openNavigator(\""+mod+"\")'>"+element.name+" ("+element.count+")</a></li>").appendTo(id+" .nav");
								}	
							)
							$(acc).accordion({});
						}								
					}
				);
				if(count>0) {
					autoOpenSidebar();
					if ($("#ypopup").dialog('isOpen')) {					
						if($("#ypopup h3 :last").position().top < $("#ypopup").dialog( "option", "height" )) {
							startRecord = startRecord + maximumRecords;
							yacysearch(submit, false);						
						}
					}
				}
			 }
        }
    );
	function autoOpenSidebar() {			
		window.setTimeout(function() {
			if(	$("#yquery").getValue() == ycurr) {													
				$("#yside").dialog('open');
				$('#ynav1').accordion('activate', false);
				$("#yquery").focus();			
			}	
		} , 1500);	
	}
}