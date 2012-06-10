function newload (name, div) {

$.get(name, function(data) {

$('#'+div).html(data);
});

}

function xmlToString (xmlData) {

	var xmlString;
	
	if (window.ActiveXObject) {
		xmlString = xmlData.xml;
	}
	
	else {
		xmlString = (new XMLSerializer()).serializeToString(xmlData);
	}
	
	return xmlString;

}

function rdfload (datastore) {

$.ajax({
	type : "GET",
	url: "/currentyacypeer/interaction/GetRDF.xml?global=true",
	dataType: "xml",
	success: function(xml) {
		datastore.load(xml);

	}


});



}


function feedback (url, comment, from) {

	

	$.getJSON('/currentyacypeer/interaction/Feedback.json?url='+url+'&comment='+comment+'&from='+from, function(data) {
			
	});
	
}

function suggest (url) {

	$.getJSON('/currentyacypeer/interaction/Suggest.json?url='+url, function(data) {
			
	
	});
	
}

function contribution (url, comment, username) {

	$.getJSON('/currentyacypeer/interaction/Contribution.json?url='+url+'&comment='+comment+'&from='+username, function(data) {
			
	
	});
	
}

function triple (url, s, p, o, username) {

	$.getJSON('/currentyacypeer/interaction/Triple.json?url='+url+'&s='+s+'&p='+p+'&o='+o+'&from='+username, function(data) {
			
	
	});
	
}

function storevalue_t (s, p, o) {

	$.getJSON('/currentyacypeer/interaction/Table.json?url='+s+'&s='+s+'&p='+p+'&o='+o, function(data) {
			
	
	});
	
}

function storevalueglobal_t (s, p, o) {

	$.getJSON('/currentyacypeer/interaction/Table.json?global=true&url='+s+'&s='+s+'&p='+p+'&o='+o, function(data) {
			
	
	});
	
}

function storevalue (s, p, o) {

	$.getJSON('/currentyacypeer/interaction/Triple.json?url='+s+'&s='+s+'&p='+p+'&o='+o, function(data) {
			
	
	});
	
}

function storevalueglobal (s, p, o) {

	$.getJSON('/currentyacypeer/interaction/Triple.json?global=true&url='+s+'&s='+s+'&p='+p+'&o='+o, function(data) {
			
	
	});
	
}

function loadvalue (s, p) {

	var res = {result: ""};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/interaction/Triple.json?s='+s+'&p='+p+'&load=true', function (data) {
	
		res = data;
	
	});
	
		
	return res.result;
	
}

function loadvalue_t (s, p) {

	var res = {result: ""};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/interaction/Table.json?s='+s+'&p='+p+'&load=true', function (data) {
	
		res = data;
	
	});
	
		
	return res.result;
	
}

function loadvalueglobal_t (s, p) {

	var res = {result: ""};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/interaction/Table.json?global=true&s='+s+'&p='+p+'&load=true', function (data) {
	
		res = data;
	
	});
	
		
	return res.result;
	
}

function loadvalueglobal (s, p) {

	var res = {result: ""};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/interaction/Triple.json?global=true&s='+s+'&p='+p+'&load=true', function (data) {
	
		res = data;
	
	});
	
		
	return res.result;
	
}

function thing(url, title)
{
	var newItems = [];	
	
	var d = new Date();
	var n = d.getTime(); 
	
	var customItem = {
			"itemType": "webpage",
			"title": title,
			"url": url
		};
		
	
	newItems.push(customItem);
	
	var payload = JSON.stringify({"overall":{"item":customItem}}, null, "\t");
		
		doAuthenticatedPost(payload, payload, function(status) {
			if(!status) {
				// callback(false, new Error("Save to server failed"));
			} else {

				// callback(true, newItems);
			}
		}, true, true);
		
		
}



function doAuthenticatedPost (body, body2, callback, docreate, dooverwrite, param, paramval) {
				
		// process url
		// var url = "http://wiki.sciety.org/content/Special:ECC_In";
		// var url = "http://wiki.sciety.org/content/Special:ECC_In";
		var url = "http://141.52.79.141/content/Special:ECC_In";
		
		
		// do form submit
		
		doFormSubmit(url, body, body2, function(xmlhttp) {

			if([200, 201, 204].indexOf(xmlhttp.status) !== -1) {		
		
				theResult = JSON.parse(xmlhttp.responseText);
				
				alert (theResult.username);
				
				// document.getElementById("sciety_averagerating").value = "Average rating: " + theResult.averagerating;
				
				// document.getElementById("sciety_username").value = "Current user: " + theResult.username;
				// document.getElementById("sciety_uservalue").value = "Your ranking weight: " + theResult.uservalue;
				
				// update_view();
				
				callback(true);
			} else {
										
				theResult = JSON.parse(xmlhttp.responseText);
							
				// document.getElementById("sciety_averagerating").value = "Average rating: " + theResult.averagerating;
				
				// document.getElementById("sciety_username").value = "Current user: " + theResult.username;
				// document.getElementById("sciety_uservalue").value = "Your ranking weight: " + theResult.uservalue;
	
				var msg = xmlhttp.responseText;
				
				alert (theResult.username);
				
				// update_view();

				callback(false);
			}
		}, false, docreate, dooverwrite, param, paramval);
	}
	
	
function doSubmitAdditionalElement (body, body2, callback, docreate, dooverwrite, param, paramval) {
				
		// process url
		// var url = "http://wiki.sciety.org/content/Special:ECC_In";
		var url = "http://wiki.sciety.org/content/Special:ECC_Add";
		
		
		// do form submit
		
		doFormSubmit(url, body, body2, function(xmlhttp) {

			if([200, 201, 204].indexOf(xmlhttp.status) !== -1) {		
		
				theResult = JSON.parse(xmlhttp.responseText);
				
				update_view();
				
				callback(true);
			} else {
										
				theResult = JSON.parse(xmlhttp.responseText);
				
				var msg = xmlhttp.responseText;
				
				update_view();

				callback(false);
			}
		}, false, docreate, dooverwrite, param, paramval);
	}
	
	
function doFormSubmit (url, body, body2, onDone, headers, docreate, dooverwrite, param, paramval) {

		var bodyStart = body.substr(0, 1024);	
			
		var formData = new FormData();
		
		formData.append ("create", docreate);
		formData.append ("overwrite", dooverwrite);
		
		formData.append ("item", body);
		formData.append ("data", body2);
		
		
		if (param) {
			formData.append(param, paramval);
		}
		
		var ie = false; 
		
		if (XDomainRequest) { 
			ie = true;
								
		}
			
			
			if (ie) { 
				var xmlhttp = new XDomainRequest();				
			} 
			else {
				var xmlhttp = new XMLHttpRequest();
		}
		
		try {
		
			
			xmlhttp.open('POST', url, true);
		
						
			xmlhttp.onreadystatechange = function(){
				_stateChange(xmlhttp, onDone);
			};
						
						// if (formData.fake)
						
			if (!ie ) {
				xmlhttp.setRequestHeader("Content-Type", "multipart/form-data; boundary="+formData.boundary);
				xmlhttp.sendAsBinary(formData.toString()); 
			} else {
				
				xmlhttp.send(body);
			
			} 
						

		} catch(e) {
		
			alert (e);

			if(onDone) {
				window.setTimeout(function() {
					try {
						onDone({"status":0});
					} catch(e) {
						return;
					}
				}, 0);
			}
		}
			
		return xmlhttp;
	}
	
	
	
	
	/**
	 * Handler for XMLHttpRequest state change
	 *
	 * @param {nsIXMLHttpRequest} XMLHttpRequest whose state just changed
	 * @param {Function} [onDone] Callback for request completion
	 * @param {String} [responseCharset] Character set to force on the response
	 * @private
	 */
	function _stateChange(xmlhttp, callback) {
		switch (xmlhttp.readyState){
			// Request not yet made
			case 1:
				break;
			
			case 2:
				break;
			
			// Called multiple times while downloading in progress
			case 3:
				break;
			
			// Download complete
			case 4:
				if (callback) {
					try {
						callback(xmlhttp);
					} catch(e) {

						return;
					}
				}
			break;
		}
	}