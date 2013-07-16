"use strict";
var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var svg = d3.select("#graph").append("svg")
    .attr("id", "fcm")
    .attr("xmlns","http://www.w3.org/2000/svg");

svg.append("svg:marker")
    .attr("id", "marker")
    .attr("viewBox", "0 0 10 10")
    .attr("refX", 15)
    .attr("refY", 3)
    .attr("markerUnits", "strokeWidth")
    .attr("markerWidth", 10)
    .attr("markerHeight", 10)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", "M0,0L10,3L0,6");

svg.append("image")
    .attr("width","50px")
    .attr("height","50px")
    .attr("xlink:href","/iasess/images/kget_list.png");

var svgnode = document.getElementById('fcm');

var svgNS = svg.attr('xmlns');

var nodes = [];
var lines = [];
var menus = [];
var editlines = [];
var foreign;

function localPoint(obj){
    var pt = svgnode.createSVGPoint();
    pt.x = obj.x; 
    pt.y = obj.y;
    return pt.matrixTransform(svgnode.getScreenCTM().inverse());
}

function localClient(e) {
    return localPoint({x:e.clientX,y:e.clientY});
}

var drag = d3.behavior.drag()
    .on("drag", dragmove)
    .on("dragend", dragmoveend);

function click(d,i){
    var e = d3.event;
    if(editlines.length == 0)
	editlines.push({source: {x:localClient(e).x, y:localClient(e).y, 
				 node: this.parentNode, datum: d}, 
			target: {x:localClient(e).x, y:localClient(e).y, 
				 node: this.parentNode, datum: d}});
    else {
	lines.push({source: editlines.pop().source, 
		    target: {x:localClient(e).x, y:localClient(e).y, 
			     node: this.parentNode, datum: d},
		    weight: 0});
    }
    refresh();
    update();
}

function addNode(elem) {
    var nodename = elem.options[elem.selectedIndex].innerHTML;
    var id = nodename.replace(/[^a-zA-Z0-9]/g, "");
    if(!_.find(nodes,function(n){return n.id == id;}))
	nodes.push({name:nodename, id:id, x:400, y:100});
    refresh();
    update();
}

function menu(dat,i) {
    menus.push(1);
    var menu = svg.selectAll("g#menu")
	.data(menus)
	.enter().append("g")
	.attr("transform", "translate(" + localClient(d3.event).x + "," + localClient(d3.event).y + ")")
	.attr("id", "menu");

    menu.append("rect")
	.attr("x", 0)
	.attr("width", 30)
	.attr("height", 150)
	.attr("fill", "lightsteelblue");

    menu.selectAll("text")
	.data([{strength:'+++',index:3},
	       {strength:'++',index:2},
	       {strength:'+',index:1},
	       {strength:'-',index:-1},
	       {strength:'--',index:-2},
	       {strength:'---',index:-3},
	       {strength:'[x]',index:999}])
	.enter().append("text")
	.text(function(d,i){return d.strength;})
	.attr("y",function(d,i) { return (i+1) * 20;})
	.on("click",function(d,i) {setWeight(dat, d.index);});
}

function setWeight(d, weight) {
    d.weight = weight;
    menus.pop();
    svg.selectAll("g#menu")
	.data(menus)
	.exit().remove();
    refresh();
    update();
}

function deleteNode(d) {
    nodes = _.without(nodes,d);
    lines = _.reject(lines, function (l) { return l.target.datum == d; });
    svg.selectAll("g.node")
	.data(nodes)
	.exit().remove();
    svg.selectAll("g.edge")
	.data(lines)
	.exit().remove();
    refresh();
    update();
}
function refresh() {   
    var node = svg.selectAll("g.node")
	.data(nodes);

    var g = node.enter().append("g");

    g.attr("transform", function(d, i) { return "translate(" + d.x + "," + d.y + ")"; })
	.attr("class", "node")
	.attr("id", function(d, i) { return d.id; })
	.call(drag);

    g.append("circle")
    	.attr("class", "circle")
    	.attr("cx", 0)
    	.attr("cy", 0)
    	.attr("r", 15)
	.on("click",click);

    g.append("text")
	.text("[x]")
    	.attr("x", 20)
    	.attr("y", 0)
    	.attr("dx", 3)
    	.attr("dy", 3)
	.on("click",deleteNode);

    g.append("text")
    	.text(function(d, i) { return d.name; })
    	.attr("x", 40)
    	.attr("y", 0)
    	.attr("dx", 3)
    	.attr("dy", 3);

    node.exit().remove();

    lines = _.reject(lines,function(l) { return l.weight == 999;});
    var line = svg.selectAll("g.edge")
	.data(lines);
    
    var gl = line.enter().insert("g",".node")
    	.attr("class", "edge");

    gl.append("line")
    	.attr("x1", function(d, i) { return d.source.x;})
    	.attr("y1", function(d, i) { return d.source.y;})
    	.attr("x2", function(d, i) { return d.target.x;})
    	.attr("y2", function(d, i) { return d.target.y;})
    	.attr("stroke", "black")
    	.attr("stroke-width", "2")
	.attr("marker-end", "url(#marker)");

    gl.append("text")
	.attr("class", "weight")
	.text(function(d, i) { return d.weight.toString();})
	.attr("x", function(d, i) { return d.source.x - (d.source.x - d.target.x) / 2;})
	.attr("y", function(d, i) { return d.source.y - (d.source.y - d.target.y) / 2;})
	.on("click", menu);

    svg.selectAll(".edge text")
	.text(function(d, i) { return d.weight;});
    
    line.exit().remove();

    var edge = svg.selectAll("g.activeedge")
	.data(editlines);
    
    edge
	.enter().insert("g",".node")
	.attr("class", "activeedge")
	.append("line")
    	.attr("x1", function(d, i) { return d.source.x;})
    	.attr("y1", function(d, i) { return d.source.y;})
    	.attr("x2", function(d, i) { return d.target.x;})
    	.attr("y2", function(d, i) { return d.target.y;})
    	.attr("stroke", "black")
    	.attr("stroke-width", "2")
	.attr("marker-end", "url(#marker)");

    edge.exit().remove();
}

function update() {
    d3.xhr("/iasess/mode/json")
	.header("Content-Type","application/x-www-form-urlencoded")
	.post("nodes=" + JSON.stringify(nodes)
	      + "&links=" + JSON.stringify(
		  _.map(lines,function(l) {return {tail: l.source.node.getAttribute("id"), 
						   head: l.target.node.getAttribute("id"),
						   weight: l.weight,
						   taildatum: l.target.datum,
						   headdatum: l.target.datum}})));
}

var screen = function(x,y,target) {
    var pt = svgnode.createSVGPoint();
    pt.x = x;
    pt.y = y;
    return pt.matrixTransform(target.getScreenCTM());
};

document.body.addEventListener('mousemove',function(e){
    if(editlines.length != 0) {
	var line = svg.selectAll("g.activeedge line")
	    .data(editlines);
	
	line.attr("x1", function(d, i) { return d.source.x;})
	    .attr("y1", function(d, i) { return d.source.y;})
	    .attr("x2", function(d, i) { return localClient(e).x;})
	    .attr("y2", function(d, i) { return localClient(e).y;});
    }
},false);

function dragmove(d) {
    d3.select(this)
	.attr("transform", d.transform = 
	      "translate(" + (d.x = d3.event.x) + "," + (d.y = d3.event.y) + ")");

    lines.forEach(function(k,v) {k.source.x = screen(0,0,k.source.node).x;
				 k.target.x = screen(0,0,k.target.node).x;
				 k.source.y = screen(0,0,k.source.node).y;
				 k.target.y = screen(0,0,k.target.node).y;
				});
    
    var line = svg.selectAll("line")
	.data(lines);
    line.attr("x1", function(d, i) { return localPoint(d.source).x;})
    	.attr("y1", function(d, i) { return localPoint(d.source).y;})
    	.attr("x2", function(d, i) { return localPoint(d.target).x;})
    	.attr("y2", function(d, i) { return localPoint(d.target).y;});

    var text = svg.selectAll(".weight")
	.data(lines);
    text.attr("x", function(d, i) { return localPoint(d.source).x 
				    - (d.source.x - d.target.x) / 2;})
	.attr("y", function(d, i) { return localPoint(d.source).y 
				    - (d.source.y - d.target.y) / 2;});
}

function dragmoveend(d) {
    update();
}


d3.json("/iasess/mode/json",function(e,d) {
    _.each(d.nodes,function(n) {
	nodes.push({name:n.name, id:n.id, x:n.x, y:n.y});
	console.log("pushed node " + n.id + " : " + n.name);
    });
    refresh();
    _.each(d.links,function(n) {
	var tail = svgnode.getElementById(n.tail);
	var head = svgnode.getElementById(n.head);
	var tailpt = localPoint(screen(0,0,tail));
	var headpt = localPoint(screen(0,0,head));
	if (head && tail)
	    lines.push({source: {node: tail, x:tailpt.x, y:tailpt.y, datum: n.taildatum},
			target: {node: head, x:headpt.x, y:headpt.y, datum: n.headdatum},
			weight: n.weight});
    });
    refresh();
});

