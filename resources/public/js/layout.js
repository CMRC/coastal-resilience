var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var svg = d3.select("#graph").append("svg")
    .attr("id", "fcm")
    .attr("xmlns","http://www.w3.org/2000/svg");
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
    if(e.target.getAttribute('class') == 'arrow') {
	if(editlines.length == 0)
	    editlines.push({source: {x:e.clientX, y:e.clientY, node: e.target.parentNode}, 
			    target: {x:e.clientX, y:e.clientY, node: e.target.parentNode}});
	else {
	    lines.push({source: editlines.pop().source, 
			target: {x:e.pageX, y:e.pageY, node:e.target.parentNode}});
	}
	refresh();
	update();

	svg.selectAll(".edge, .node").sort(function (a, b) {
	    if (a.class == "edge" && b.class == "node") return -1; 
	    else return 1;                    
	});
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

    g.append("rect")
    	.attr("x", 0)
    	.attr("y", 0)
    	.attr("rx", 3)
    	.attr("width", "20em")
    	.attr("height", "1em")
    	.html(function(d, i) { return d.name; });

    g.append("text")
    	.text(function(d, i) { return d.name; })
    	.attr("x", 0)
    	.attr("y", 10)
    	.attr("dx", 3)
    	.attr("dy", 3);

    g.append("image")
    	.attr("width", 20)
    	.attr("height", 20)
    	.attr("x", "6em")
    	.attr("y", "1em")
    	.attr("class", "arrow")
    	.attr("xlink:href", "/iasess/images/kget_list.png");

    node.exit().remove();

    var line = svg.selectAll("line")
	.data(editlines.concat(lines));
    
    line.enter().append("line")
    	.attr("class", "edge")
    	.attr("x1", function(d, i) { return d.source.x;})
    	.attr("y1", function(d, i) { return d.source.y;})
    	.attr("x2", function(d, i) { return d.target.x;})
    	.attr("y2", function(d, i) { return d.target.y;})
    	.attr("stroke", "black");
    
    line.exit().remove();
}

function update() {
    d3.xhr("/iasess/mode/json")
	.header("Content-Type","application/x-www-form-urlencoded")
	.post("nodes=" + JSON.stringify(nodes)
	      + "&links=" + JSON.stringify(
		  _.map(lines,function(l) {return {tail: l.source.node.getAttribute("id"), 
						   head: l.target.node.getAttribute("id")}})));
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
	      "translate(" + (d.x = d3.event.x - 150) + "," + (d.y = d3.event.y) + ")");

    lines.forEach(function(k,v) {k.source.x = screen(0,0,k.source.node).x;
				 k.target.x = screen(0,0,k.target.node).x;
				 k.source.y = screen(0,0,k.source.node).y;
				 k.target.y = screen(0,0,k.target.node).y;
				});
    
    var line = svg.selectAll("line")
	.data(lines);
    line.attr("x1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).x + 150;})
    	.attr("y1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).y + 5;})
    	.attr("x2", function(d, i) { return localPoint(d.target.x,d.target.y,svgnode).x + 150;})
    	.attr("y2", function(d, i) { return localPoint(d.target.x,d.target.y,svgnode).y + 5;});
}

function dragmoveend(d) {
    d3.xhr("/iasess/mode/json")
	.header("Content-Type","application/x-www-form-urlencoded")
	.post("nodes=" + JSON.stringify(nodes));
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