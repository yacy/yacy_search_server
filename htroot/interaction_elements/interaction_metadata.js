
function getMetadata (url) {


	var res = {"item": {"title": "no title"}};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/api/getpageinfo.json?url='+url, function(data) {
	
		res = data;
		
	
	});
	
		
	return res;
	
	
}

