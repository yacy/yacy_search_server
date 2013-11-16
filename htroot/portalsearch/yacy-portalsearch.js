/*
 * YaCy Portalsearch
 * 
 * @author Stefan Förster (apfelmaennchen) <sof@gmx.de>
 * @version 1.2 
 *
 * Dual licensed under the MIT and GPL licenses:
 *   http://www.opensource.org/licenses/mit-license.php
 *   http://www.gnu.org/licenses/gpl.html
 *
 * Date: 10-Nov-2011
 * 
 */

function statuscheck() {
	if(load_status < 5) {
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
		url      : '',
		'global' : false,		
		theme    : 'start',
		title    : 'YaCy Search Widget',
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
		var style1 = yconf.url + '/portalsearch/yacy-portalsearch.css';
		var style2 = yconf.url + '/jquery/themes/'+yconf.theme+'/jquery-ui-1.8.16.custom.css';
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
		var script1 = yconf.url + '/jquery/js/jquery.query-2.1.7.js';
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
	maximumRecords = parseInt($("#ysearch input[name='maximumRecords']").getValue());
	global = yconf.global;
	
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
			$("#yquery").setValue('');	
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
					yacysearch(false);
				}
			});


		}  
	});
	
	$('#ysearch').keyup(function(e) {		    // React to keyboard input

		if(e.which == 27) {						// Close popup on ESC
			$("#ypopup").dialog('close');
			$("#yquery").setValue("");
		}
		
		if(e.which == 18) {						// Global search on ALT
			global = true;			
			ycurr = $("#yquery").getValue();			
			yacysearch(true);
		}
		
		if(ycurr == $("#yquery").getValue()) {  // Do nothing if search term hasn't changed		
			return false;
		}

		global = yconf.global;					// As this is a new search, revert to default resource 

		if ($("#yquery").getValue() == '') {    // If search term is empty reset to default resource and close popup 
			if($("#ypopup").dialog('isOpen'))
				$("#ypopup").dialog('close');
		} else {                                // Else fire up a search request and remeber the current search term
			ycurr = $("#yquery").getValue();			
			yacysearch(true);
		}		
		return false;		
	});
	
	$('#ysearch').submit(function() {           // Submit a search request
		ycurr = $("#yquery").getValue();

		if (!$("#ypopup").dialog('isOpen'))			
			$("#ypopup").dialog('open');
		else	
			if ($("#yside").dialog('isOpen'))
				$("#yside").dialog('close');					
	
		$("#yquery").focus();	

		yacysearch(true);		
		return false;
	});	
}

function yacysearch(clear) {	
	var url = yconf.url + '/yacysearch.json?callback=?'    // JSONP (cross domain) request URL
	//var url = yconf.url + '/solr/select?wt=yjson&callback=?'    // JSONP (cross domain) request URL

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
	
	var param = [];		                                   // Generate search request parameters from HTML form
	$("#ysearch input").each(function(i){
		var item = { name : $(this).attr('name'), value : $(this).attr('value') };		
		if(item.name == 'resource') {					   // Set parameter for resource according to global
			if(global) 
				item.value = 'global';
			else {
				item.value = 'local'
			}
		}
		if(item.name == 'query' || item.name == 'search') {
			item.value = $.trim(item.value);               // remove heading and trailing white spaces from querey
			if(item.value != ycurr)						   // in case of fast typing ycurr needs to be updated	
				ycurr = item.value;			
		}
		param[i] = item;
	});
	param[param.length] = { name : 'startRecord', value : startRecord };
	ycurr = ycurr.replace("<"," ").replace(">"," ");
	
	$.ajaxSetup({ 
        timeout: 10000,
        error: function(x,e,ex) {
			var err = 'Unknow Error: '+x.responseText;
        	if(x.status==0) {
				err = 'Unknown Network Error! I try to reload...';
				yacysearch(true);
			} else if(x.status==404) {
					err = x.status + ' - Requested URL not found.';
			} else if(x.status==500) {
					err = x.status + ' - Internel Server Error.';
			} else if(e=='parsererror') {
					err = 'Parsing JSON Request failed:' + ex;
			} else if(e=='timeout') {
					err = 'Request Time out.';
			};
			if (clear) $('#ypopup').empty();
			var favicon = "<img src='"+yconf.url+"/yacy/ui/img-2/stop.png' class='favicon'/>";
			var title = "<h3 class='linktitle'>"+favicon+" "+err+"</h3>";						
			var url = "<p class='url'><a href=''>Current search terms: "+ycurr+"</a></p>"
			$(title+url).appendTo("#ypopup");
		}
    }); 
	
	$.getJSON(url, param,
        function(json, status) {	

			if (json[0]) data = json[0];
			else data = json;			
			
			var searchTerms = "";
			searchTerms = data.channels[0].searchTerms.replace("<"," ").replace(">"," ");;			
						
			if($.trim(ycurr.replace(/ /g,"+")) != searchTerms) {
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
			
			if(count == 0) {
    			if (clear) $('#ypopup').empty();
				var favicon = "<img src='"+yconf.url+"/yacy/ui/img-2/stop.png' class='favicon'/>";
				var title = "<h3 class='linktitle'>"+favicon+"No search results!</h3>";						
				var url = "<p class='url'><a href=''>Current search terms: "+searchTerms+"</a></p>"
				var desc = "<p class='desc'>You could restate your search, release some navigators or switch to global search...</p>";
				$(title+desc+url).appendTo("#ypopup");
			}
			
			if(clear) {		
				$('#yside').empty();
								
				var ylogo = "<a href='"+yconf.link+"' target='_blank'><img style='padding-left: 24px;' src='"+yconf.logo+"' alt='"+yconf.logo+"' title='"+yconf.logo+"' /></a>";
				var ymsg= "Total "+result+" results: "+total;
				$("<div class='ymsg'><table><tr><td width='55px'>"+ylogo+"</td><td id='yresult'>"+ymsg+"</td></tr></div").appendTo('#yside');
				$('<hr />').appendTo("#yside");
					
				var selected = 'selected="selected">';
				var select1 = '<select class="selector" id="yglobal"><option value="local"';
				var select2 = 'local</option><option value="global"';
				var select3 = 'global</option></select>';
				
				if(global) {
					select = select1 + '>' + select2 + selected + select3;
				} else {
					select = select1 + selected + select2 + '>' + select3;					
				}
				
				$('<div class="ui-widget ynav"><label for="yglobal">Get local/global results:</label><br />'+select+'</div>').appendTo('#yside');
				$("#yglobal").combobox({									
					selected: function(event, ui) { 						
						if(ui.item.value == "global") {
							global = true;
						} else {
							global = false;
						}
						yacysearch(true);
					}
				});				
				
				select1 = '<select class="selector" id="yrecent"><option value="relevance"';
				select2 = 'Relevance</option><option value="date"';
				select3 = 'Date</option></select>';
				
				var query = unescape($("#yquery").getValue());	
				if(query.indexOf("/date") != -1) {
					select = select1 + '>' + select2 + selected + select3;
				} else {
					select = select1 + selected + select2 + '>' + select3;					
				}
				$('<div class="ui-widget ynav"><label for="yrecent">Sort result by:</label><br />'+select+'</div>').appendTo('#yside');
				$("#yrecent").combobox({									
					selected: function(event, ui) { 						
						if(ui.item.value == "date") {
							query = query + " /date";
						} else {
							query = query.replace(/\s\/date/g,"");
						}
						$("#yquery").setValue($.trim(query));
						$("#yquery").trigger('keyup');
					}
				});
				
				$('<hr />').appendTo("#yside");				
				$("<p class='ytxt'>You can narrow down your search by selecting one of the below navigators:</p>").appendTo('#yside');
				
				/*
				var label = '<label for="ylang">Language:</label><br />';				
				var select = '<select class="selector" id="ylang"><option value="">Select one...</option><option value="en" >en-English</option><option value="fr" >fr-French</option><option value="es" >es-Spanish</option><option value="de" >de-German</option><option value="zh" >zh-Chinese</option></select>';
				$('<div class="ui-widget ynav">'+label+select+'</div>').appendTo('#yside');
				$("#ylang").combobox({									
					selected: function(event, ui) { 
						var query = unescape($("#yquery").getValue() + " /language/" +ui.item.value);
						$("#yquery").setValue(query);
						ynavigators.push("/language/"+ui.item.value);
						$("#yquery").trigger('keyup');
					}
				});
				*/
				
				$.each (    // Loop through all available navigators and include comboboxes in sidebar
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
									ynavigators.push(ui.item.value);
									$("#yquery").trigger('submit');								
								}
							});							
						}								
					}
				);
				
				$('<hr />').appendTo("#yside");	
				if(ynavigators.length > 0) {
					$("<p class='ytxt'>Uncheck to release navigators:</p>").appendTo('#yside');	
				}
							
				cancelNavigators(ynavigators, "#yside");

				if($("#ypopup .yloading").length == 0) {
					$(".ynav-cancel").bind("change", function(event) {
						var query = $("#yquery").getValue();
						var str = $(event.target).val();
						var idx = ynavigators.indexOf($.trim(str));
						if(idx!=-1) ynavigators.splice(idx, 1);
						var regexp = new RegExp(' '+str.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&"));   // properly escape string for regexp
						$("#yquery").setValue($.trim(query.replace(regexp,"")));
						startRecord = 0;
						$("#yquery").trigger('submit');
					});
					autoOpenSidebar();
					if ($("#ypopup").dialog('isOpen')) {					
						// If you got maximumRecords results, but still have display space, load more results
						if($("#ypopup h3 :last").position().top < $("#ypopup").dialog( "option", "height" ) && count == maximumRecords) {						
							startRecord = startRecord + maximumRecords;
							yacysearch(false);						
						}
					}
				} 
			 }
        }
    );
	function autoOpenSidebar() {	              	
		window.setTimeout(function() {                      // The delay prevents the sidebar to open on every intermediate search results
			if($("#ypopup .yloading").length == 0) {        // Check again wether a search result is still loading
				if(	$("#yquery").getValue() == ycurr) {		// Open side bar only if result matches current search term												
					$("#yside").dialog('open');
					$("#yquery").focus();			
				}
			}
		} , 1000);	
	}
	function cancelNavigators(ynavigators, appendTo) {   // Include checkboxes to release navigators
		var arLen=ynavigators.length;
		var query = $("#yquery").getValue();
		for ( var i=0, len=arLen; i<len; ++i ){	
			if(query.indexOf(ynavigators[i]) != -1)      // Check wether search term still contains the navigator
				$(' <input type="checkbox" checked="checked" class="ynav-cancel" name="ynav'+i+'" value="'+ynavigators[i]+'"><span class="ytxt">'+ynavigators[i]+'</span><br>').appendTo(appendTo);			
			else
				ynavigators.splice(i, 1);                // Remove navigator from array as it has been removed manually from search term 
		}
	}
}