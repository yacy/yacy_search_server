/*
 * YaCy Portalsearch
 * 
 * @author Stefan Förster (apfelmaennchen)
 * @version 1.1 
 * 
 * @requires jquery-1.6.1
 * @requires jquery-ui-1.8.13
 * @requires jquery-query-2.1.7
 * @requires jquery.form-2.73
 * @requires jquery.field-0.9.2.min
 *
 * Dual licensed under the MIT and GPL licenses:
 *   http://www.opensource.org/licenses/mit-license.php
 *   http://www.gnu.org/licenses/gpl.html
 *
 * Date: 19-MAY-2011
 * 
 */

function statuscheck() {
	if(load_status < 4) {
		return;
	} else {
		window.clearInterval(loading);
		yrun();
	}
}

$(document).ready(function() {
	ynavigators =  new Array();
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
		var style1 = yconf.url + '/jquery/css/yacy-portalsearch.css';
		var style2 = yconf.url + '/jquery/themes/'+yconf.theme+'/ui.all.css';
		var style3 = yconf.url + '/jquery/css/jquery-ui-combobox.css';
	
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
	}

	load_status = 0;
	loading = window.setInterval("statuscheck()", 200);    
    if(yconf.load_js) {
		var script1 = yconf.url + '/jquery/js/jquery-query-2.1.7.js';
		var script2 = yconf.url + '/jquery/js/jquery.form-2.73.js';
		var script3 = yconf.url + '/jquery/js/jquery.field-0.9.2.min.js';
		var script4 = yconf.url + '/jquery/js/jquery-ui-1.8.16.custom.min.js';
		var script5 = yconf.url + '/jquery/js/jquery-ui-combobox.js';
		
		$.getScript(script1, function(){ load_status++; });
		$.getScript(script2, function(){ load_status++; });
		$.getScript(script3, function(){ load_status++; });
		$.getScript(script4, function(){ load_status++; });
		$.getScript(script5, function(){ load_status++; });
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
			var position = $("#ypopup").parent(".ui-dialog").position();
			var left = $("#ypopup").parent(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'position', [left,position.top+32]);
		},
		dragStop: function(event, ui) {
			var position = $("#ypopup").parent(".ui-dialog").position();
			var left = $("#ypopup").parent(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'position', [left,position.top+32]);
		},
		resizeStop: function(event, ui) {
			var position = $("#ypopup").parent(".ui-dialog").position();
			var height = $("#ypopup").parent(".ui-dialog").height()-55;
			var left = $("#ypopup").parent(".ui-dialog").width()+5+position.left;
			$("#yside").dialog('option', 'height', height);
			$("#yside").dialog('option', 'position', [left,position.top+32]);
        },
		close: function(event, ui) {
			$("#yside").dialog('destroy');
			$('#yside').remove();
		},
		open: function(event, ui) {
			$('<div id="yside" style="padding:0px;"></div>').insertAfter("#ypopup").parent(".ui-dialog-content");
			var position = $("#ypopup").parent(".ui-dialog").position();
			$("#yside").dialog({
				title: 'Navigation',
				autoOpen: false,
				draggable: false,
				resizable: false,
				width: 220,
				height: $("#ypopup").parent(".ui-dialog").height()-55,
				minHeight: $("#ypopup").parent(".ui-dialog").height()-55,
				show: 'slide',
				hide: 'slide',
				position : [position.left+$("#ypopup").parent(".ui-dialog").width()+5,position.top+32],
				open: function(event, ui) {
					$('div.ui-widget-shadow').remove();
					$('#ypopup').dialog( 'moveToTop' );
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
        error: function() {
        			if (clear) $('#ypopup').empty();
        		}
    }); 
	
	$.getJSON(url, param,
        function(json, status) {	

			if (json[0]) data = json[0];
			else data = json;			
			
			var searchTerms = data.channels[0].searchTerms;			
			
			if(ycurr.replace(/ /g,"+") != searchTerms) {
				return false;
			}
			if(clear) {	
				$('#ypopup').empty();
			}
			
			var total = data.channels[0].totalResults;
			
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
				var query = unescape($("#yquery").getValue());
				var yglobal = "local";		
				var sel_date = "";
				var sel_relev = "";
				var sel_local = "";
				var sel_global = "";				
				if(query.indexOf("/date") != -1) 
					sel_date = 'checked="checked"';
				else 
					sel_relev = 'checked="checked"';
				
				if(global) {
					sel_global = 'checked="checked"';
					yglobal = "global";	
				}
				else 
					sel_local = 'checked="checked"';
								
				var ylogo = "<a href='"+yconf.link+"' target='_blank'><img style='padding-left: 24px;' src='"+yconf.logo+"' alt='"+yconf.logo+"' title='"+yconf.logo+"' /></a>";
				var ymsg= "Total "+yglobal+" results: "+total;
				$("<div class='ymsg'><table><tr><td width='55px'>"+ylogo+"</td><td id='yresult'>"+ymsg+"</td></tr></div").appendTo('#yside');
				$('<hr />').appendTo("#yside");

				$("<p class='ytxt'>You can narrow down your search by selecting one of the below navigators:</p>").appendTo('#yside');
				var label = '<label for="ylang">Language:</label><br />';				
				var select = '<select class="selector" id="ylang"><option value="">Select one...</option><option value="en" >en-English</option><option value="fr" >fr-French</option><option value="es" >es-Spanish</option><option value="de" >de-German</option><option value="zh" >zh-Chinese</option></select>';
				$('<div class="ui-widget ynav">'+label+select+'</div>').appendTo('#yside');
				$("#ylang").combobox({									
					selected: function(event, ui) { 
						var query = unescape($("#yquery").getValue() + " /language/" +ui.item.value);
						$("#yquery").setValue(query);
						$("#yquery").trigger('keyup');	
					}
				});	
				$.each (
					data.channels[0].navigation,
					function(i,facet) {
						if (facet) {
							var id = "#y"+facet.facetname;
							var label = '<label for="y' +facet.facetname+ '">' +facet.displayname+ ': </label><br />';
							var select = '<select id="y'  +facet.facetname+ '"><option value="">Select one...</option></select>';
							$('<div class="ui-widget ynav">'+label+select+'</div>').appendTo('#yside');
							$.each (
								facet.elements,
								function(j,element) {
									var mod = '<option value="'+element.modifier +'">'+element.name+ ' (' +element.count+ ')</option>';
									$(mod).appendTo(id);
								}	
							)
							$(id).combobox({									
								selected: function(event, ui) { 
									var query = unescape($("#yquery").getValue() + " " +ui.item.value);
									$("#yquery").setValue(query);
									$("#yquery").trigger('keyup');
									ynavigators.push(ui.item.value);
								}
							});							
						}								
					}
				);
				$('<hr />').appendTo("#yside");				
				var radio1 = '<table><tr><td><span class="ynav">Get results: </span><div class="yradio" id="yglobal"><input type="radio" id="local" name="yglobal"" '+sel_local+' /><label for="local">local</label><br><input type="radio" id="global" name="yglobal" '+sel_global+' /><label for="global">global</label></div></td>';
				var radio2 = '<td><span class="ynav">Sort by: </span><div class="yradio" id="yrecent"><input type="radio" id="relevance" name="yrecent" '+sel_relev+' /><label for="relevance">relevance</label><br><input type="radio" id="date" name="yrecent" '+sel_date+' /><label for="date">date</label></div></td></tr></table>';
				$(radio1 + radio2).appendTo('#yside');
			
				$('#local, #global, #date, #relevance').change(function() {
					var query = unescape($("#yquery").getValue());
					if (this.id == "date") {
						$("#yquery").setValue(query + " /date");
					} else if (this.id == "relevance") {
						$("#yquery").setValue(query.replace(/ \/date/g,""));
					} else if (this.id == "global") {
						global = true;
					} else if (this.id == "local") {
						global = false;
					}
					yacysearch(global, true);
				});
				
				$('<hr />').appendTo("#yside");	
				var arLen=ynavigators.length;
				for ( var i=0, len=arLen; i<len; ++i ){
					$('<p><img src="/yacy/ui/img-2/cancel_round.png" class="ynav-cancel" /><span class="ytxt"> '+ynavigators[i]+'</span></p>').appendTo("#yside");
				}
				if(count>0) {
					autoOpenSidebar();
					if ($("#ypopup").dialog('isOpen')) {					
						if($("#ypopup h3 :last").position().top < $("#ypopup").dialog( "option", "height" )) {
							startRecord = startRecord + maximumRecords;
							yacysearch(submit, false);						
						}
					}
					$(".ynav-cancel").bind("click", function(event) {
						var str = $(event.target).next().text().replace(/^[\s\xA0]+/, "").replace(/[\s\xA0]+$/, "");
						var idx = ynavigators.indexOf(str);
						if(idx!=-1) ynavigators.splice(idx, 1);
						var regexp = new RegExp(str.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&"));
						$("#yquery").setValue(query.replace(regexp,"").replace(/^[\s\xA0]+/, "").replace(/[\s\xA0]+$/, ""));
						startRecord = 0;
						yacysearch(submit, true);
					});
				} 
			 }
        }
    );
	function autoOpenSidebar() {			
		window.setTimeout(function() {
			if(	$("#yquery").getValue() == ycurr) {													
				$("#yside").dialog('open');
				$("#yquery").focus();			
			}	
		} , 1500);	
	}
}