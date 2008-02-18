	/******************************************************************************
Name:    Highslide JS
Version: 3.3.9 (February 15 2008)
Config:  default +positioning +events +unobtrusive +transitions +inline +ajax +iframe +flash
Author:  Torstein Hønsi
Support: http://vikjavev.no/highslide/forum

Licence:
Highslide JS is licensed under a Creative Commons Attribution-NonCommercial 2.5
License (http://creativecommons.org/licenses/by-nc/2.5/).

You are free:
	* to copy, distribute, display, and perform the work
	* to make derivative works

Under the following conditions:
	* Attribution. You must attribute the work in the manner  specified by  the
	  author or licensor.
	* Noncommercial. You may not use this work for commercial purposes.

* For  any  reuse  or  distribution, you  must make clear to others the license
  terms of this work.
* Any  of  these  conditions  can  be  waived  if  you  get permission from the 
  copyright holder.

Your fair use and other rights are in no way affected by the above.
******************************************************************************/

var hs = {

// Apply your own settings here, or override them in the html file.  
graphicsDir : '/env/grafics/highslide/',
restoreCursor : 'zoomout.cur', // necessary for preload
expandSteps : 10, // number of steps in zoom. Each step lasts for duration/step milliseconds.
expandDuration : 250, // milliseconds
restoreSteps : 10,
restoreDuration : 250,
marginLeft : 15,
marginRight : 15,
marginTop : 15,
marginBottom : 15,
zIndexCounter : 1001, // adjust to other absolutely positioned elements

restoreTitle : 'Click to close image, click and drag to move. Use arrow keys for next and previous.',
loadingText : 'Loading...',
loadingTitle : 'Click to cancel',
loadingOpacity : 0.75,
focusTitle : 'Click to bring to front',
allowMultipleInstances: true,
numberOfImagesToPreload : 5,
captionSlideSpeed : 1, // set to 0 to disable slide in effect
padToMinWidth : false, // pad the popup width to make room for wide caption
outlineWhileAnimating : 2, // 0 = never, 1 = always, 2 = HTML only 
outlineStartOffset : 3, // ends at 10
fullExpandTitle : 'Expand to actual size',
fullExpandPosition : 'bottom right',
fullExpandOpacity : 1,
showCredits : false, // you can set this to false if you want
creditsText : 'Powered by <i>Highslide JS</i>',
creditsHref : 'http://vikjavev.no/highslide/',
creditsTitle : 'Go to the Highslide JS homepage',
enableKeyListener : true,
transitions : [],
dimmingOpacity: 0, // Lightbox style dimming background
dimmingDuration: 50, // 0 for instant dimming


// HTML extension

previousText : 'Previous',
nextText : 'Next', 
moveText : 'Move',
closeText : 'Close', 
closeTitle : 'Click to close', 
resizeTitle : 'Resize',
allowWidthReduction : false,
allowHeightReduction : true,
preserveContent : true, // Preserve changes made to the content and position of HTML popups.
objectLoadTime : 'before', // Load iframes 'before' or 'after' expansion.
cacheAjax : true, // Cache ajax popups for instant display. Can be overridden for each popup.

// These settings can also be overridden inline for each image
anchor : 'auto', // where the image expands from
align : 'auto', // position in the client (overrides anchor)
targetX: null, // the id of a target element
targetY: null,
captionId : null,
spaceForCaption : 30, // leaves space below images with captions
slideshowGroup : null, // defines groups for next/previous links and keystrokes
minWidth: 200,
minHeight: 200,
allowSizeReduction: true, // allow the image to reduce to fit client size. If false, this overrides minWidth and minHeight
outlineType : 'drop-shadow', // set null to disable outlines
wrapperClassName : 'highslide-wrapper', // for enhanced css-control

// END OF YOUR SETTINGS


// declare internal properties
preloadTheseImages : [],
continuePreloading: true,
expanders : [],
overrides : [
	'allowSizeReduction',
	'anchor',
	'align',
	'targetX',
	'targetY',
	'outlineType',
	'outlineWhileAnimating',
	'spaceForCaption',
	'captionId',
	'captionText',
	'captionEval',
	'transitions',
	'dimmingOpacity',
	
	'contentId',
	'width',
	'height',
	'allowWidthReduction',
	'allowHeightReduction',
	'preserveContent',
	'objectType',	
	'cacheAjax',	
	'objectWidth',
	'objectHeight',
	'objectLoadTime',	
	'swfObject',
	'wrapperClassName',
	'minWidth',
	'minHeight',
	'slideshowGroup',
	'easing',
	'easingClose',
	'fadeInOut'
],
overlays : [],
faders : [],

pendingOutlines : {},
sleeping : [],
preloadTheseAjax : [],
cacheBindings : [],
cachedGets : {},
clones : {},
ie : (document.all && !window.opera),
safari : /Safari/.test(navigator.userAgent),
geckoMac : /Macintosh.+rv:1\.[0-8].+Gecko/.test(navigator.userAgent),

$ : function (id) {
	return document.getElementById(id);
},

push : function (arr, val) {
	arr[arr.length] = val;
},

createElement : function (tag, attribs, styles, parent, nopad) {
	var el = document.createElement(tag);
	if (attribs) hs.setAttribs(el, attribs);
	if (nopad) hs.setStyles(el, {padding: 0, border: 'none', margin: 0});
	if (styles) hs.setStyles(el, styles);
	if (parent) parent.appendChild(el);	
	return el;
},

setAttribs : function (el, attribs) {
	for (var x in attribs) el[x] = attribs[x];
},

setStyles : function (el, styles) {
	for (var x in styles) {
		try { 
			if (hs.ie && x == 'opacity') 
				el.style.filter = (styles[x] == 1) ? '' : 'alpha(opacity='+ (styles[x] * 100) +')';
			else el.style[x] = styles[x]; 
		}
		catch (e) {}
	}
},

ieVersion : function () {
	arr = navigator.appVersion.split("MSIE");
	return parseFloat(arr[1]);
},

getPageSize : function () {
	var iebody = document.compatMode && document.compatMode != "BackCompat" 
		? document.documentElement : document.body;	
	
	
	var b = document.body;
	var xScroll = (window.innerWidth && window.scrollMaxX) 
			? window.innerWidth + window.scrollMaxX :
				(b.scrollWidth > b.offsetWidth ? b.scrollWidth : b.offsetWidth),
		yScroll = (window.innerHeight && window.scrollMaxY) 
			? window.innerHeight + window.scrollMaxY :
				(b.scrollHeight > b.offsetHeight ? b.scrollHeight : b.offsetHeight),
		pageWidth = hs.ie ? iebody.scrollWidth :
			(document.documentElement.clientWidth || self.innerWidth);
      	pageHeight = hs.ie ? iebody.clientHeight : 
			(document.documentElement.clientHeight || self.innerHeight);
	
	var width = hs.ie ? iebody.clientWidth : 
			(document.documentElement.clientWidth || self.innerWidth),
		height = hs.ie ? iebody.clientHeight : self.innerHeight;
	
	return {
		pageWidth: xScroll < pageWidth ? pageWidth : xScroll,
		pageHeight: yScroll < pageHeight ? pageHeight : yScroll,
		width: width,
		height: height,		
		scrollLeft: hs.ie ? iebody.scrollLeft : pageXOffset,
		scrollTop: hs.ie ? iebody.scrollTop : pageYOffset
	}
},

position : function(el)	{ 
	var p = { x: el.offsetLeft, y: el.offsetTop };
	while (el.offsetParent)	{
		el = el.offsetParent;
		p.x += el.offsetLeft;
		p.y += el.offsetTop;
		if (el != document.body && el != document.documentElement) {
			p.x -= el.scrollLeft;
			p.y -= el.scrollTop;
		}
	}
	return p;
},

expand : function(a, params, custom) {
	if (a.getParams) return params;
	
	try {
		new hs.Expander(a, params, custom);
		return false;		
	} catch (e) { return true; }
},

htmlExpand : function(a, params, custom) {
	if (a.getParams) return params;
	
	for (var i = 0; i < hs.sleeping.length; i++) {
		if (hs.sleeping[i] && hs.sleeping[i].a == a) {
			hs.sleeping[i].awake();
			hs.sleeping[i] = null;
			return false;
		}
	}
	try {
		hs.hasHtmlexpanders = true;
		new hs.Expander(a, params, custom, 'html');
		return false;
	} catch (e) {
		return true;
	}	
},

getElementByClass : function (el, tagName, className) {
	var els = el.getElementsByTagName(tagName);
	for (var i = 0; i < els.length; i++) {
    	if (els[i].className == className) {
			return els[i];
		}
	}
},

getSelfRendered : function() {
	var s =	
		 '<div class="highslide-header"><ul>'
		+	'<li class="highslide-previous"><a onclick="return hs.previous(this)" href="#">'+ hs.previousText +'</a></li>'
		+	'<li class="highslide-next"><a onclick="return hs.next(this)" href="#">'+ hs.nextText +'</a></li>'
		+	'<li class="highslide-move"><a href="#" onclick="return false">'+ hs.moveText +'</a></li>'
		+	'<li class="highslide-close"><a onclick="return hs.close(this)" title="'+ hs.closeTitle +'" href="#">'
			+ hs.closeText +'</a></li>'
		+'</ul></div>'
		+'<div class="highslide-body"></div>'
		+'<div class="highslide-footer"><div>'
		+	'<span class="highslide-resize" title="'+ hs.resizeTitle +'"><span></span></span>'
		+'</div></div>';
	return hs.createElement('div', { className: 'highslide-html-content', innerHTML: s } );
},


setClickEvents : function () {
	var els = document.getElementsByTagName('A');
	for (var i = 0; i < els.length; i++) {
		if (/^highslide$/.test(els[i].rel)) {
			if (hs.fireEvent(hs, 'onSetClickEvent', { element: els[i] })) {
			
				els[i].onclick = function() { return hs.expand(this) };
			}
			
			els[i].removeAttribute('rel');	
		}
		
		var match = /^highslide-(ajax|iframe)$/.exec(els[i].rel);
		if (match) {
			(function(){
				var t = match[1];
				if (hs.fireEvent(hs, 'onSetClickEvent', { element: els[i], type: t })) {
				
					els[i].onclick = function() {
						return hs.htmlExpand(this, { objectType: t } ); 
					};
				}
			})();
			els[i].removeAttribute('rel');
		}
	}
	if (!hs.pageLoaded) setTimeout( hs.setClickEvents, 50);
},

getCacheBinding : function (a) {
	for (var i = 0; i < hs.cacheBindings.length; i++) {
		if (hs.cacheBindings[i][0] == a) {
			var c = hs.cacheBindings[i][1];
			hs.cacheBindings[i][1] = c.cloneNode(1);
			return c;
		}
	}
},

preloadAjax : function (e) {
	var aTags = document.getElementsByTagName('A');
	var a, re;
	for (var i = 0; i < aTags.length; i++) {
		a = aTags[i];
		re = hs.isHsAnchor(a);
		if (re && re[0] == 'hs.htmlExpand' && hs.getParam(a, 'objectType') == 'ajax' 
				&& hs.getParam(a, 'cacheAjax')) {
			hs.push(hs.preloadTheseAjax, a);
		}
	}
	hs.preloadAjaxElement(0);
},

preloadAjaxElement : function (i) {
	if (!hs.preloadTheseAjax[i]) return;
	var a = hs.preloadTheseAjax[i];
	var cache = hs.getNode(hs.getParam(a, 'contentId'));
	if (!cache) cache = hs.getSelfRendered();
	var ajax = new hs.Ajax(a, cache, 1);	
   	ajax.onError = function () { };
   	ajax.onLoad = function () {
   		hs.push(hs.cacheBindings, [a, cache]);
   		hs.preloadAjaxElement(i + 1);
   	};
   	ajax.run();
},

focusTopmost : function() {
	var topZ = 0, topmostKey = -1;
	for (var i = 0; i < hs.expanders.length; i++) {
		if (hs.expanders[i]) {
			if (hs.expanders[i].wrapper.style.zIndex && hs.expanders[i].wrapper.style.zIndex > topZ) {
				topZ = hs.expanders[i].wrapper.style.zIndex;
				
				topmostKey = i;
			}
		}
	}
	if (topmostKey == -1) hs.focusKey = -1;
	else hs.expanders[topmostKey].focus();
},

getAdjacentAnchor : function(key, op) {
	var aAr = document.getElementsByTagName('A'), hsAr = {}, activeI = -1, j = 0;
	for (var i = 0; i < aAr.length; i++) {
		if (hs.isHsAnchor(aAr[i]) && ((hs.expanders[key].slideshowGroup 
				== hs.getParam(aAr[i], 'slideshowGroup')))) {
			hsAr[j] = aAr[i];
			if (hs.expanders[key] && aAr[i] == hs.expanders[key].a) {
				activeI = j;
			}
			j++;
		}
	}
	return hsAr[activeI + op];
},

getParam : function (a, param) {
	a.getParams = a.onclick;
	var p = a.getParams();
	a.getParams = null;
	
	return (p && typeof p[param] != 'undefined') ? p[param] : hs[param];
},

getSrc : function (a) {
	var src = hs.getParam(a, 'src');
	if (src) return src;
	return a.href;
},

getNode : function (id) {
	var node = hs.$(id), clone = hs.clones[id], a = {};
	if (!node && !clone) return null;
	if (!clone) {
		clone = node.cloneNode(true);
		clone.id = '';
		hs.clones[id] = clone;
		return node;
	} else {
		return clone.cloneNode(true);
	}
},

purge : function(d) {
	if (!hs.ie) return;
	var a = d.attributes, i, l, n;
	if (a) {
		l = a.length;
		for (var i = 0; i < l; i += 1) {
			n = a[i].name;
			if (typeof d[n] === 'function') {
				d[n] = null;
			}
		}
	}
	a = d.childNodes;
	if (a) {
		l = a.length;
		for (var i = 0; i < l; i += 1) {
			hs.purge(d.childNodes[i]);
		}
	}
},
dim : function(exp) {
	if (!hs.dimmer) {
		hs.dimmer = hs.createElement ('div', 
			{ className: 'highslide-dimming', onclick: function() { hs.close() } }, 
			{ position: 'absolute' }, hs.container, true);
		hs.addEventListener(window, 'resize', hs.setDimmerSize);		
	}
	hs.dimmer.style.display = '';
	hs.setDimmerSize();
	hs.dimmer.owner = exp.key;
	if (hs.geckoMac && hs.dimmingGeckoFix) 
		hs.dimmer.style.background = 'url('+ hs.graphicsDir + 'geckodimmer.png)';		
	else
		hs.fade(hs.dimmer, 0, exp.dimmingOpacity, hs.dimmingDuration); 
},
unDim : function(key) {
	if (!hs.dimmer) return;
	if (typeof key != 'undefined' && key != hs.dimmer.owner) return;
	if (hs.upcoming && hs.getParam(hs.upcoming, 'dimmingOpacity')) return;
	if (hs.geckoMac && hs.dimmingGeckoFix) 
		hs.dimmer.style.background = 'none';
	else hs.fade(hs.dimmer, hs.dimmingOpacity, 0, hs.dimmingDuration);
	setTimeout( function() {
			hs.dimmer.style.display = 'none';
		}, 250);
},
setDimmerSize : function() {
	if (!hs.dimmer) return;
	var page = hs.getPageSize();
	hs.setStyles(hs.dimmer, { width: page.pageWidth +'px', height: page.pageHeight +'px'});
},

previousOrNext : function (el, op) {
	var exp = hs.last = hs.getExpander(el);
	try {
		var adj = hs.upcoming =  hs.getAdjacentAnchor(exp.key, op);
		adj.onclick(); 		
	} catch (e){}
	try { exp.close(); } catch (e) {}	
	return false;
},

previous : function (el) {
	return hs.previousOrNext(el, -1);
},

next : function (el) {
	return hs.previousOrNext(el, 1);	
},

keyHandler : function(e) {
	if (!e) e = window.event;
	if (!e.target) e.target = e.srcElement; // ie
	if (e.target.form) return; // form element has focus
	if (!hs.fireEvent(hs, 'onKeyDown', e)) return;
	
	var op = null;
	switch (e.keyCode) {
		case 34: // Page Down
		case 39: // Arrow right
		case 40: // Arrow down
			op = 1;
			break;
		case 33: // Page Up
		case 37: // Arrow left
		case 38: // Arrow up
			op = -1;
			break;
		case 27: // Escape
		case 13: // Enter
			op = 0;
	}
	if (op !== null) {
		hs.removeEventListener(document, 'keydown', hs.keyHandler);
		if (!hs.enableKeyListener) return true;
		
		if (e.preventDefault) e.preventDefault();
    	else e.returnValue = false;
		if (op == 0) {
			try { hs.getExpander().close(); } catch (e) {}
			return false;
		} else {
			return hs.previousOrNext(hs.focusKey, op);
		}
	}
	return true;
},


registerOverlay : function (overlay) {
	hs.push(hs.overlays, overlay);
},

getWrapperKey : function (element) {
	var el, re = /^highslide-wrapper-([0-9]+)$/;
	// 1. look in open expanders
	el = element;
	while (el.parentNode)	{
		if (el.id && re.test(el.id)) return el.id.replace(re, "$1");
		el = el.parentNode;
	}
	// 2. look in thumbnail
	el = element;
	while (el.parentNode)	{
		if (el.tagName && hs.isHsAnchor(el)) {
			for (var key = 0; key < hs.expanders.length; key++) {
				var exp = hs.expanders[key];
				if (exp && exp.a == el) return key;
			}
		}
		el = el.parentNode;
	}
},

getExpander : function (el) {
	try {	
		if (!el) return hs.expanders[hs.focusKey];
		if (typeof el == 'number') return hs.expanders[el];
		if (typeof el == 'string') el = hs.$(el);
		return hs.expanders[hs.getWrapperKey(el)];
	} catch (e) {}
},

isHsAnchor : function (a) {
	return (a.onclick && a.onclick.toString().replace(/\s/g, ' ').match(/hs.(htmlE|e)xpand/));
},

cleanUp : function () {
	for (var i = 0; i < hs.expanders.length; i++)
		if (hs.expanders[i] && hs.expanders[i].isExpanded) hs.focusTopmost();
},
fireEvent : function (obj, evt, args) {
	return obj && obj[evt] ? (obj[evt](obj, args) !== false) : true;
},

mouseClickHandler : function(e) 
{	
	if (!e) e = window.event;
	if (e.button > 1) return true;
	if (!e.target) e.target = e.srcElement;
	
	var el = e.target;
	while (el.parentNode
		&& !(/highslide-(image|move|html|resize)/.test(el.className)))
	{
		el = el.parentNode;
	}
	var exp = hs.getExpander(el);
	if (exp && (exp.isClosing || !exp.isExpanded)) return;
		
	if (exp && e.type == 'mousedown') {
		if (e.target.form) return;
		var match = el.className.match(/highslide-(image|move|resize)/);
		if (match) {
			hs.dragArgs = { exp: exp , type: match[1], left: exp.x.min, width: exp.x.span, top: exp.y.min, 
				height: exp.y.span, clickX: e.clientX, clickY: e.clientY };
			
			if (hs.dragArgs.type == 'image') exp.content.style.cursor = 'move';
			
			hs.addEventListener(document, 'mousemove', hs.dragHandler);
			if (e.preventDefault) e.preventDefault(); // FF
			
			if (/highslide-(image|html)-blur/.test(exp.content.className)) {
				exp.focus();
				hs.hasFocused = true;
			}
			return false;
		}
		else if (/highslide-html/.test(el.className) && hs.focusKey != exp.key) {
			exp.focus();
			exp.redoShowHide();
		}
	} else if (e.type == 'mouseup') {
		
		hs.removeEventListener(document, 'mousemove', hs.dragHandler);
		
		if (hs.dragArgs) {
			
			if (hs.dragArgs.type == 'image')
				hs.dragArgs.exp.content.style.cursor = hs.styleRestoreCursor;
			
			var hasDragged = (Math.abs(hs.dragArgs.dX) + Math.abs(hs.dragArgs.dY) > 0);
			
			if (!hasDragged &&!hs.hasFocused && !/(move|resize)/.test(hs.dragArgs.type)) {
				if (hs.fireEvent(exp, 'onImageClick'))
				exp.close();
			} 
			else if (hasDragged || (!hasDragged && hs.hasHtmlexpanders)) {
				hs.dragArgs.exp.redoShowHide();
			}
			
			if (hs.dragArgs.exp.releaseMask) 
				hs.dragArgs.exp.releaseMask.style.display = 'none';
			
			if (hasDragged) hs.fireEvent(hs.dragArgs.exp, 'onDrop', hs.dragArgs);
			if (hasDragged) hs.setDimmerSize();
			
			hs.hasFocused = false;
			hs.dragArgs = null;
		
		} else if (/highslide-image-blur/.test(el.className)) {
			el.style.cursor = hs.styleRestoreCursor;		
		}
	}
},

dragHandler : function(e)
{
	if (!hs.dragArgs) return;
	if (!e) e = window.event;
	var exp = hs.dragArgs.exp;
	if (exp.iframe) {		
		if (!exp.releaseMask) exp.releaseMask = hs.createElement('div', null, 
			{ position: 'absolute', width: exp.x.span+'px', height: exp.y.span+'px', 
				left: 0, top: 0, zIndex: 4,	background: (hs.ie ? 'white' : 'none'), 
				opacity: 0.01 }, 
			exp.wrapper, true);
		if (exp.releaseMask.style.display == 'none')
			exp.releaseMask.style.display = '';
	}
	
	hs.dragArgs.dX = e.clientX - hs.dragArgs.clickX;
	hs.dragArgs.dY = e.clientY - hs.dragArgs.clickY;
	if (!hs.fireEvent(exp, 'onDrag', hs.dragArgs)) return false;
	
	if (hs.dragArgs.type == 'resize') exp.resize(hs.dragArgs);
	else exp.move(hs.dragArgs);
	return false;
},

addEventListener : function (el, event, func) {
	try {
		el.addEventListener(event, func, false);
	} catch (e) {
		try {
			el.detachEvent('on'+ event, func);
			el.attachEvent('on'+ event, func);
		} catch (e) {
			el['on'+ event] = func;
		}
	} 
},

removeEventListener : function (el, event, func) {
	try {
		el.removeEventListener(event, func, false);
	} catch (e) {
		try {
			el.detachEvent('on'+ event, func);
		} catch (e) {
			el['on'+ event] = null;
		}
	}
},

preloadFullImage : function (i) {
	if (hs.continuePreloading && hs.preloadTheseImages[i] && hs.preloadTheseImages[i] != 'undefined') {
		var img = document.createElement('img');
		img.onload = function() { hs.preloadFullImage(i + 1); };
		img.src = hs.preloadTheseImages[i];
	}
},
preloadImages : function (number) {
	if (number && typeof number != 'object') hs.numberOfImagesToPreload = number;
	var a, re, j = 0;
	
	var aTags = document.getElementsByTagName('A');
	for (var i = 0; i < aTags.length; i++) {
		a = aTags[i];
		re = hs.isHsAnchor(a);
		if (re && re[0] == 'hs.expand') {
			if (j < hs.numberOfImagesToPreload) {
				hs.preloadTheseImages[j] = hs.getSrc(a); 
				j++;
			}
		}
	}
	
	// preload outlines
	new hs.Outline(hs.outlineType, function () { hs.preloadFullImage(0)} );
	
	
	// preload cursor
	var cur = hs.createElement('img', { src: hs.graphicsDir + hs.restoreCursor });
},


genContainer : function () {
	if (!hs.container) {
		hs.container = hs.createElement('div', 
			null, 
			{ position: 'absolute', left: 0, top: 0, width: '100%', zIndex: hs.zIndexCounter }, 
			document.body,
			true
		);
		hs.loading = hs.createElement('a',
			{
				className: 'highslide-loading',
				title: hs.loadingTitle,
				innerHTML: hs.loadingText,
				href: 'javascript:void(0)'
			},
			{
				position: 'absolute',
				opacity: hs.loadingOpacity,
				left: '-9999px',
				zIndex: 1
			}, hs.container
		);
		hs.clearing = hs.createElement('div', null, 
			{ clear: 'both', paddingTop: '1px' }, null, true);
		
		// http://www.robertpenner.com/easing/ 
		Math.linearTween = function (t, b, c, d) {
			return c*t/d + b;
		};
		Math.easeInQuad = function (t, b, c, d) {
			return c*(t/=d)*t + b;
		};
		hs.fireEvent(this, 'onActivate');
	}
},

fade : function (el, o, oFinal, dur, i, dir) {
	if (typeof i == 'undefined') { // new fader
		if (dur === 0) { // instant
			hs.setStyles( el, {
				opacity: oFinal,
				visibility: (o < oFinal ? 'visible': 'hidden')
			});
			return;
		}
		i = hs.faders.length;
		dir = oFinal > o ? 1 : -1;
		var step = (25 / (dur || 250)) * Math.abs(o - oFinal);
	}
	o = parseFloat(o);
	el.style.visibility = (o <= 0) ? 'hidden' : 'visible';
	if (o < 0 || (dir == 1 && o > oFinal)) return;
	if (el.fading && el.fading.i != i) { // reverse
		clearTimeout(hs.faders[el.fading.i]);
		o = el.fading.o;
	}
	el.fading = {i: i, o: o, step: (step || el.fading.step)};
	el.style.visibility = (o <= 0) ? 'hidden' : 'visible';
	hs.setStyles(el, { opacity: o });
	hs.faders[i] = setTimeout(function() {
			hs.fade(el, o + el.fading.step * dir, oFinal, null, i, dir);
	 	}, 25);
},

close : function(el) {
	try { hs.getExpander(el).close(); } catch (e) {}
	return false;
}
}; // end hs object


//-----------------------------------------------------------------------------
hs.Outline =  function (outlineType, onLoad) {
	this.onLoad = onLoad;
	this.outlineType = outlineType;
	var v = hs.ieVersion(), tr;
	
	this.hasAlphaImageLoader = hs.ie && v >= 5.5 && v < 7;
	if (!outlineType) {
		if (onLoad) onLoad();
		return;
	}
	
	hs.genContainer();
	this.table = hs.createElement(
		'table', { cellSpacing: 0 },
		{
			visibility: 'hidden',
			position: 'absolute',
			borderCollapse: 'collapse'
		},
		hs.container,
		true
	);
	this.tbody = hs.createElement('tbody', null, null, this.table, 1);
	
	this.td = [];
	for (var i = 0; i <= 8; i++) {
		if (i % 3 == 0) tr = hs.createElement('tr', null, { height: 'auto' }, this.tbody, true);
		this.td[i] = hs.createElement('td', null, null, tr, true);
		var style = i != 4 ? { lineHeight: 0, fontSize: 0} : { position : 'relative' };
		hs.setStyles(this.td[i], style);
	}
	this.td[4].className = outlineType;
	
	this.preloadGraphic(); 
};

hs.Outline.prototype = {
preloadGraphic : function () {	
	var src = hs.graphicsDir + (hs.outlinesDir || "outlines/")+ this.outlineType +".png";
				
	var appendTo = hs.safari ? hs.container : null;
	this.graphic = hs.createElement('img', null, { position: 'absolute', left: '-9999px', 
		top: '-9999px' }, appendTo, true); // for onload trigger
	
	var pThis = this;
	this.graphic.onload = function() { pThis.onGraphicLoad(); };
	
	this.graphic.src = src;
},

onGraphicLoad : function () {
	var o = this.offset = this.graphic.width / 4,
		pos = [[0,0],[0,-4],[-2,0],[0,-8],0,[-2,-8],[0,-2],[0,-6],[-2,-2]],
		dim = { height: (2*o) +'px', width: (2*o) +'px' };
		
	for (var i = 0; i <= 8; i++) {
		if (pos[i]) {
			if (this.hasAlphaImageLoader) {
				var w = (i == 1 || i == 7) ? '100%' : this.graphic.width +'px';
				var div = hs.createElement('div', null, { width: '100%', height: '100%', position: 'relative', overflow: 'hidden'}, this.td[i], true);
				hs.createElement ('div', null, { 
						filter: "progid:DXImageTransform.Microsoft.AlphaImageLoader(sizingMethod=scale, src='"+ this.graphic.src + "')", 
						position: 'absolute',
						width: w, 
						height: this.graphic.height +'px',
						left: (pos[i][0]*o)+'px',
						top: (pos[i][1]*o)+'px'
					}, 
				div,
				true);
			} else {
				hs.setStyles(this.td[i], { background: 'url('+ this.graphic.src +') '+ (pos[i][0]*o)+'px '+(pos[i][1]*o)+'px'});
			}
			
			if (window.opera && (i == 3 || i ==5)) 
				hs.createElement('div', null, dim, this.td[i], true);
			
			hs.setStyles (this.td[i], dim);
		}
	}
	
	hs.pendingOutlines[this.outlineType] = this;
	if (this.onLoad) this.onLoad();
},
	
setPosition : function (exp, x, y, w, h, vis) {
	if (vis) this.table.style.visibility = (h >= 4 * this.offset) 
		? 'visible' : 'hidden';
	this.table.style.left = (x - this.offset) +'px';
	this.table.style.top = (y - this.offset) +'px';
	this.table.style.width = (w + 2 * (exp.offsetBorderW + this.offset)) +'px';
	w += 2 * (exp.offsetBorderW - this.offset);
	h += + 2 * (exp.offsetBorderH - this.offset);
	this.td[4].style.width = w >= 0 ? w +'px' : 0;
	this.td[4].style.height = h >= 0 ? h +'px' : 0;
	if (this.hasAlphaImageLoader) this.td[3].style.height 
		= this.td[5].style.height = this.td[4].style.height;
},
	
destroy : function(hide) {
	if (hide) this.table.style.visibility = 'hidden';
	else {
		hs.purge(this.table);
		try { this.table.parentNode.removeChild(this.table); } catch (e) {}
	}
}
};

//-----------------------------------------------------------------------------
// The expander object
hs.Expander = function(a, params, custom, contentType) {
	this.a = a;
	this.custom = custom;
	this.contentType = contentType || 'image';
	this.isHtml = (contentType == 'html');
	this.isImage = !this.isHtml;
	
	hs.continuePreloading = false;
	hs.genContainer();
	var key = this.key = hs.expanders.length;
	
	// override inline parameters
	for (var i = 0; i < hs.overrides.length; i++) {
		var name = hs.overrides[i];
		this[name] = params && typeof params[name] != 'undefined' ?
			params[name] : hs[name];
	}
	
	// get thumb
	var el = this.thumb = (params ? hs.$(params.thumbnailId) : null) 
		|| a.getElementsByTagName('IMG')[0] || a;
	this.thumbsUserSetId = el.id || a.id;
	if (!hs.fireEvent(this, 'onInit')) return;
	
	// check if already open
	for (var i = 0; i < hs.expanders.length; i++) {
		if (hs.expanders[i] && hs.expanders[i].a == a) {
			hs.expanders[i].focus();
			return false;
		}		
	}	
	// cancel other
	for (var i = 0; i < hs.expanders.length; i++) {
		if (hs.expanders[i] && hs.expanders[i].thumb != el && !hs.expanders[i].onLoadStarted) {
			hs.expanders[i].cancelLoading();
		}
	}
	hs.expanders[this.key] = this;
	if (!hs.allowMultipleInstances) {
		try { hs.expanders[key - 1].close(); } catch (e){}
		try { hs.expanders[hs.focusKey].close(); } catch (e){} // preserved
	}
	this.overlays = [];

	var pos = hs.position(el);
	
	// store properties of thumbnail
	this.thumbWidth = el.width ? el.width : el.offsetWidth;		
	this.thumbHeight = el.height ? el.height : el.offsetHeight;
	this.thumbLeft = pos.x;
	this.thumbTop = pos.y;
	this.thumbOffsetBorderW = (this.thumb.offsetWidth - this.thumbWidth) / 2;
	this.thumbOffsetBorderH = (this.thumb.offsetHeight - this.thumbHeight) / 2;
	
	// instanciate the wrapper
	this.wrapper = hs.createElement(
		'div',
		{
			id: 'highslide-wrapper-'+ this.key,
			className: this.wrapperClassName
		},
		{
			visibility: 'hidden',
			position: 'absolute',
			zIndex: hs.zIndexCounter++
		}, null, true );
	
	this.wrapper.onmouseover = function (e) { 
		try { hs.expanders[key].wrapperMouseHandler(e); } catch (e) {} 
	};
	this.wrapper.onmouseout = function (e) { 
		try { hs.expanders[key].wrapperMouseHandler(e); } catch (e) {}
	};
	if (this.contentType == 'image' && this.outlineWhileAnimating == 2)
		this.outlineWhileAnimating = 0;
	// get the outline
	if (hs.pendingOutlines[this.outlineType]) {
		this.connectOutline();
		this[this.contentType +'Create']();
	} else if (!this.outlineType) {
		this[this.contentType +'Create']();
	} else {
		this.displayLoading();
		var exp = this;
		new hs.Outline(this.outlineType, 
			function () { 
				exp.connectOutline();
				exp[exp.contentType +'Create']();
			} 
		);
	}
};

hs.Expander.prototype = {

connectOutline : function(x, y) {	
	var w = hs.pendingOutlines[this.outlineType];
	this.objOutline = w;
	w.table.style.zIndex = this.wrapper.style.zIndex;
	hs.pendingOutlines[this.outlineType] = null;
},

displayLoading : function() {
	if (this.onLoadStarted || this.loading) return;
		
	this.originalCursor = this.a.style.cursor;
	this.a.style.cursor = 'wait';
	
	this.loading = hs.loading;
	var exp = this;
	this.loading.onclick = function() {
		exp.cancelLoading();
	};
	
	
	if (!hs.fireEvent(this, 'onShowLoading')) return;
	this.loading.style.top = (this.thumbTop 
		+ (this.thumbHeight - this.loading.offsetHeight) / 2) +'px';
	var exp = this, left = (this.thumbLeft + this.thumbOffsetBorderW 
		+ (this.thumbWidth - this.loading.offsetWidth) / 2) +'px';
	setTimeout(function () { if (exp.loading) exp.loading.style.left = left }, 100); 
},

imageCreate : function() {
	var exp = this;
	
	var img = document.createElement('img');
    this.content = img;
    img.onload = function () {
    	if (hs.expanders[exp.key]) exp.contentLoaded(); 
	};
    img.className = 'highslide-image';
    img.style.visibility = 'hidden'; // prevent flickering in IE
    img.style.display = 'block';
	img.style.position = 'absolute';
	img.style.maxWidth = 'none';
    img.style.zIndex = 3;
    img.title = hs.restoreTitle;
    if (hs.safari) hs.container.appendChild(img);
    // uncomment this to flush img size:
    // if (hs.ie) img.src = null;
	img.src = hs.getSrc(this.a);
	
	this.displayLoading();
},

htmlCreate : function () {
	if (!hs.fireEvent(this, 'onBeforeGetContent')) return;
	this.tempContainer = hs.createElement('div', { className: this.wrapperClassName },
		{
			padding: '0 '+ hs.marginRight +'px 0 '+ hs.marginLeft +'px',
			visibility: 'hidden'
		}, hs.container
	);
	
	this.content = hs.getCacheBinding(this.a);
	if (!this.content) 
		this.content = hs.getNode(this.contentId);
	if (!this.content) 
		this.content = hs.getSelfRendered();
	
	hs.fireEvent(this, 'onAfterGetContent');
	this.innerContent = this.content;
	
	if (this.swfObject || this.objectType == 'iframe') this.setObjContainerSize(this.innerContent);
	this.tempContainer.appendChild(this.innerContent); // to get full width
	hs.setStyles (this.innerContent, { position: 'relative', visibility: 'hidden' });
	this.innerContent.className += ' highslide-display-block';
	if (this.width) this.innerContent.style.width = this.width+'px';
	if (this.height) this.innerContent.style.height = this.height+'px';
	if (this.innerContent.offsetWidth < this.minWidth)
		this.innerContent.style.width = this.minWidth +'px';
	
	this.content = hs.createElement(
    	'div',
    	{	className: 'highslide-html' },
		{
			position: 'relative',
			zIndex: 3,
			overflow: 'hidden',
			width: this.thumbWidth +'px',
			height: this.thumbHeight +'px'
		}
	);
    
	if (this.objectType == 'ajax' && !hs.getCacheBinding(this.a)) {
    	var ajax = new hs.Ajax(this.a, this.innerContent);
    	var exp = this;
    	ajax.onLoad = function () {	if (hs.expanders[exp.key]) exp.contentLoaded(); };
    	ajax.onError = function () { location.href = hs.getSrc(this.a); };
    	ajax.run();
	}
    else
    
    if (this.objectType == 'iframe' && this.objectLoadTime == 'before') {
		this.writeExtendedContent();
	}
    else
    	this.contentLoaded();
},

contentLoaded : function() {
	try {	
		if (!this.content) return;
		if (this.onLoadStarted) return; // old Gecko loop
		else this.onLoadStarted = true;
		
			   
		if (this.loading) {
			this.loading.style.left = '-9999px';
			this.loading = null;
			this.a.style.cursor = this.originalCursor || '';
			hs.fireEvent(this, 'onHideLoading');
		}
		this.marginBottom = hs.marginBottom;
		if (this.isImage) {	
			this.newWidth = this.content.width;
			this.newHeight = this.content.height;
			this.fullExpandWidth = this.newWidth;
			this.fullExpandHeight = this.newHeight;
			
			this.content.style.width = this.thumbWidth +'px';
			this.content.style.height = this.thumbHeight +'px';
			this.getCaption();
		} else if (this.htmlGetSize) this.htmlGetSize();	
		
		
		this.wrapper.appendChild(this.content);
		this.content.style.position = 'relative'; // Saf
		if (this.caption) this.wrapper.appendChild(this.caption);
		this.wrapper.style.left = this.thumbLeft +'px';
		this.wrapper.style.top = this.thumbTop +'px';
		hs.container.appendChild(this.wrapper);
		
		// correct for borders
		this.offsetBorderW = (this.content.offsetWidth - this.thumbWidth) / 2;
		this.offsetBorderH = (this.content.offsetHeight - this.thumbHeight) / 2;
		var modMarginRight = hs.marginRight + 2 * this.offsetBorderW;
		this.marginBottom += 2 * this.offsetBorderH;
		
		var ratio = this.newWidth / this.newHeight;
		var minWidth = this.allowSizeReduction 
			? this.minWidth : this.newWidth;
		var minHeight = this.allowSizeReduction 
			? this.minHeight : this.newHeight;
		
		var justify = { x: 'auto', y: 'auto' };
		
		if (this.align == 'center') {
			justify.x = 'center';
			justify.y = 'center';
		} else {
			if (this.anchor.match(/^top/)) justify.y = null;
			if (this.anchor.match(/right$/)) justify.x = 'max';
			if (this.anchor.match(/^bottom/)) justify.y = 'max';
			if (this.anchor.match(/left$/)) justify.x = null;
		}
		
		var page = hs.getPageSize();
		// justify
		this.x = { 
			min: parseInt(this.thumbLeft) - this.offsetBorderW + this.thumbOffsetBorderW,
			span: this.newWidth,
			minSpan: (this.newWidth < minWidth && !hs.padToMinWidth) 
				? this.newWidth : minWidth,
			justify: justify.x,
			target: this.targetX,
			marginMin: hs.marginLeft, 
			marginMax: modMarginRight,
			scroll: page.scrollLeft,
			clientSpan: page.width,
			thumbSpan: this.thumbWidth
		};
		var oldRight = this.x.min + parseInt(this.thumbWidth);
		this.x = this.justify(this.x);
		this.y = { 
			min: parseInt(this.thumbTop) - this.offsetBorderH + this.thumbOffsetBorderH,
			span: this.newHeight,
			minSpan: this.newHeight < minHeight ? this.newHeight : minHeight,
			justify: justify.y,
			target: this.targetY,
			marginMin: hs.marginTop, 
			marginMax: this.marginBottom, 
			scroll: page.scrollTop,
			clientSpan: page.height,
			thumbSpan: this.thumbHeight
		};
		var oldBottom = this.y.min + parseInt(this.thumbHeight);
		this.y = this.justify(this.y);
		if (this.isHtml) this.htmlSizeOperations();
		
		if (this.isImage)
			this.correctRatio(ratio);
		

		var x = this.x;
		var y = this.y;
		
		this.show();
	} catch (e) {
		window.location.href = hs.getSrc(this.a);
	}
},


setObjContainerSize : function(parent, auto) {
	var c = hs.getElementByClass(parent, 'DIV', 'highslide-body');
	
	if (this.objectType == 'iframe') {
		if (this.objectWidth) c.style.width = this.objectWidth +'px';
		if (this.objectHeight) c.style.height = this.objectHeight +'px';
	}
	if (this.swfObject) {
		c.style.width = this.swfObject.attributes.width +'px';
		c.style.height = this.swfObject.attributes.height +'px';
	}
},

writeExtendedContent : function (loadTime) {
	if (this.hasExtendedContent) return;
	var exp = this;
	this.body = hs.getElementByClass(this.innerContent, 'DIV', 'highslide-body');
	if (this.objectType == 'iframe') {
		this.displayLoading();
		this.ruler = hs.clearing.cloneNode(1);
		this.body.appendChild(this.ruler);
		this.newWidth = this.innerContent.offsetWidth;
		if (!this.objectWidth) this.objectWidth = this.ruler.offsetWidth;
		var hDiff = this.innerContent.offsetHeight - this.body.offsetHeight;
		var h = this.objectHeight || (hs.getPageSize()).height - hDiff - hs.marginTop - hs.marginBottom;
		var onload = (hs.ie && this.objectLoadTime == 'before') ? 
			'onload="if(hs.expanders['+ this.key +'])hs.expanders['+ this.key +'].contentLoaded();"' : '';
		var tag = hs.ie ? '<iframe name="hsIframe'+ this.key+ '" '+ onload +'/>' : 'iframe';
		this.iframe = hs.createElement(tag, 
			{ name: 'hsIframe'+ this.key, frameBorder: 0, allowTransparency: true },
			{ width: this.objectWidth +'px', height: h +'px' }, 
			this.body);
		if (this.objectLoadTime == 'before') {
			if (!hs.ie) this.iframe.onload = function () { if (hs.expanders[exp.key]) exp.contentLoaded(); };
		}
		if (hs.safari) this.iframe.src = null;
		this.iframe.src = hs.getSrc(this.a);
		
		if (this.objectLoadTime == 'after') this.correctIframeSize();
		
	} else if (this.swfObject) {	
		this.body.id = this.body.id || 'hs-flash-id-' + this.key;
		this.swfObject.write(this.body.id);	
	}
	this.hasExtendedContent = true;	
},
htmlGetSize : function() {
	if (this.iframe && !this.objectHeight) { // loadtime before
		try {
			var doc = this.iframe.contentDocument || this.iframe.contentWindow.document;
			var clearing = doc.createElement('div');
			clearing.style.clear = 'both';
			doc.body.appendChild(clearing);
			var h = clearing.offsetTop;
			if (hs.ie) h += parseInt(doc.body.currentStyle.marginTop) 
				+ parseInt(doc.body.currentStyle.marginBottom) - 1;		
			this.iframe.style.height = this.body.style.height = h +'px';
		} catch (e) { // other domain
			this.iframe.style.height = '300px';
		}
	}
	this.innerContent.appendChild(hs.clearing);
	if (!this.newWidth) this.newWidth = this.innerContent.offsetWidth;
    this.newHeight = this.innerContent.offsetHeight;
    this.innerContent.removeChild(hs.clearing);
    if (hs.ie && this.newHeight > parseInt(this.innerContent.currentStyle.height)) { // ie css bug
		this.newHeight = parseInt(this.innerContent.currentStyle.height);
	}
},

correctIframeSize : function () {
	var wDiff = this.innerContent.offsetWidth - this.ruler.offsetWidth;
	if (wDiff < 0) wDiff = 0;
	
	var hDiff = this.innerContent.offsetHeight - this.body.offsetHeight;
	
    hs.setStyles(this.iframe, { width: (this.x.span - wDiff) +'px', 
		height: (this.y.span - hDiff) +'px' });
    hs.setStyles(this.body, { width: this.iframe.style.width, 
    	height: this.iframe.style.height });
    	
    this.scrollingContent = this.iframe;
    this.scrollerDiv = this.scrollingContent;
},
htmlSizeOperations : function () {
	
	this.setObjContainerSize(this.innerContent);
	
	
	if (this.swfObject && this.objectLoadTime == 'before') this.writeExtendedContent();	
	
	
    // handle minimum size
    if (this.x.span < this.newWidth && !this.allowWidthReduction) this.x.span = this.newWidth;
    if (this.y.span < this.newHeight && !this.allowHeightReduction) this.y.span = this.newHeight;
    this.scrollerDiv = this.innerContent;
    this.mediumContent = hs.createElement('div', null, 
    	{ 
    		width: this.x.span +'px',
    		position: 'relative',
    		left: (this.x.min - this.thumbLeft) +'px',
    		top: (this.y.min - this.thumbTop) +'px'
    	}, this.content, true);
	
    this.mediumContent.appendChild(this.innerContent);
    hs.container.removeChild(this.tempContainer);
    hs.setStyles(this.innerContent, { border: 'none', width: 'auto', height: 'auto' });
    
    var node = hs.getElementByClass(this.innerContent, 'DIV', 'highslide-body');
    if (node && !this.swfObject && this.objectType != 'iframe') {    
    	var cNode = node; // wrap to get true size
    	node = hs.createElement(cNode.nodeName, null, {overflow: 'hidden'}, null, true);
    	cNode.parentNode.insertBefore(node, cNode);
    	node.appendChild(hs.clearing); // IE6
    	node.appendChild(cNode);
    	
    	var wDiff = this.innerContent.offsetWidth - node.offsetWidth;
    	var hDiff = this.innerContent.offsetHeight - node.offsetHeight;
    	node.removeChild(hs.clearing);
    	
    	var kdeBugCorr = hs.safari || navigator.vendor == 'KDE' ? 1 : 0; // KDE repainting bug
    	hs.setStyles(node, { 
    			width: (this.x.span - wDiff - kdeBugCorr) +'px', 
    			height: (this.y.span - hDiff) +'px',
    			overflow: 'auto', 
    			position: 'relative' 
    		} 
    	);
		if (kdeBugCorr && cNode.offsetHeight > node.offsetHeight)	{
    		node.style.width = (parseInt(node.style.width) + kdeBugCorr) + 'px';
		}
    	this.scrollingContent = node;
    	this.scrollerDiv = this.scrollingContent;
    	
	}
    if (this.iframe && this.objectLoadTime == 'before') this.correctIframeSize();
    if (!this.scrollingContent && this.y.span < this.mediumContent.offsetHeight) this.scrollerDiv = this.content;
	
	if (this.scrollerDiv == this.content && !this.allowWidthReduction && this.objectType != 'iframe') {
		this.x.span += 17; // room for scrollbars
	}
	if (this.scrollerDiv && this.scrollerDiv.offsetHeight > this.scrollerDiv.parentNode.offsetHeight) {
		setTimeout("try { hs.expanders["+ this.key +"].scrollerDiv.style.overflow = 'auto'; } catch(e) {}",
			 hs.expandDuration);
	}
},

justify : function (p) {
	
	var tgt, dim = p == this.x ? 'x' : 'y';
	
	
	if (p.target && p.target.match(/ /)) {
		tgt = p.target.split(' ');
		p.target = tgt[0];
	}
	if (p.target && hs.$(p.target)) {
		p.min = hs.position(hs.$(p.target))[dim];
		if (tgt && tgt[1] && tgt[1].match(/^[-]?[0-9]+px$/)) p.min += parseInt(tgt[1]);
		
	} else if (p.justify == 'auto' || p.justify == 'center') {
	
		var hasMovedMin = false;
		
		var allowReduce = true;
		
		// calculate p.min
		if (p.justify == 'center') p.min = Math.round(p.scroll + (p.clientSpan - p.span - p.marginMax) / 2);
		else
		p.min = Math.round(p.min - ((p.span - p.thumbSpan) / 2)); // auto
		
		if (p.min < p.scroll + p.marginMin) {
			p.min = p.scroll + p.marginMin;
			hasMovedMin = true;		
		}
	
		
		if (p.span < p.minSpan) {
			p.span = p.minSpan;
			allowReduce = false;			
		}
		
		// calculate right/newWidth
		if (p.min + p.span > p.scroll + p.clientSpan - p.marginMax) {
			if (hasMovedMin && allowReduce) {
				
				p.span = p.clientSpan - p.marginMin - p.marginMax; // can't expand more
				
			} else if (p.span < p.clientSpan - p.marginMin - p.marginMax) { // move newTop up
				p.min = p.scroll + p.clientSpan - p.span - p.marginMin - p.marginMax;
			} else { // image larger than client
				p.min = p.scroll + p.marginMin;
				
				if (allowReduce) p.span = p.clientSpan - p.marginMin - p.marginMax;
				
			}
			
		}
		
		if (p.span < p.minSpan) {
			p.span = p.minSpan;
			allowReduce = false;
		}
		
	
	} else if (p.justify == 'max') {
		p.min = Math.floor(p.min - p.span + p.thumbSpan);
	}
	
		
	if (p.min < p.marginMin) {
		tmpMin = p.min;
		p.min = p.marginMin; 
		
		if (allowReduce) p.span = p.span - (p.min - tmpMin);
		
	}
	return p;
},

correctRatio : function(ratio) {
	var x = this.x;
	var y = this.y;
	var changed = false;
	if (x.span / y.span > ratio) { // width greater
		var tmpWidth = x.span;
		x.span = y.span * ratio;
		if (x.span < x.minSpan) { // below minWidth
			if (hs.padToMinWidth) x.imgSpan = x.span;			
			x.span = x.minSpan;
			if (!x.imgSpan)
			y.span = x.span / ratio;
		}
		changed = true;
	
	} else if (x.span / y.span < ratio) { // height greater
		var tmpHeight = y.span;
		y.span = x.span / ratio;
		changed = true;
	}
	
	if (changed) {
		x.min = parseInt(this.thumbLeft) - this.offsetBorderW + this.thumbOffsetBorderW;
		x.minSpan = x.span;
		this.x = this.justify(x);
		
		y.min = parseInt(this.thumbTop) - this.offsetBorderH + this.thumbOffsetBorderH;
		y.minSpan = y.span;
		this.y = this.justify(y);
	}
},

reflow : function () {
	hs.setStyles(this.scrollerDiv, { height: 'auto', width: 'auto' });
	this.x.span = this.innerContent.offsetWidth;
	this.y.span = this.innerContent.offsetHeight;
	var size = { width: this.x.span +'px', height: this.y.span +'px' };
	hs.setStyles(this.content, size);
	if (this.objOutline)
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
},

show : function () {
	
	// Selectbox bug
	var imgPos = {x: this.x.min - 20, y: this.y.min - 20, w: this.x.span + 40, 
		h: this.y.span + 40
		 + this.spaceForCaption};
	hs.hideSelects = (hs.ie && hs.ieVersion() < 7);
	if (hs.hideSelects) this.showHideElements('SELECT', 'hidden', imgPos);
	// Iframes bug
	hs.hideIframes = ((window.opera && navigator.appVersion < 9) || navigator.vendor == 'KDE' 
		|| (hs.ie && hs.ieVersion() < 5.5));
	if (hs.hideIframes) this.showHideElements('IFRAME', 'hidden', imgPos);
	// Scrollbars bug
	if (hs.geckoMac) this.showHideElements('*', 'hidden', imgPos); 
	
	
	if (this.x.imgSpan) this.content.style.margin = '0 auto';
	
	hs.fireEvent(this, 'onBeforeExpand');
	
	// Apply size change		
	this.changeSize(
		1,
		{ 
			x: this.thumbLeft + this.thumbOffsetBorderW - this.offsetBorderW,
			y: this.thumbTop + this.thumbOffsetBorderH - this.offsetBorderH,
			w: this.thumbWidth,
			h: this.thumbHeight,
			imgW: this.thumbWidth,
			o: hs.outlineStartOffset
		},
		{
			x: this.x.min,
			y: this.y.min,
			w: this.x.span,
			h: this.y.span,
			imgW: this.x.imgSpan,
			o: this.objOutline ? this.objOutline.offset : 0
		},
		hs.expandDuration,
		hs.expandSteps
	);
},

changeSize : function(up, from, to, dur, steps) {
	// transition
	var trans = this.transitions, 
	t = (trans[1] && hs.upcoming 
			&& hs.getParam(hs.upcoming, 'transitions')[1] == trans[1]) ?
		trans[1] : trans[0];
	if (this[t]) {
		this[t](up, from, to); 
		return;
	}
	
	if (up && this.objOutline && !this.outlineWhileAnimating) 
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
	
	else if (!up && this.objOutline) {
		if (this.outlineWhileAnimating) this.objOutline.setPosition(this, from.x, from.y, from.w, from.h);
		else this.objOutline.destroy(
			(this.isHtml && this.preserveContent));
	}	
			
	if (!up) { // remove children
		var n = this.wrapper.childNodes.length;
		for (var i = n - 1; i >= 0 ; i--) {
			var child = this.wrapper.childNodes[i];
			if (child != this.content) {
				hs.purge(child);
				this.wrapper.removeChild(child);
			}
		}
	}
		
	if (this.fadeInOut) {
		from.op = up ? 0 : 1;
		to.op = up;
	}
	var dT = Math.round(dur/steps),
	exp = this,
	easing = Math[this.easing] || Math.easeInQuad;
	if (!up) easing = Math[this.easingClose] || easing;
	
	for (var i = 1, t = 0; i <= steps; i++, t += dT) {
		(function() {
			var size = {}, pI = i;
			
			for (var x in from) 
				size[x] = easing(t + dT, from[x], to[x] - from[x], dur);
				
			setTimeout ( function() { 
				exp.setSize(size);
				
				if (up && pI == 1) {
					exp.content.style.visibility = 'visible';
					exp.a.className += ' highslide-active-anchor';
				}
				
			}, t);				
		})();
	}
	
	if (up) { 
			
		setTimeout(function() {
			if (exp.objOutline) exp.objOutline.table.style.visibility = "visible";
		}, t);
		setTimeout(function() {
			if (exp.caption) exp.writeCaption();
			exp.afterExpand();
		}, t + 50);
	}
	else setTimeout(function() { exp.afterClose(); }, t);
		
},

fade : function(up, from, to) {
	this.outlineWhileAnimating = false;
	var exp = this,	t = up ? 250 : 0;
	
	if (up) {
		hs.setStyles(this.wrapper, { opacity: 0 });
		this.setSize(to);
		if (this.caption) {
			var ss = hs.captionSlideSpeed;
			hs.captionSlideSpeed = 0;
			this.writeCaption();
			hs.captionSlideSpeed = ss;
		}
		this.content.style.visibility = 'visible';

		hs.fade (this.wrapper, 0, 1);
	}
	
	if (this.objOutline) {
		this.objOutline.table.style.zIndex = this.wrapper.style.zIndex;
		var dir = up || -1;
		for (var i = from.o; dir * i <= dir * to.o; i += dir, t += 25) {
			(function() {
				var o = up ? to.o - i : from.o - i;
				setTimeout(function() {
					exp.objOutline.setPosition(exp, (exp.x.min + o), (exp.y.min + o),
						(exp.x.span - 2 * o), (exp.y.span - 2 * o), 1, t);
				}, t);
			})();
		}
	}
	
	
	if (up) setTimeout(function() { exp.afterExpand(); }, t+50);
	else {
		setTimeout( function() {
			if (exp.objOutline) exp.objOutline.destroy(exp.preserveContent);
			hs.fade (exp.wrapper, 1, 0);
			setTimeout( function() {
				exp.afterClose();
			}, 250);
		}, t);		
	}
},

setSize : function (to) {
	try {
		if (this.isHtml) {
			hs.setStyles(this.content, { width: to.w +'px', height: to.h +'px' });
			hs.setStyles(this.mediumContent, { left: (this.x.min - to.x) +'px', 
				top: (this.y.min - to.y) +'px' });
			this.innerContent.style.visibility = 'visible';
		} else {
			this.wrapper.style.width = (to.w + 2*this.offsetBorderW) +'px';
			this.content.style.width =
				((to.imgW && !isNaN(to.imgW)) ? to.imgW : to.w) +'px';
			if (hs.safari) this.content.style.maxWidth = this.content.style.width;
			this.content.style.height = to.h +'px';
		}
		
		if (to.op) hs.setStyles(this.wrapper, { opacity: to.op });
				
		
		if (this.objOutline && this.outlineWhileAnimating) {
			var o = this.objOutline.offset - to.o;
			this.objOutline.setPosition(this, to.x + o, to.y + o, to.w - 2 * o, to.h - 2 * o, 1);
		}
				
		hs.setStyles ( this.wrapper,
			{
				'visibility': 'visible',
				'left': to.x +'px',
				'top': to.y +'px'
			}
		);
		
	} catch (e) { window.location.href = hs.getSrc(this.a);	}
},

afterExpand : function() {
	this.isExpanded = true;	
	this.focus();
	
	if (this.isHtml && this.objectLoadTime == 'after') this.writeExtendedContent();
	
	if (this.isHtml) {
		if (this.iframe) {
			try {
				var exp = this,
					doc = this.iframe.contentDocument || this.iframe.contentWindow.document;
				hs.addEventListener(doc, 'mousedown', function () {
					if (hs.focusKey != exp.key) exp.focus();
				});
			} catch(e) {}
			if (hs.ie && typeof this.isClosing != 'boolean') // first open 
				this.iframe.style.width = (this.objectWidth - 1) +'px'; // hasLayout
		}
	}
	
	this.createOverlays();
	if (hs.showCredits) this.writeCredits();
	if (this.fullExpandWidth > this.x.span) this.createFullExpand();
	if (this.dimmingOpacity) hs.dim(this);
	if (hs.upcoming == this.a) hs.upcoming = null;
	if (!this.caption) this.prepareNextOutline();
	if (!this.caption) hs.fireEvent(this, 'onAfterExpand');
},


prepareNextOutline : function() {
	var key = this.key;
	var outlineType = this.outlineType;
	new hs.Outline(outlineType, 
		function () { try { hs.expanders[key].preloadNext(); } catch (e) {} });
},


preloadNext : function() {
	var next = hs.getAdjacentAnchor(this.key, 1);	
	if (next.onclick.toString().match(/hs\.expand/)) 
		var img = hs.createElement('img', { src: hs.getSrc(next) });
},

cancelLoading : function() {	
	hs.expanders[this.key] = null;
	if (hs.upcoming == this.a) hs.upcoming = null;
	hs.unDim();
	this.a.style.cursor = this.originalCursor;	
	if (this.loading) hs.loading.style.left = '-9999px';
	hs.fireEvent(this, 'onHideLoading');
},

writeCredits : function () {
	var credits = hs.createElement('a',
		{
			href: hs.creditsHref,
			className: 'highslide-credits',
			innerHTML: hs.creditsText,
			title: hs.creditsTitle
		}
	);
	this.createOverlay({ overlayId: credits, position: 'top left'});
},

getCaption : function() {
	if (!hs.fireEvent(this, 'onBeforeGetCaption')) return;
	if (!this.captionId && this.thumbsUserSetId)  
		this.captionId = 'caption-for-'+ this.thumbsUserSetId;
	if (this.captionId) this.caption = hs.getNode(this.captionId);
	if (!this.caption && !this.captionText && this.captionEval) try {
		this.captionText = eval(this.captionEval);
	} catch (e) {}
	if (!this.caption && this.captionText) this.caption = hs.createElement('div', 
			{ className: 'highslide-caption', innerHTML: this.captionText } );
	
	if (!this.caption) {
		var next = this.a.nextSibling;
		while (next && !hs.isHsAnchor(next)) {
			if (/highslide-caption/.test(next.className)) {
				this.caption = next.cloneNode(1);
				break;
			}
			next = next.nextSibling;
		}
	}
	if (this.caption) {
		this.marginBottom += this.spaceForCaption;
		hs.fireEvent(this, 'onAfterGetCaption');		
	}
	
},

writeCaption : function() {
	try {
		hs.setStyles(this.wrapper, { width: this.wrapper.offsetWidth +'px', 
			height: this.wrapper.offsetHeight +'px' } );	
		hs.setStyles(this.caption, { visibility: 'hidden', marginTop: hs.safari ? 0 : '-'+ this.y.span +'px'});
		this.caption.className += ' highslide-display-block';
		
		var height, exp = this;
		if (hs.ie && (hs.ieVersion() < 6 || document.compatMode == 'BackCompat')) {
			height = this.caption.offsetHeight;
		} else {
			var temp = hs.createElement('div', {innerHTML: this.caption.innerHTML}, 
				null, null, true); // to get height
			this.caption.innerHTML = '';
			this.caption.appendChild(temp);	
			height = this.caption.childNodes[0].offsetHeight;
			this.caption.innerHTML = this.caption.childNodes[0].innerHTML;
		}
		hs.setStyles(this.caption, { overflow: 'hidden', height: 0, zIndex: 2, marginTop: 0 });
		this.wrapper.style.height = 'auto';
		
		if (hs.captionSlideSpeed) {
			var step = (Math.round(height/50) || 1) * hs.captionSlideSpeed;
		} else {
			this.placeCaption(height, 1);
			return;
		}
		for (var h = height % step, t = 0; h <= height; h += step, t += 10) {
			(function(){
				var pH = h, end = (h == height) ? 1 : 0;
				setTimeout( function() {
					exp.placeCaption(pH, end);
				}, t);
			})();
		}
	} catch (e) {}	
},

placeCaption : function(height, end) {
	if (!this.caption) return;
	this.caption.style.height = height +'px';
	this.caption.style.visibility = 'visible';
	this.y.span = this.wrapper.offsetHeight - 2 * this.offsetBorderH;
	
	
	var o = this.objOutline;
	if (o) {
		o.td[4].style.height = (this.wrapper.offsetHeight - 2 * this.objOutline.offset) +'px';
		if (o.hasAlphaImageLoader) o.td[3].style.height = o.td[5].style.height = o.td[4].style.height;
	}
	if (end) this.prepareNextOutline();
	
	if (end) hs.fireEvent(this, 'onAfterExpand');
},


showHideElements : function (tagName, visibility, imgPos) {
	var els = document.getElementsByTagName(tagName);
	var prop = tagName == '*' ? 'overflow' : 'visibility';
	for (var i = 0; i < els.length; i++) {
		if (prop == 'visibility' || (document.defaultView.getComputedStyle(
				els[i], "").getPropertyValue('overflow') == 'auto'
				|| els[i].getAttribute('hidden-by') != null)) {
			var hiddenBy = els[i].getAttribute('hidden-by');
			if (visibility == 'visible' && hiddenBy) {
				hiddenBy = hiddenBy.replace('['+ this.key +']', '');
				els[i].setAttribute('hidden-by', hiddenBy);
				if (!hiddenBy) els[i].style[prop] = els[i].origProp;
			} else if (visibility == 'hidden') { // hide if behind
				var elPos = hs.position(els[i]);
				elPos.w = els[i].offsetWidth;
				elPos.h = els[i].offsetHeight;
				if (!this.dimmingOpacity) { // hide all if dimming
				
					var clearsX = (elPos.x + elPos.w < imgPos.x || elPos.x > imgPos.x + imgPos.w);
					var clearsY = (elPos.y + elPos.h < imgPos.y || elPos.y > imgPos.y + imgPos.h);
				}
				var wrapperKey = hs.getWrapperKey(els[i]);
				if (!clearsX && !clearsY && wrapperKey != this.key) { // element falls behind image
					if (!hiddenBy) {
						els[i].setAttribute('hidden-by', '['+ this.key +']');
						els[i].origProp = els[i].style[prop];
						els[i].style[prop] = 'hidden';
					} else if (!hiddenBy.match('['+ this.key +']')) {
						els[i].setAttribute('hidden-by', hiddenBy + '['+ this.key +']');
					}
				} else if (hiddenBy == '['+ this.key +']' || hs.focusKey == wrapperKey) { // on move
					els[i].setAttribute('hidden-by', '');
					els[i].style[prop] = els[i].origProp || '';
				} else if (hiddenBy && hiddenBy.match('['+ this.key +']')) {
					els[i].setAttribute('hidden-by', hiddenBy.replace('['+ this.key +']', ''));
				}
						
			}
		}
	}
},

focus : function() {
	this.wrapper.style.zIndex = hs.zIndexCounter++;
	// blur others
	for (var i = 0; i < hs.expanders.length; i++) {
		if (hs.expanders[i] && i == hs.focusKey) {
			var blurExp = hs.expanders[i];
			blurExp.content.className += ' highslide-'+ blurExp.contentType +'-blur';
			
			if (blurExp.caption) {
				blurExp.caption.className += ' highslide-caption-blur';
			}
			
			if (blurExp.isImage) {
				blurExp.content.style.cursor = hs.ie ? 'hand' : 'pointer';
				blurExp.content.title = hs.focusTitle;	
			}	
			hs.fireEvent(this, 'onBlur');
		}
	}
	
	// focus this
	if (this.objOutline) this.objOutline.table.style.zIndex 
		= this.wrapper.style.zIndex;
	
	this.content.className = 'highslide-'+ this.contentType;
	
	if (this.caption) {
		this.caption.className = this.caption.className.replace(' highslide-caption-blur', '');
	}
	
	if (this.isImage) {
		this.content.title = hs.restoreTitle;
		
		hs.styleRestoreCursor = window.opera ? 'pointer' : 'url('+ hs.graphicsDir + hs.restoreCursor +'), pointer';
		if (hs.ie && hs.ieVersion() < 6) hs.styleRestoreCursor = 'hand';
		this.content.style.cursor = hs.styleRestoreCursor;
	}
	hs.focusKey = this.key;	
	hs.addEventListener(document, 'keydown', hs.keyHandler);	
	hs.fireEvent(this, 'onFocus');	
},

move : function (e) {
	this.x.min = e.left + e.dX;
	this.y.min = e.top + e.dY;
	
	hs.setStyles(this.wrapper, { left: this.x.min +'px', top: this.y.min +'px' });
	
	if (this.objOutline)
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
	
},

resize : function (e) {
	this.x.span = e.width + e.dX;
	this.y.span = e.height + e.dY;
	
	if (this.x.span < this.minWidth) this.x.span = this.minWidth;
	if (this.y.span < this.minHeight) this.y.span = this.minHeight;
	
	var d = this.scrollerDiv;	
	if (typeof this.wDiff == 'undefined') {
		this.wDiff = this.innerContent.offsetWidth - d.offsetWidth;
		this.hDiff = this.innerContent.offsetHeight - d.offsetHeight;
	}
	hs.setStyles(d, { width: (this.x.span - this.wDiff) +'px', 
		height: (this.y.span - this.hDiff) +'px' });
			
	var size = { width: this.x.span +'px', height: this.y.span +'px' };
	hs.setStyles(this.content, size);
	if (this.releaseMask) hs.setStyles(this.releaseMask, size);
	
	this.mediumContent.style.width = 'auto';
	hs.setStyles(this.body, { width: 'auto', height: 'auto' });
	
	
	for (var i = 0; i < this.overlays.length; i++) 
		this.positionOverlay(this.overlays[i]);
	if (this.objOutline)
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
	
},

close : function() {
	if (this.isClosing || !this.isExpanded) return;
	this.isClosing = true;
	if (!hs.fireEvent(this, 'onBeforeClose')) return;
	
	hs.removeEventListener(document, 'keydown', hs.keyHandler);
	
	try {
		if (this.isHtml) this.htmlPrepareClose();
		
		this.content.style.cursor = 'default';
		
		this.changeSize(
			0,
			{
				x: this.x.min,
				y: this.y.min,
				w: this.x.span,
				h: parseInt(this.content.style.height),
				imgW: this.x.imgSpan,
				o: this.objOutline ? this.objOutline.offset : 0
			},
			{
				x: this.thumbLeft - this.offsetBorderW + this.thumbOffsetBorderW,
				y: this.thumbTop - this.offsetBorderH + this.thumbOffsetBorderH,
				w: this.thumbWidth,
				h: this.thumbHeight,
				imgW: this.thumbWidth,
				o: hs.outlineStartOffset
			},
			hs.restoreDuration,
			hs.restoreSteps
		);
		
	} catch (e) { this.afterClose(); } 
},

htmlPrepareClose : function() {
	if (hs.geckoMac) { // bad redraws
		if (!hs.mask) hs.mask = hs.createElement('div', null, 
			{ position: 'absolute' }, hs.container);
		hs.setStyles(hs.mask, { width: this.x.span +'px', height: this.y.span +'px', 
			left: this.x.min +'px', top: this.y.min +'px', display: 'block' });			
	}
	if (this.swfObject) try { hs.$(this.swfObject.getAttribute('id')).StopPlay(); } catch (e) {}
	
	if (this.objectLoadTime == 'after' && !this.preserveContent) this.destroyObject();		
	if (this.scrollerDiv && this.scrollerDiv != this.scrollingContent) 
		this.scrollerDiv.style.overflow = 'hidden';
},

destroyObject : function () {
	if (hs.ie && this.iframe)
		try { this.iframe.contentWindow.document.body.innerHTML = ''; } catch (e) {}	
	this.body.innerHTML = '';
},

sleep : function() {
	if (this.objOutline) this.objOutline.table.className = 'highslide-display-none';
	this.releaseMask = null;
	this.wrapper.className += ' highslide-display-none';
	hs.push(hs.sleeping, this);
},

awake : function() {
	hs.expanders[this.key] = this;
	
	if (!hs.allowMultipleInstances &&hs.focusKey != this.key) {	
		try { hs.expanders[hs.focusKey].close(); } catch (e){}
	}
	
	this.wrapper.className = this.wrapper.className.replace(/highslide-display-none/, '');
	var z = hs.zIndexCounter++;
	this.wrapper.style.zIndex = z;
	this.isClosing = false;
	
	if (o = this.objOutline) {
		if (!this.outlineWhileAnimating) o.table.style.visibility = 'hidden';
		o.table.className = null;
		o.table.style.zIndex = z;
	}
	this.show();
},

createOverlay : function (o) {
	var el = o.overlayId;
	if (typeof el == 'string') el = hs.getNode(el);
	if (!el || typeof el == 'string') return;
	
	
	if (!hs.fireEvent(this, 'onCreateOverlay', { overlay: el })) return;
	
	var overlay = hs.createElement(
		'div',
		null,
		{
			'left' : 0,
			'top' : 0,
			'position' : 'absolute',
			'zIndex' : 3,
			'visibility' : 'hidden'
		},
		this.wrapper,
		true
	);
	if (o.opacity) hs.setStyles(el, { opacity: o.opacity });
	el.className += ' highslide-display-block';
	overlay.appendChild(el);	
	
	overlay.hsPos = o.position;
	this.positionOverlay(overlay);	
	
	if (o.hideOnMouseOut) overlay.setAttribute('hideOnMouseOut', true);
	if (!o.opacity) o.opacity = 1;
	overlay.setAttribute('opacity', o.opacity);
	hs.fade(overlay, 0, o.opacity);
	
	hs.push(this.overlays, overlay);
},

positionOverlay : function(overlay) {
	var left = this.offsetBorderW;
	var dLeft = this.x.span - overlay.offsetWidth;
	var top = this.offsetBorderH;
	var dTop = parseInt(this.content.style.height) - overlay.offsetHeight;
	
	var p = overlay.hsPos || 'center center';
	if (/^bottom/.test(p)) top += dTop;
	if (/^center/.test(p)) top += dTop / 2;
	if (/right$/.test(p)) left += dLeft;
	if (/center$/.test(p)) left += dLeft / 2;
	overlay.style.left = left +'px';
	overlay.style.top = top +'px';
},

createOverlays : function() {
	for (var i = 0; i < hs.overlays.length; i++) {
		var o = hs.overlays[i];
		if ((!o.thumbnailId && !o.slideshowGroup) || o.thumbnailId == this.thumbsUserSetId
				|| o.slideshowGroup === this.slideshowGroup) {
			if (this.isImage || (this.isHtml && o.useOnHtml))
			this.createOverlay(o);
		}
	}
},


createFullExpand : function () {
	var a = hs.createElement(
		'a',
		{
			href: 'javascript:hs.expanders['+ this.key +'].doFullExpand();',
			title: hs.fullExpandTitle,
			className: 'highslide-full-expand'
		}
	);
	
	this.fullExpandLabel = a;
	if (!hs.fireEvent(this, 'onCreateFullExpand')) return;
	this.createOverlay({ overlayId: a, position: hs.fullExpandPosition, 
		hideOnMouseOut: true, opacity: hs.fullExpandOpacity });
},

doFullExpand : function () {
	try {
		if (!hs.fireEvent(this, 'onDoFullExpand')) return;	
		hs.purge(this.fullExpandLabel);
		this.fullExpandLabel.parentNode.removeChild(this.fullExpandLabel);
		this.focus();
		
		this.x.min = parseInt(this.wrapper.style.left) - (this.fullExpandWidth - this.content.width) / 2;
		if (this.x.min < hs.marginLeft) this.x.min = hs.marginLeft;		
		this.wrapper.style.left = this.x.min +'px';
		
		hs.setStyles(this.content, { width: this.fullExpandWidth +'px', 
			height: this.fullExpandHeight +'px'});
		
		this.x.span = this.fullExpandWidth;
		this.wrapper.style.width = (this.x.span + 2*this.offsetBorderW) +'px';
		
		this.y.span = this.wrapper.offsetHeight - 2 * this.offsetBorderH;
		
		if (this.objOutline)
			this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
		
		for (var i = 0; i < this.overlays.length; i++)
			this.positionOverlay(this.overlays[i]);
		
		this.redoShowHide();
		
		hs.setDimmerSize();
		
	
	} catch (e) {
		window.location.href = this.content.src;
	}
},


// on end move and resize
redoShowHide : function() {
	var imgPos = {
		x: parseInt(this.wrapper.style.left) - 20, 
		y: parseInt(this.wrapper.style.top) - 20, 
		w: this.content.offsetWidth + 40, 
		h: this.content.offsetHeight + 40 
			+ this.spaceForCaption
	};
	if (hs.hideSelects) this.showHideElements('SELECT', 'hidden', imgPos);
	if (hs.hideIframes) this.showHideElements('IFRAME', 'hidden', imgPos);
	if (hs.geckoMac) this.showHideElements('*', 'hidden', imgPos);

},

wrapperMouseHandler : function (e) {
	if (!e) e = window.event;
	var over = /mouseover/i.test(e.type); 
	if (!e.target) e.target = e.srcElement; // ie
	if (!e.relatedTarget) e.relatedTarget = 
		over ? e.fromElement : e.toElement; // ie
	if (hs.getExpander(e.relatedTarget) == this || hs.dragArgs) return;
	if (this.isExpanded) hs.fireEvent(this, over ? 'onMouseOver' : 'onMouseOut', e);
	for (var i = 0; i < this.overlays.length; i++) {
		var o = this.overlays[i];
		if (o.getAttribute('hideOnMouseOut')) {
			var from = over ? 0 : o.getAttribute('opacity'),
				to = over ? o.getAttribute('opacity') : 0;			
			hs.fade(o, from, to);
		}
	}
},

afterClose : function () {
	this.a.className = this.a.className.replace('highslide-active-anchor', '');
	
	if (hs.hideSelects) this.showHideElements('SELECT', 'visible');
	if (hs.hideIframes) this.showHideElements('IFRAME', 'visible');
	if (hs.geckoMac) this.showHideElements('*', 'visible');	
	
	if (this.isHtml && this.preserveContent) this.sleep();
	else {
		if (this.objOutline && this.outlineWhileAnimating) this.objOutline.destroy();
		hs.purge(this.wrapper);
		if (hs.ie && hs.ieVersion() < 5.5) this.wrapper.innerHTML = ''; // crash
		else this.wrapper.parentNode.removeChild(this.wrapper);
	}
	if (hs.mask) hs.mask.style.display = 'none';
	if (this.dimmingOpacity) hs.unDim(this.key);
	hs.fireEvent(this, 'onAfterClose');
	hs.expanders[this.key] = null;		
	hs.cleanUp();
}
};


// hs.Ajax object prototype
hs.Ajax = function (a, content, pre) {
	this.a = a;
	this.content = content;
	this.pre = pre; // preloading
};

hs.Ajax.prototype = {
run : function () {
	this.src = hs.getSrc(this.a);
	if (this.src.match('#')) {
		var arr = this.src.split('#');
		this.src = arr[0];
		this.id = arr[1];
	}
	if (hs.cachedGets[this.src]) {
		this.cachedGet = hs.cachedGets[this.src];
		if (this.id) this.getElementContent();
		else this.loadHTML();
		return;
	}
	try { this.xmlHttp = new XMLHttpRequest(); }
	catch (e) {
		try { this.xmlHttp = new ActiveXObject("Msxml2.XMLHTTP"); }
		catch (e) {
			try { this.xmlHttp = new ActiveXObject("Microsoft.XMLHTTP"); }
			catch (e) { this.onError(); }
		}
	}
	var pThis = this;
	this.xmlHttp.onreadystatechange = function() {
		if(pThis.xmlHttp.readyState == 4) {	
			if (pThis.id) pThis.getElementContent();
			else pThis.loadHTML();
		}
	};
	
	this.xmlHttp.open("GET", this.src, true);
	this.xmlHttp.send(null);
},

getElementContent : function() {
	hs.genContainer();
	var attribs = window.opera ? { src: this.src } : null; // Opera needs local src
	this.iframe = hs.createElement('iframe', attribs, 
		{ position: 'absolute', left: '-9999px' }, hs.container);
		
	try {
		this.loadHTML();
	} catch (e) { // Opera security
		var pThis = this;
		setTimeout(function() {	pThis.loadHTML(); }, 1);
	}
},

loadHTML : function() {
	var s = this.cachedGet || this.xmlHttp.responseText;
	if (this.pre) hs.cachedGets[this.src] = s;
	if (!hs.ie || hs.ieVersion() >= 5.5) {
		s = s.replace(/\s/g, ' ');
		s = s.replace(new RegExp('<link[^>]*>', 'gi'), '');
		s = s.replace(new RegExp('<script[^>]*>.*?</script>', 'gi'), '');
		if (this.iframe) {
			var doc = this.iframe.contentDocument || this.iframe.contentWindow.document;
			doc.open();
			doc.write(s);
			doc.close();
			try { s = doc.getElementById(this.id).innerHTML; } catch (e) {
				try { s = this.iframe.document.getElementById(this.id).innerHTML; } catch (e) {} // opera
			}
			hs.container.removeChild(this.iframe);
		} else {
			s = s.replace(new RegExp('^.*?<body[^>]*>(.*?)</body>.*?$', 'i'), '$1');
		}
		
	}
	hs.getElementByClass(this.content, 'DIV', 'highslide-body').innerHTML = s;
	this.onLoad();
}
};
// history
var HsExpander = hs.Expander;

// set handlers
hs.addEventListener(document, 'mousedown', hs.mouseClickHandler);
hs.addEventListener(document, 'mouseup', hs.mouseClickHandler);
hs.addEventListener(window, 'load', hs.preloadImages);
hs.addEventListener(window, 'load', hs.preloadAjax);
hs.addEventListener(window, 'load', function() { hs.pageLoaded = true; });
hs.setClickEvents();