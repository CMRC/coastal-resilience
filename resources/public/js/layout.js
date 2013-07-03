var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var svg = d3.select("#graph").append("svg")
    .attr("id", "fcm")
    .attr("width", "1000")
    .attr("height", "1000")
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
    .on("drag", dragmove);

document.body.addEventListener('click',function(e){
    if(e.target.getAttribute('class') == 'arrow') {
	if(editlines.length == 0)
	    editlines.push({source: {x:e.clientX, y:e.clientY, node: e.target.parentNode}, 
			    target: {x:e.clientX, y:e.clientY, node: e.target.parentNode}});
	else {
	    lines.push({source: editlines.pop().source, target: 
			{x:e.pageX, y:e.pageY, node:e.target.parentNode}});
	}
    } else if (e.target.getAttribute('class') == 'menuitem') {
	nodes.push({name:e.target.innerText, x:400,y:100});
	d3.xhr("/iasess/mode/json")
	    .header("Content-Type","application/x-www-form-urlencoded")
	    .post("nodes=" + JSON.stringify(nodes));
	svgnode.removeChild(foreign);
    } 
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

    svg.selectAll(".edge, .node").sort(function (a, b) {
	if (a.class == "edge" && b.class == "node") return -1; 
	else return 1;                    
    });
},false)

function addnode(elem) {
    var nodename = elem.options[elem.selectedIndex].innerHTML;
    nodes.push({name:nodename, x:400, y:100});
    var node = svg.selectAll("g.node")
	.data(nodes);
    
    var textnode = node.enter().append("g")
	.attr("transform", function(d, i) { return "translate(" + d.x + "," + d.y + ")"; })
	.attr("class", "node")
	.on("mouseover", function(e) {textnode.attr("class", "activenode");})
	.on("mouseout", function(e) {textnode.attr("class", "node");})
	.call(drag);

    textnode
	.append("rect")
	.attr("x", 0)
	.attr("y", 0)
	.attr("rx", 3)
	.attr("width", "20em")
	.attr("height", "1em")
	.html(function(d, i) { return d.name; });

    textnode.append("text").text(nodename)
	.attr("x", 0)
	.attr("y", 10)
	.attr("dx", 3)
	.attr("dy", 3);

    textnode.append("image")
    	.attr("class", "arrow")
	.attr("width", 20)
	.attr("height", 20)
	.attr("x", "6em")
	.attr("y", "1em")
	.attr("xlink:href", "/iasess/images/kget_list.png");
}

var screen = function(x,y,target) {
    var pt = svgnode.createSVGPoint();
    pt.x = x;
    pt.y = y;
    return pt.matrixTransform(target.getScreenCTM());};

document.body.addEventListener('mousemove',function(e){
    var line = svg.selectAll("line")
	.data(editlines);
    
    line.attr("x1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).x;})
	.attr("y1", function(d, i) { return localPoint(d.source.x,d.source.y,svgnode).y;})
	.attr("x2", function(d, i) { return localPoint(e.clientX,e.clientY,svgnode).x;})
	.attr("y2", function(d, i) { return localPoint(e.clientX,e.clientY,svgnode).y;});
},false);

function dragmove(d) {
    console.log(d3.event);
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