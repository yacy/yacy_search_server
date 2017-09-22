/*    
* Copyright (C) 2017 by luccioman; https://github.com/luccioman
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Add complementary features to a bar chart created with morris.js for improved accessibility : 
 * keyboard navigation support and accessible labels and widget roles.
 * @param {Morris.Bar} morrisBar a bar chart created with Morris.Bar()
 * @param {String} title the accessible title to add to the bar chart
 * @param {Function} barLabelGenerator the eventual function providing an accessible label for each bar. The function must accept one parameter (the data item related to the bar) and return a String.
 * @param {String} barRole the eventual ARIA role to assign to each bar element
 * @param {Function} clickHandler an eventual click event handler function defined on the chart and to be applied when pressing "Enter" on a focused bar
 */
function makeAccessibleMorrisBar(morrisBar, title, barLabelGenerator, barRole, clickHandler) {
	if(morrisBar && morrisBar.el && morrisBar.el.length > 0) {
		var svgBarChart = morrisBar.el[0];
		/* Mark the chart with the appropriate ARIA roles, including fallback values for older user agents */
		svgBarChart.setAttribute("role", "graphics-document figure document");
		
		/* Add a comprehensive title */
		var titleElements = svgBarChart.getElementsByTagName("title");
		var titleElement;
		if(titleElements.length < 1) {
			titleElement = document.createElement("title");
		} else {
			titleElement = titleElements[0];
		}
		titleElement.innerHTML = title;
		titleElement.id = "morisBarTitle";
		svgBarChart.insertBefore(titleElement, svgBarChart.firstChild);
		svgBarChart.setAttribute("aria-labelledby", "morisBarTitle");
		
		/* Handle keyboard events on focusable bars to allow keyboard navigation */
		var histogramBarKeydownHandler = function(event) {
			if(event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
				/* Prevent collision with any eventual other keyboard shortcuts */
				return;
			}

			if(event.key == "ArrowRight" || event.keyCode == 39) {
			    	var nextFocusable = this.nextSibling;
			    	/* Look for the next focusable bar */
			    	while(nextFocusable != null && (!nextFocusable.focus || !nextFocusable.hasAttribute("tabindex"))) {
			    		nextFocusable = nextFocusable.nextSibling;
			    	}
			    	if(nextFocusable != null && nextFocusable.focus && nextFocusable.tabIndex != null) {
			    		/* Set the current bar focusable but out of the tab sequence */
				    	this.setAttribute("tabindex", "-1")
				    	/* Set the next bar focusable in the tab sequence */
				    	nextFocusable.setAttribute("tabindex", "0");
				    	/* Give focus to the next bar */
				    	nextFocusable.focus();
			    	}
			} else if(event.key == "ArrowLeft" || event.keyCode == 37) {
			    	var prevFocusable = this.previousSibling;
			    	/* Look for the previous focusable bar */
			    	while(prevFocusable != null && (!prevFocusable.focus || !prevFocusable.hasAttribute("tabindex"))) {
			    		prevFocusable = prevFocusable.previousSibling;
			    	}
			    	if(prevFocusable != null && prevFocusable.focus && prevFocusable.tabIndex != null) {
			    		/* Set the current bar focusable but out of the tab sequence */
				    	this.setAttribute("tabindex", "-1");
				    	/* Set the next bar focusable in the tab sequence */
				    	prevFocusable.setAttribute("tabindex", "0");
				    	/* Give focus to the next bar */
				    	prevFocusable.focus();
			    	}
			} else if(clickHandler && (event.key == "Enter" || event.key == "NumpadEnter" || event.keyCode == 13)) {
				/* Find the data index from the bar position */
				var dataIndex = morrisBar.hitTest(this.x.animVal.value);
				if(dataIndex != null && dataIndex >= 0 && dataIndex < morrisBar.options.data.length) {
					/* Implement the same behavior as a link */
					clickHandler(morrisBar.options.data[dataIndex]);	
				}
			}
		};
		
		/* When a bar receive focus from keyboard navigation : show the same toolip as the one used on mouse hover */
		var histogramBarFocusHandler = function() {
			/* Find the data index from the bar position */
			var dataIndex = morrisBar.hitTest(this.x.animVal.value);
				if(dataIndex != null && dataIndex >= 0 && morrisBar.hover != null) {
					morrisBar.hover.update.apply(morrisBar.hover, morrisBar.hoverContentForRow(dataIndex));
				}
		};
		
		/* When a bar looses focus : hide the tooltip */
		var histogramBarBlurHandler = function() {
		    if (morrisBar.options.hideHover !== false) {
		    	morrisBar.hover.hide();
		    }
		};
		
		var bars = svgBarChart.getElementsByTagName("rect");
		var data, count, bar, firstFocusableBar = true;
		for(var i = 0; i < bars.length && i < morrisBar.options.data.length; i++) {
			data = morrisBar.options.data[i];
			count = data.y;
			bar = bars[i];
			/* Only make non zero value bars focusable */
			if(count != "0") {
				/* Add the eventual bar specific role */
				if(barRole) {
					bar.setAttribute("role", barRole);
				}
				/* Add an accessible label as the regular hover is dynamically generated and this doesn't work well with screen readers */
				bar.setAttribute("aria-label", barLabelGenerator ? barLabelGenerator(data) : data.x);
				/* make each bar keyboard focusable, adding only the first one to the main tab sequence */
				bar.setAttribute("tabindex", firstFocusableBar ? "0" : "-1");
				/* Handle keyboard navigation */
				bar.onkeydown = histogramBarKeydownHandler;
				/* Show/hide each bar tooltip when each bear receive/loose focus with keyboard */
				bar.onfocus = histogramBarFocusHandler;
				bar.onblur = histogramBarBlurHandler;
				firstFocusableBar = false;
			}
		}
	}
}