function linkstructure(hostname, element, width, height, maxtime, maxnodes) {
	var nodes = {};
	var links = [];
	var linkstructure = {};
	$.getJSON("/api/linkstructure.json?about=" + hostname + "&maxtime=" + maxtime + "&maxnodes=" + maxnodes, function(linkstructure) {
		links = linkstructure.graph;
		links.forEach(function(link) {
			  link.source = nodes[link.source] || (nodes[link.source] = {name: link.source, type:"Inbound"});
			  link.target = nodes[link.target] || (nodes[link.target] = {name: link.target, type:link.type});
		});
		var force = d3.layout.force().nodes(d3.values(nodes)).links(links).size([width, height]).linkDistance(60).charge(-800).on("tick", tick).start();
		force.gravity(0.7);
		var svg = d3.select(element).append("svg").attr("id", "hypertree").attr("width", width).attr("height", height);
		svg.append("defs").selectAll("marker")
		    .data(["Dead", "Outbound", "Inbound"])
		    .enter().append("marker")
		    .attr("id", function(d) { return d; })
		    .attr("viewBox", "0 -5 10 10")
		    .attr("refX", 15)
		    .attr("refY", -1.5)
		    .attr("markerWidth", 6)
		    .attr("markerHeight", 6)
		    .attr("orient", "auto")
		    .append("path")
		    .attr("d", "M0,-5L10,0L0,5");
		svg.append("text").attr("x", 10).attr("y", 15).text(hostname).attr("style", "font-size:16px").attr("fill", "black");
		svg.append("text").attr("x", 10).attr("y", 30).text("Site Link Structure Visualization made with YaCy").attr("style", "font-size:9px").attr("fill", "black");
		svg.append("text").attr("x", 10).attr("y", 40).text(new Date()).attr("style", "font-size:9px").attr("fill", "black");
		svg.append("text").attr("x", 10).attr("y", height - 20).text("green: links to same domain").attr("style", "font-size:9px").attr("fill", "green");
		svg.append("text").attr("x", 10).attr("y", height - 10).text("blue: links to other domains").attr("style", "font-size:9px").attr("fill", "lightblue");
		svg.append("text").attr("x", 10).attr("y", height).text("red: dead links").attr("style", "font-size:9px").attr("fill", "red");
		var path = svg.append("g")
			.selectAll("path").data(force.links()).enter().append("path")
			.attr("class",function(d) {return "hypertree-link " + d.type; })
			.attr("marker-end", function(d) { return "url(#" + d.type + ")";});
		var circle = svg.append("g").selectAll("circle").data(force.nodes()).enter().append("circle").attr("r", 4).call(force.drag);
		var text = svg.append("g")
			.selectAll("text").data(force.nodes()).enter().append("text").attr("x", 8).attr("y", ".31em")
			.attr("style", function(d) {return d.type == "Outbound" ? "fill:#888888;" : "fill:#000000;";})
			.text(function(d) {return d.name;});
		function tick() {
		  path.attr("d", linkArc);
		  circle.attr("transform", transform);
		  text.attr("transform", transform);
		}
		function linkArc(d) {
		  var dx = d.target.x - d.source.x, dy = d.target.y - d.source.y, dr = Math.sqrt(dx * dx + dy * dy);
		  return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr + " 0 0,1 " + d.target.x + "," + d.target.y;
		}
		function transform(d) {
		  return "translate(" + d.x + "," + d.y + ")";
		}
	});
 };
 