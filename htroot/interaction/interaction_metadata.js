
function getMetadata (url) {


	var res = {"item": ""};
	
	$.ajaxSetup({async: false});

	$.getJSON('/currentyacypeer/api/getpageinfo.json?url='+url, function(data) {
	
		res = data;
		
	
	});
	
		
	return res;
	
	
}

