//view-source:http://phrogz.net/svg/drag_under_transformation.xhtml
var svg   = document.getElementsByTagName('svg')[0];
var svgNS = svg.getAttribute('xmlns');
var xlinkNS = 'http://www.w3.org/1999/xlink';
var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var pt    = svg.createSVGPoint();
var fromNode;
var g = document.getElementById('graph1');

function cursorPoint(evt,tgt){
    pt.x = evt.clientX; 
    pt.y = evt.clientY;
    return pt.matrixTransform(tgt.getScreenCTM().inverse());
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
	    menu.style.left = e.pageX + 'px';
	    menu.style.top = e.pageY + 'px';
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
	    var lg = document.createElementNS(svgNS,'g');
	    var n = document.createElementNS(svgNS,'line');
	    lg.appendChild(n);
	    n.setAttribute('id', 'arrow');
	    n.setAttribute('transform', 'translate(' + elementStart.x + ','
			   + elementStart.y + ')');
	    n.setAttribute('x1',0);
	    n.setAttribute('y1',0);
	    n.setAttribute('x2',1);
	    n.setAttribute('y2',1);
	    n.setAttribute('stroke', 'black');
	    g.insertBefore(lg,svg.querySelectorAll('.node')[0]);
	    onmove = function(evt){
		var current = cursorPoint(evt,n);
		n.setAttribute('x1',current.x);
		n.setAttribute('y1',current.y);
		n.setAttribute('x2',0);
		n.setAttribute('y2',0);
		// lg.setAttribute('transform',
		// 	       ' translate(' + elementStart.x + ',' + elementStart.y + ')' + 
		// 	       ' scale(' + Math.sqrt((pt.x * pt.x) + (pt.y * pt.y)) + ') ' +
		// 	       '
		//rotate(' + (((180 * Math.atan(pt.y / pt.x)) / Math.PI) - 45) + ')');
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
	deletemenu.style.left = e.pageX + 'px';
	deletemenu.style.top = e.pageY + 'px';
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

function submitform(fname)
{
    document.getElementById(fname).submit();
}

