/**
 * Faviconize (http://www.babylon-design.com/share/faviconize/)
 * A jQuery plugin for displaying a favicon on an external link.
 * 
 * Version 1.0
 * March 4th, 2008
 *
 * Author : Samuel Le Morvan (http://www.babylon-design.com/)
 * 
 * Inspired by:
 * Ask the CSS Guy (http://www.askthecssguy.com/2006/12/hyperlink_cues_with_favicons.html)
 * 
 *
 **/
(function($){
	$.fn.faviconize = function(e) {
		
		var e = $.extend({position:'before', linkable:false, exceptions: new Array()}, e);
		 
		function faviconizePlace(a, h) {
			switch(e.position) {
				case "before" : a.before(h+'&nbsp;'); break;
				case "after" : a.after('&nbsp;'+h); break;
				default :  break;
			}
		}
		
		$(this).each(function() {
			var a = $(this);
			var r = a.attr("href").match(/http[s]*:\/\/[a-z0-9.-]*(\/)?/i);
			var r = r[0] + ((r[1] == null) ? "/" : "");
			if(r) {
				if($.grep(e.exceptions, function(x) {x = (x.match(/\/$/) == null) ? x+"/" : x; return (x == r);}).length == 0) {
					var f = r + 'favicon.ico';
					var i = new Image(); $(i).attr("src", f);
					var h = '<img src="'+f+'" alt="'+ ((e.linkable) ? a.text() : '') +'" '+ ((e.className) ? 'class="'+e.className+'"' : '') +' />';
					h = (e.linkable) ? '<a href="'+a.attr("href")+'">'+h+'</a>' : h;
					$(i).load(function() {faviconizePlace(a, h);});
					$(i).error(function() {if(e.defaultImage) {faviconizePlace(a, h.replace(/src="(.*?)"/i, 'src="'+e.defaultImage+'"')); }});
				}
			}
		});
	}
})(jQuery);
