function changeHost(){
	window.location.replace("http://"+window.location.host+":"+window.location.port+"/WatchWebStructure_p.html?host="+document.getElementById("host").value);
}
function keydown(ev){
        if(ev.which==13){
                changeHost();
        }
}

/* Check if the input matches some RGB hex values */
function isValidColor(hexcolor) {
	var strPattern = /^[0-9a-f]{3,6}$/i;
	return strPattern.test(hexcolor);
} 
	
function checkform(form) {
	if (isValidColor(form.colorback.value)) {
		if (isValidColor(form.colortext.value)) {
			if (isValidColor(form.colorline.value)) {
				if (isValidColor(form.colordot.value)) {
					if (isValidColor(form.colorlineend.value)) {
						return true;
					} else {
						alert("Invalid Dot-end value: " + form.colorlineend.value);
						return false;
					}
				} else {
					alert("Invalid Dot value: " + form.colordot.value);
					return false;
				}
			} else {
				alert("Invalid Line value: " + form.colorline.value);
				return false;
			}
		} else {
			alert("Invalid Text value: " + form.colortext.value);
			return false;
		}
	} else {
		alert("Invalid Background value: " + form.colorback.value);
		return false;
	}
}