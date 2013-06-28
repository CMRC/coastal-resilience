var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var svg = d3.select("#graph1").append("svg")
    .attr("width", "1000")
    .attr("height", "1000")
    .attr("xmlns","http://www.w3.org/2000/svg");
var svgNS = svg.attr('xmlns');

var nodes = [];
var lines = [];
var editlines = [];
var foreign;

document.body.addEventListener('click',function(e){
    if(e.target.getAttribute('class') == 'node') {
	if(editlines.length == 0)
	    editlines.push({start: e, finish: e});
	else
	    lines.push({start: editlines.pop().start, finish: e});
    } else if (e.target.getAttribute('class') == 'menuitem') {
	nodes.push({name:e.target.innerText, x:e.pageX,y:e.pageY});
	d3.xhr("/iasess/mode/json")
	    .header("Content-Type","application/x-www-form-urlencoded")
	    .post("nodes=" + JSON.stringify(nodes));
	document.getElementsByTagName('svg')[0].removeChild(foreign);
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
		     // menuitem.addEventListener('click',function(e){
		     // 	 nodes.push({name:namestr, x:e.pageX,y:e.pageY});
		     // 	 d3.xhr("/iasess/mode/json")
		     // 	     .header("Content-Type","application/x-www-form-urlencoded")
		     // 	     .post("nodes=" + JSON.stringify(nodes));
		     // 	 document.getElementsByTagName('svg')[0].removeChild(foreign);
		     // },false);
		     menu.appendChild(menuitem);
		     ++j;
		 });
	var body = document.createElementNS(xhtmlNS,'body');
	body.setAttribute("xmlns",xhtmlNS);
	foreign.appendChild(body);
	body.appendChild(menu);
	document.getElementsByTagName('svg')[0].appendChild(foreign);
	
    }
    var node = svg.selectAll("text")
	.data(nodes);
    var line = svg.selectAll("line")
	.data(editlines.concat(lines));
	      
    node.enter().append("text")
	.attr("class", "node")
	.attr("x", function(d, i) { return d.x - 50; })
	.attr("y", function(d, i) { return d.y - 12;})
	.attr("fill", "black")
	.text(function(d, i) { return d.name; });
    
    line.enter().append("line")
	.attr("x1", function(d, i) { return d.start.pageX;})
	.attr("y1", function(d, i) { return d.start.pageY;})
	.attr("x2", function(d, i) { return d.finish.pageX;})
	.attr("y2", function(d, i) { return d.finish.pageY;})
	.attr("stroke", 1);
    line.exit().remove();
},false);

document.body.addEventListener('mousemove',function(e){
    var line = svg.selectAll("line")
	.data(editlines);
    line.attr("x1", function(d, i) { return d.start.pageX;})
	.attr("y1", function(d, i) { return d.start.pageY;})
	.attr("x2", e.pageX)
	.attr("y2", e.pageY);
},true);