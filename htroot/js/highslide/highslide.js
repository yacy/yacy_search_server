	/******************************************************************************
Name:    Highslide JS
Version: 3.3.9 (February 15 2008)
Config:  default
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
graphicsDir : '/js/highslide/graphics/',
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


// These settings can also be overridden inline for each image
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
	'outlineType',
	'outlineWhileAnimating',
	'spaceForCaption',
	'captionId',
	'captionText',
	'captionEval',
	
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
	
	var width = hs.ie ? iebody.clientWidth : 
			(document.documentElement.clientWidth || self.innerWidth),
		height = hs.ie ? iebody.clientHeight : self.innerHeight;
	
	return {
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
	} else if (e.type == 'mouseup') {
		
		hs.removeEventListener(document, 'mousemove', hs.dragHandler);
		
		if (hs.dragArgs) {
			
			if (hs.dragArgs.type == 'image')
				hs.dragArgs.exp.content.style.cursor = hs.styleRestoreCursor;
			
			var hasDragged = (Math.abs(hs.dragArgs.dX) + Math.abs(hs.dragArgs.dY) > 0);
			
			if (!hasDragged &&!hs.hasFocused && !/(move|resize)/.test(hs.dragArgs.type)) {
				exp.close();
			} 
			else if (hasDragged || (!hasDragged && hs.hasHtmlexpanders)) {
				hs.dragArgs.exp.redoShowHide();
			}
			
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
	
	hs.dragArgs.dX = e.clientX - hs.dragArgs.clickX;
	hs.dragArgs.dY = e.clientY - hs.dragArgs.clickY;
	
	 exp.move(hs.dragArgs);
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
		
		// http://www.robertpenner.com/easing/ 
		Math.linearTween = function (t, b, c, d) {
			return c*t/d + b;
		};
		Math.easeInQuad = function (t, b, c, d) {
			return c*(t/=d)*t + b;
		};
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

contentLoaded : function() {
	try {	
		if (!this.content) return;
		if (this.onLoadStarted) return; // old Gecko loop
		else this.onLoadStarted = true;
		
			   
		if (this.loading) {
			this.loading.style.left = '-9999px';
			this.loading = null;
			this.a.style.cursor = this.originalCursor || '';
		}
		this.marginBottom = hs.marginBottom;	
			this.newWidth = this.content.width;
			this.newHeight = this.content.height;
			this.fullExpandWidth = this.newWidth;
			this.fullExpandHeight = this.newHeight;
			
			this.content.style.width = this.thumbWidth +'px';
			this.content.style.height = this.thumbHeight +'px';
			this.getCaption();	
		
		
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
		
		var page = hs.getPageSize();
		// justify
		this.x = { 
			min: parseInt(this.thumbLeft) - this.offsetBorderW + this.thumbOffsetBorderW,
			span: this.newWidth,
			minSpan: (this.newWidth < minWidth && !hs.padToMinWidth) 
				? this.newWidth : minWidth,
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
			marginMin: hs.marginTop, 
			marginMax: this.marginBottom, 
			scroll: page.scrollTop,
			clientSpan: page.height,
			thumbSpan: this.thumbHeight
		};
		var oldBottom = this.y.min + parseInt(this.thumbHeight);
		this.y = this.justify(this.y);
		
			this.correctRatio(ratio);
		

		var x = this.x;
		var y = this.y;
		
		this.show();
	} catch (e) {
		window.location.href = hs.getSrc(this.a);
	}
},

justify : function (p) {
	
	var tgt, dim = p == this.x ? 'x' : 'y';
	
	
		var hasMovedMin = false;
		
		var allowReduce = true;
		
		// calculate p.min
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
	
	if (up && this.objOutline && !this.outlineWhileAnimating) 
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
	
	else if (!up && this.objOutline) {
		if (this.outlineWhileAnimating) this.objOutline.setPosition(this, from.x, from.y, from.w, from.h);
		else this.objOutline.destroy();
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

setSize : function (to) {
	try {
			this.wrapper.style.width = (to.w + 2*this.offsetBorderW) +'px';
			this.content.style.width =
				((to.imgW && !isNaN(to.imgW)) ? to.imgW : to.w) +'px';
			if (hs.safari) this.content.style.maxWidth = this.content.style.width;
			this.content.style.height = to.h +'px';
		
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
	
	this.createOverlays();
	if (hs.showCredits) this.writeCredits();
	if (this.fullExpandWidth > this.x.span) this.createFullExpand();
	if (!this.caption) this.prepareNextOutline();
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
	this.a.style.cursor = this.originalCursor;	
	if (this.loading) hs.loading.style.left = '-9999px';
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
			
				
					var clearsX = (elPos.x + elPos.w < imgPos.x || elPos.x > imgPos.x + imgPos.w);
					var clearsY = (elPos.y + elPos.h < imgPos.y || elPos.y > imgPos.y + imgPos.h);
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
			
				blurExp.content.style.cursor = hs.ie ? 'hand' : 'pointer';
				blurExp.content.title = hs.focusTitle;
		}
	}
	
	// focus this
	if (this.objOutline) this.objOutline.table.style.zIndex 
		= this.wrapper.style.zIndex;
	
	this.content.className = 'highslide-'+ this.contentType;
	
	if (this.caption) {
		this.caption.className = this.caption.className.replace(' highslide-caption-blur', '');
	}
	
		this.content.title = hs.restoreTitle;
		
		hs.styleRestoreCursor = window.opera ? 'pointer' : 'url('+ hs.graphicsDir + hs.restoreCursor +'), pointer';
		if (hs.ie && hs.ieVersion() < 6) hs.styleRestoreCursor = 'hand';
		this.content.style.cursor = hs.styleRestoreCursor;
		
	hs.focusKey = this.key;	
	hs.addEventListener(document, 'keydown', hs.keyHandler);	
},

move : function (e) {
	this.x.min = e.left + e.dX;
	this.y.min = e.top + e.dY;
	
	hs.setStyles(this.wrapper, { left: this.x.min +'px', top: this.y.min +'px' });
	
	if (this.objOutline)
		this.objOutline.setPosition(this, this.x.min, this.y.min, this.x.span, this.y.span);
	
},

close : function() {
	if (this.isClosing || !this.isExpanded) return;
	this.isClosing = true;
	
	hs.removeEventListener(document, 'keydown', hs.keyHandler);
	
	try {
		
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

createOverlay : function (o) {
	var el = o.overlayId;
	if (typeof el == 'string') el = hs.getNode(el);
	if (!el || typeof el == 'string') return;
	
	
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
	this.createOverlay({ overlayId: a, position: hs.fullExpandPosition, 
		hideOnMouseOut: true, opacity: hs.fullExpandOpacity });
},

doFullExpand : function () {
	try {	
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
		if (this.objOutline && this.outlineWhileAnimating) this.objOutline.destroy();
		hs.purge(this.wrapper);
		if (hs.ie && hs.ieVersion() < 5.5) this.wrapper.innerHTML = ''; // crash
		else this.wrapper.parentNode.removeChild(this.wrapper);
	hs.expanders[this.key] = null;		
	hs.cleanUp();
}
};
// history
var HsExpander = hs.Expander;

// set handlers
hs.addEventListener(document, 'mousedown', hs.mouseClickHandler);
hs.addEventListener(document, 'mouseup', hs.mouseClickHandler);
hs.addEventListener(window, 'load', hs.preloadImages);