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

var svgnode = document.getElementById('fcm');

var svgNS = svg.attr('xmlns');

var nodes = [];
var lines = [];
var editlines = [];
var foreign;

function localPoint(x,y,tgt){
    var pt = svgnode.createSVGPoint();
    pt.x = x; 
    pt.y = y;
    return pt.matrixTransform(tgt.getScreenCTM().inverse());
}

var drag = d3.behavior.drag()
    .on("drag", dragmove)
    .on("dragend", dragmoveend);

document.body.addEventListener('click',function(e){
    if(e.target.getAttribute('class') == 'circle') {
	if(editlines.length == 0)
	    editlines.push({source: {x:e.clientX, y:e.clientY, node: e.target.parentNode}, 
			    target: {x:e.clientX, y:e.clientY, node: e.target.parentNode}});
	else {
	    lines.push({source: editlines.pop().source, 
			target: {x:e.pageX, y:e.pageY, node:e.target.parentNode}});
	}
	refresh();
	update();

    }
},false)

function addNode(elem) {
    var nodename = elem.options[elem.selectedIndex].innerHTML;
    var id = nodename.replace(/[^a-zA-Z0-9]/g, "");
    if(!_.find(nodes,function(n){return n.id == id;}))
	nodes.push({name:nodename, id:id, x:400, y:100});
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
    	.attr("r", 15);

    g.append("text")
    	.text(function(d, i) { return d.name; })
    	.attr("x", 20)
    	.attr("y", 0)
    	.attr("dx", 3)
    	.attr("dy", 3);

    node.exit().remove();

    var line = svg.selectAll("line")
	.data(editlines.concat(lines));
    
    line.enter().insert("line",".node")
    	.attr("class", "edge")
    	.attr("x1", function(d, i) { return d.source.x;})
    	.attr("y1", function(d, i) { return d.source.y;})
    	.attr("x2", function(d, i) { return d.target.x;})
    	.attr("y2", function(d, i) { return d.target.y;})
    	.attr("stroke", "black")
    	.attr("stroke-width", "2")
	.attr("marker-end", "url(#marker)");
    
    line.exit().remove();

}

function update() {
    d3.xhr("/iasess/mode/json")
	.header("Content-Type","application/x-www-form-urlencoded")
	.post("nodes=" + JSON.stringify(nodes)
	      + "&links=" + JSON.stringify(
		  _.map(lines,function(l) {return {tail: l.source.node.getAttribute("id"), 
						   head: l.target.node.getAttribute("id"),
						   weight: 2}})));
}

var screen = function(x,y,target) {
    var pt = svgnode.createSVGPoint();
    pt.x = x;
    pt.y = y;
    return pt.matrixTransform(target.getScreenCTM());
};

document.body.addEventListener('mousemove',function(e){
    var line = svg.selectAll("line")
	.data(editlines);
    
    line.attr("x1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).x;})
	.attr("y1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).y;})
	.attr("x2", function(d, i) { return localPoint(e.clientX,e.clientY,svgnode).x;})
	.attr("y2", function(d, i) { return localPoint(e.clientX,e.clientY,svgnode).y;});
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
    line.attr("x1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).x;})
    	.attr("y1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).y;})
    	.attr("x2", function(d, i) { return localPoint(d.target.x,d.target.y,svgnode).x;})
    	.attr("y2", function(d, i) { return localPoint(d.target.x,d.target.y,svgnode).y;});
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
	console.log(n);
	var tail = svgnode.getElementById(n.tail);
	var head = svgnode.getElementById(n.head);
	var tailpt = localPoint(screen(0,0,tail).x, screen(0,0,tail).y, svgnode);
	var headpt = localPoint(screen(0,0,head).x, screen(0,0,head).y, svgnode);
	if (head && tail)
	    lines.push({source: {node: tail, x:tailpt.x, y:tailpt.y},
			target: {node: head, x:headpt.x, y:headpt.y}});
    });
    refresh();
});