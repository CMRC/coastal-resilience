//view-source:http://phrogz.net/svg/drag_under_transformation.xhtml
var svg   = document.getElementsByTagName('svg')[0];
var svgNS = svg.getAttribute('xmlns');
var xlinkNS = 'http://www.w3.org/1999/xlink';
var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var pt    = svg.createSVGPoint();
var fromNode;
var g = document.getElementById('graph1');

function cursorPoint(evt,tgt){
    pt.x = evt.clientX; pt.y = evt.clientY;
    return pt.matrixTransform(evt.target.getScreenCTM().inverse());
}

var onmove;

document.body.addEventListener('click',function(e){
    if(e.target.parentNode.getAttribute('class') == 'node') {
	if(fromNode) {
	    document.body.removeEventListener('mousemove',onmove,false);
	    document.body.removeEventListener('mouseup',onmouseup,false);
	    m = e.target.parentNode.firstChild;
	    while(m) {
		if(m.tagName == 'ellipse') {
		    var endPos = { x:m['cx'].animVal.value, y:m['cy'].animVal.value };
		}
		m = m.nextSibling;
	    }
	    var menu = document.createElementNS(xhtmlNS,'ul');
	    menu.style.left = e.clientX + 'px';
	    menu.style.top = e.clientY + 'px';
	    menu.style.position = 'absolute';
	    menu.setAttribute('class','menu');
	    var j=0;
            [{strength:'S+',index:6},
	     {strength:'M+',index:5},
	     {strength:'W+',index:4},
	     {strength:'W-',index:2},
	     {strength:'M-',index:1},
	     {strength:'S-',index:0},
	     {strength:'Delete',index:3}].map(
		 function(el) {
		     var url = 'save/' + fromNode + '/' +
			 e.target.parentNode.firstChild.firstChild.nodeValue + '/' + el["index"];
		     var link = document.createElementNS(xhtmlNS,'a');
		     link.setAttribute('href',url);
		     var menuitem = document.createElementNS(xhtmlNS,'li');
		     var strength = document.createTextNode(el["strength"]);
		     
		     link.appendChild(strength);
		     menuitem.appendChild(link);
		     menu.appendChild(menuitem);
		     ++j;
		 });
	    document.body.appendChild(menu);
	    fromNode = null;
	}
	else {
	    var mouseStart   = cursorPoint(e);
	    fromNode = e.target.parentNode.firstChild.firstChild.nodeValue;
	    m = e.target.parentNode.firstChild;
	    while(m) {
		if(m.tagName == 'ellipse') {
		    var oldFill = m.getAttribute('fill');
		    var oldStroke = m.getAttribute('stroke');
		    m.setAttribute('fill',oldStroke);
		    m.setAttribute('stroke',oldFill);
		    var elementStart = { x:m['cx'].animVal.value, y:m['cy'].animVal.value };
		}
		m = m.nextSibling;
	    }
	    var n = document.createElementNS(svgNS,'line');
	    n.setAttribute('id', 'arrow');
	    n.setAttribute('x1',mouseStart.x);
	    n.setAttribute('y1',mouseStart.y);
	    n.setAttribute('x2',elementStart.x);
	    n.setAttribute('y2',elementStart.y);
	    n.setAttribute('stroke', 'black');
	    g.insertBefore(n,svg.querySelectorAll('.node')[0]);
	    onmove = function(e){
		var current = cursorPoint(e);
		pt.x = current.x - mouseStart.x;
		pt.y = current.y - mouseStart.y;
		n.setAttribute('x1',mouseStart.x+pt.x);
		n.setAttribute('y1',mouseStart.y+pt.y);
		n.setAttribute('x2',elementStart.x);
		n.setAttribute('y2',elementStart.y);
	    };
	    document.body.addEventListener('mousemove',onmove,false);
	}
    }
    return false;
},false);

document.body.addEventListener('contextmenu',function(e){
    if(e.target.parentNode.getAttribute('class') == 'node')
    {
	var deleteurl = 'delete/' + e.target.parentNode.firstChild.firstChild.nodeValue;
	var deletelink = document.createElementNS(xhtmlNS,'a');
	deletelink.setAttribute('href',deleteurl);
	var deletemenu = document.createElementNS(xhtmlNS,'ul');
	deletemenu.style.left = e.clientX + 'px';
	deletemenu.style.top = e.clientY + 'px';
	deletemenu.style.position = 'absolute';
	deletemenu.setAttribute('class','menu');
	var deleteitem = document.createElementNS(xhtmlNS,'li');
	var deletetext = document.createTextNode('Delete');
	deletelink.appendChild(deletetext);
	deleteitem.appendChild(deletelink);
	deletemenu.appendChild(deleteitem);
	document.body.appendChild(deletemenu);
    }
    e.preventDefault();
    return false;
},false)


document.body.addEventListener('mouseover',function(e){
    if(fromNode && e.target.parentNode.getAttribute('class') == 'node') {
	m = e.target.parentNode.firstChild;
	while(m) {
	    if(m.tagName == 'ellipse') {
		var oldFill = m.getAttribute('fill');
		var oldStroke = m.getAttribute('stroke');
		m.setAttribute('fill',oldStroke);
		m.setAttribute('stroke',oldFill);
	    }
	    m = m.nextSibling;
	}
    }
},false);

document.body.addEventListener('mouseout',function(e){
    if(fromNode && e.target.parentNode.getAttribute('class') == 'node'
       && fromNode != e.target.parentNode.firstChild.firstChild.nodeValue) {
	m = e.target.parentNode.firstChild;
	while(m) {
	    if(m.tagName == 'ellipse') {
		var oldFill = m.getAttribute('fill');
		var oldStroke = m.getAttribute('stroke');
		m.setAttribute('fill',oldStroke);
		m.setAttribute('stroke',oldFill);
	    }
	    m = m.nextSibling;
	}
    }
},false);
