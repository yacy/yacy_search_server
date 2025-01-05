/*
 * @licstart  The following is the entire license notice for the 
 * JavaScript code in this file.
 * 
 * Copyright (C) 2018 by luccioman; https://github.com/luccioman
 * 
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
 * 
 * @licend  The above is the entire license notice
 * for the JavaScript code in this file.
 */

/* This is JavaScript for the IndexReIndexMonitor_p.html page */

var REFRESH_DELAY = 5000;
var TERMINATED_STATUS = 2;
var xhr = null;
if (window.XMLHttpRequest) {
	if(window.JSON && window.JSON.parse) {
		xhr = new XMLHttpRequest();
	} else {
		console.warn("JSON parsing is not supported by the browser.");
	}
} else {
	console.warn("XMLHttpRequest is not supported by the browser.");
}

/**
 * Once DOM is fully loaded, handle eventual reindex and recrawl jobs reports
 * refreshing
 */
function domLoaded() {
	/* Get the DOM elements to be refreshed */
	var earlyRecrawlTerminationElem = document.getElementById("earlyRecrawlTermination");
	var recrawlStatusElem = document.getElementById("recrawlStatus");
	var recrawlQueryTextElem = document.getElementById("recrawlQueryText");
	var recrawlStartTimeElem = document.getElementById("recrawlStartTime");
	var recrawlEndTimeElem = document.getElementById("recrawlEndTime");
	var urlsToRecrawlCountElem = document.getElementById("urlsToRecrawlCount");
	var recrawledUrlsCountElem = document.getElementById("recrawledUrlsCount");
	var rejectedUrlsCountElem = document.getElementById("rejectedUrlsCount");
	var malformedUrlsCountElem = document.getElementById("malformedUrlsCount");
	var refreshingIcon = document.getElementById("refreshingIcon");
	var refreshFailureIcon = document.getElementById("refreshFailureIcon");
	var refreshBtn = document.getElementById("recrawlRefreshBtn");

	if (recrawlStatusElem != null && recrawlStatusElem.dataset != null) {
		/* Initialize status labels that may be translated in the html template */
		var recrawlStatusLabels = {};
		if(recrawlStatusElem.dataset.status0 != null) {
			recrawlStatusLabels["0"] = recrawlStatusElem.dataset.status0;
		}
		if(recrawlStatusElem.dataset.status1 != null) {
			recrawlStatusLabels["1"] = recrawlStatusElem.dataset.status1;
		}
		if(recrawlStatusElem.dataset.status2 != null) {
			recrawlStatusLabels["2"] = recrawlStatusElem.dataset.status2;
		}
		
		if (recrawlStatusElem.dataset.status != TERMINATED_STATUS) {
			/* Update a DOM element when it exists */
			var updateElemText = function(elem, newValue) {
				if (newValue != null && elem != null) {
					elem.innerText = newValue;
				}
			}
			
			/* Handle failure to fetch fresh report information */
			var handleFetchReportError = function() {
				/* Hide the icon marking automated refresh, but keep the manual refresh button */
				if(refreshingIcon != null) {
					refreshingIcon.className = "hidden";
				}
				/* Display the icon informing user about failure on automated report refresh */
				if(refreshFailureIcon != null && refreshFailureIcon.className != null) {
					refreshFailureIcon.className = refreshFailureIcon.className.replace("hidden", "");
				}
			}
			
			/* Update report DOM elements with the fetched report information */
			var updateReportElements = function(report) {
				recrawlStatusElem.dataset.status = report.status;

				if (recrawlStatusLabels[report.status] != null) {
					/* Use the eventually translated status label when available */
					updateElemText(recrawlStatusElem, recrawlStatusLabels[report.status]);
				} else if(report.statusLabel != null) {
					/* Otherwise use the default one provided in the json response */
					updateElemText(recrawlStatusElem, report.statusLabel);
				}
				if(report.earlyTerminated) {
					if(earlyRecrawlTerminationElem != null && earlyRecrawlTerminationElem.className != null) {
						earlyRecrawlTerminationElem.className = earlyRecrawlTerminationElem.className.replace("hidden", "");
					}
				}
				updateElemText(recrawlQueryTextElem, report.query);
				updateElemText(recrawlStartTimeElem,
						report.startTime);
				updateElemText(recrawlEndTimeElem, report.endTime);
				if (report.urlCounts != null) {
					updateElemText(urlsToRecrawlCountElem,
							report.urlCounts.toRecrawl);
					updateElemText(recrawledUrlsCountElem,
							report.urlCounts.recrawled);
					updateElemText(rejectedUrlsCountElem,
							report.urlCounts.rejected);
					updateElemText(malformedUrlsCountElem,
							report.urlCounts.malformed);
					if (report.urlCounts.malformedDeleted != null
							&& malformedUrlsCountElem != null) {
						malformedUrlsCountElem.title = malformedUrlsCountElem.title
								.replace(/\d+/,
										report.urlCounts.malformedDeleted);
					}
				}
			}

			/* Processing the response from IndexReIndexMonitor_p.json */
			var handleResponse = function() {
				if (xhr.readyState == 4 /* XMLHttpRequest.DONE */) {
					if (xhr.status != 200 || xhr.response == null) {
						handleFetchReportError();
						return;
					}
					var report = null;
					try {
						var jsonResponse = window.JSON.parse(xhr.response);
						if (jsonResponse != null) {
							report = jsonResponse.recrawlJob;
						}
					} catch(error) {
						console.error("JSON parsing error ", error);
					}
					if (report == null || report.status == null) {
						handleFetchReportError();
						return;
					}

					updateReportElements(report);

					if(report.status == TERMINATED_STATUS) {
						/* First hide the icon marking automated refresh and the manual refresh button */
						if(refreshingIcon != null) {
							refreshingIcon.className = "hidden";
						}
						if(refreshBtn != null) {
							refreshBtn.className = "hidden";
						}
						/*
						 * Then if the update fieldset is displayed, completely refresh the page to display again the
						 * new recrawl job fieldset
						 */
						if(document.getElementById("updateJobFieldset") != null) {
							window.location.href = "IndexReIndexMonitor_p.html";
						}
					} else {
						/*
						 * Continue refreshing while the job is not
						 * terminated
						 */
						window.setTimeout(function() {
							xhr.onreadystatechange = handleResponse;
							xhr.open("get", "IndexReIndexMonitor_p.json");
							xhr.send();
						}, REFRESH_DELAY);
					}
				}
			};
			
			/*
			 * We are here so JavaScript is enabled and required API are supported : 
			 * we can show the refreshing icon if the element is present
			 */
			if(refreshingIcon != null && refreshingIcon.className != null) {
				refreshingIcon.className = refreshingIcon.className.replace("hidden", "");
			}

			window.setTimeout(function() {
				xhr.onreadystatechange = handleResponse;
				xhr.open("get", "IndexReIndexMonitor_p.json");
				xhr.send();
			}, REFRESH_DELAY);
		}
	}
}
if (xhr != null) {
	window.onload = domLoaded;
}