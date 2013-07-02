var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var svg = d3.select("#graph1").append("svg")
    .attr("width", "1000")
    .attr("height", "1000")
    .attr("xmlns","http://www.w3.org/2000/svg");
var svgnode = document.getElementsByTagName('svg')[0];

var svgNS = svg.attr('xmlns');

var nodes = [];
var lines = [];
var editlines = [];
var foreign;

var drag = d3.behavior.drag()
    .origin(Object)
    .on("drag", dragmove);

document.body.addEventListener('click',function(e){
    if(e.target.getAttribute('class') == 'arrow') {
	if(editlines.length == 0)
	    editlines.push({source: {x:e.pageX, y:e.pageY, node: e.target.parentNode}, 
			    target: {x:e.pageX, y:e.pageY, node: e.target.parentNode}});
	else {
	    lines.push({source: editlines.pop().source, target: 
			{x:e.pageX, y:e.pageY, node:e.target.parentNode}});
	}
    } else if (e.target.getAttribute('class') == 'menuitem') {
	nodes.push({name:e.target.innerText, x:e.pageX,y:e.pageY});
	d3.xhr("/iasess/mode/json")
	    .header("Content-Type","application/x-www-form-urlencoded")
	    .post("nodes=" + JSON.stringify(nodes));
	svgnode.removeChild(foreign);
    } else {
	var menu = document.createElementNS(xhtmlNS,'ul');
	menu.style.left = e.pageX + 'px';
	menu.style.top = e.pageY + 'px';
	menu.style.position = 'absolute';
	menu.setAttribute('class','menu');
	foreign = document.createElementNS(svgNS,'foreignObject');
	foreign.setAttribute("width", "100");
	foreign.setAttribute("height", "100");
	var j=0;
        [{name:'Driver'},
	 {name:'Welfare'}].map(
		 function(el) {
		     var menuitem = document.createElementNS(xhtmlNS,'li');
		     var namestr = el["name"];
		     var name = document.createTextNode(namestr);
		     
		     var link = document.createElementNS(xhtmlNS,'a');
		     link.setAttribute('class','menuitem');
		     link.appendChild(name);
		     menuitem.appendChild(link);
		     menu.appendChild(menuitem);
		     ++j;
		 });
	var body = document.createElementNS(xhtmlNS,'body');
	body.setAttribute("xmlns",xhtmlNS);
	foreign.appendChild(body);
	body.appendChild(menu);
	svgnode.appendChild(foreign);
	
    }
    var node = svg.selectAll("g.node")
	.data(nodes);
    var line = svg.selectAll("line")
	.data(editlines.concat(lines));
	      
    line.enter().append("line")
    	.attr("class", "edge")
    	.attr("x1", function(d, i) { return d.source.x;})
    	.attr("y1", function(d, i) { return d.source.y;})
    	.attr("x2", function(d, i) { return d.target.x;})
    	.attr("y2", function(d, i) { return d.target.y;})
    	.attr("stroke", 1)
    	.attr("marker-end","url(#Triangle)");
    
    var textnode = node.enter().append("g")
	.call(drag)
	.attr("transform", function(d, i) { return "translate(" + d.x + "," + d.y + ")"; })
	.attr("class", "node")
	.on("mouseover", function(e) {textnode.attr("class", "activenode");})
	.on("mouseout", function(e) {textnode.attr("class", "node");});

    textnode
	.append("rect")
	.attr("x", 0)
	.attr("y", 0)
	.attr("rx", 3)
	.attr("width", "14em")
	.attr("height", "1em")
	.html(function(d, i) { return d.name; });

    textnode.append("text").text("blah blah blah")
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
    
    svg.selectAll(".edge, .node").sort(function (a, b) {
	if (a.class == "edge" && b.class == "node") return -1; 
	else return 1;                    
    });
    
    line.exit().remove();
    console.log(e);
},false);

document.body.addEventListener('mousemove',function(e){
    var line = svg.selectAll("line")
	.data(editlines);
    line.attr("x1", function(d, i) { return d.source.x;})
	.attr("y1", function(d, i) { return d.source.y;})
	.attr("x2", e.pageX)
	.attr("y2", e.pageY);
},false);

function dragmove(d) {
    d3.select(this)
	.attr("transform", d.transform = "translate(" + (d.x = d3.event.x) + "," + (d.y = d3.event.y) + ")");

    lines.forEach(function(k,v) {k.source.x = k.source.node.getAttribute("x");
				 k.target.x = k.target.node.getAttribute("x");
				 k.source.y = k.source.node.getAttribute("y");
				 k.target.y = k.target.node.getAttribute("y");
				});

    var line = svg.selectAll("line")
	.data(lines);
    var pt    = svgnode.createSVGPoint();
    var screen = function(s) {
	pt.x = s.x;
	pt.y = s.y;
	return pt.matrixTransform(s.node.getScreenCTM());};
    line.attr("x1", function(d, i) { return screen(d.source).x;})
    	.attr("y1", function(d, i) { return screen(d.source).y;})
    	.attr("x2", function(d, i) { return screen(d.target).x;})
    	.attr("y2", function(d, i) { return screen(d.target).y;})
    	.attr("stroke", 1);
}