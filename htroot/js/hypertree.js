/*    
* Copyright (C) 2014 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

function linkstructure(hostname, element, width, height, maxtime, maxnodes) {
	var nodes = {};
	var links = [];
	$.getJSON("api/linkstructure.json?about=" + hostname + "&maxtime=" + maxtime + "&maxnodes=" + maxnodes, function(linkstructure) {
		links = linkstructure.graph;
		links.forEach(function(link) {
			  link.source = nodes[link.source] || (nodes[link.source] = {name: link.source, type:"Inbound"});
			  link.target = nodes[link.target] || (nodes[link.target] = {name: link.target, type:link.type});
		});
		
		/* attract nodes to the center - was set with force.gravity(0.7) in d3v3 */
		var forceX = d3.forceX(width / 2).strength(0.7);
		var forceY = d3.forceY(height / 2).strength(0.7);
		
		var link = d3.forceLink(links).distance(60).strength(1);
		var simulation = d3.forceSimulation()
			.nodes(d3.values(nodes))
			.force('link', link)
			.force("center", d3.forceCenter(width / 2, height / 2)) // center elements - was set with size([width, height]) in d3v3
			.force('charge', d3.forceManyBody().strength(-800))
			.force('x', forceX)
			.force('y',  forceY)
			.on("tick", ticked);
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
			.selectAll("path").data(link.links()).enter().append("path")
			.attr("class",function(d) {return "hypertree-link " + d.type; })
			.attr("marker-end", function(d) { return "url(#" + d.type + ")";});
		var circle = svg.append("g").selectAll("circle").data(simulation.nodes()).enter().append("circle").attr("r", 4).call(d3.drag());
		var maxTextLength = 40;
		var text = svg.append("g")
			.selectAll("text").data(simulation.nodes()).enter().append("text").attr("x", 8).attr("y", ".31em")
			.attr("style", function(d) {return d.type == "Outbound" ? "fill:#888888;" : "fill:#000000;";})
			.text(function(d) {/* Limit the length of nodes visible text to improve readability */ return d.name.substring(0, Math.min(d.name.length, maxTextLength));});
		text.append("tspan")
			.attr("class", "truncated")
			.text(function(d) {/* The end of large texts is wraped in a tspan, made visible on mouse overing */return d.name.length > maxTextLength ? d.name.substring(maxTextLength) : ""});
		
		text.append("tspan")
			.attr("class", "ellipsis")
			.text(function(d) {/* Add an ellipsis to mark long texts that are truncated */ return d.name.length > maxTextLength ? "..." : ""});

		
		function ticked() {
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
 