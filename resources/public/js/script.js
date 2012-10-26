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
		     var link = document.createElementNS(svgNS,'a');
		     link.setAttributeNS(xlinkNS,'href',url);
		     link.setAttributeNS(xlinkNS,'title',el["strength"]);
		     var menu = document.createElementNS(svgNS,'text');
		     var menubg = document.createElementNS(svgNS,'rect');
		     var medium = document.createTextNode(el["strength"]);
                     var height = 24;
                     var width = 60;
		     menu.setAttribute('x',endPos.x+width/2);
		     menu.setAttribute('y',endPos.y+height/2+height*j);
		     menu.setAttribute('text-anchor','middle');
		     menubg.setAttribute('x',endPos.x);
		     menubg.setAttribute('y',endPos.y+height*j);
		     menubg.setAttribute('fill','white');
		     menubg.setAttribute('stroke','black');
		     menubg.setAttribute('width',width);
		     menubg.setAttribute('height',height);
		     
		     menu.appendChild(medium);
		     link.appendChild(menubg);
		     link.appendChild(menu);
		     g.appendChild(link);
		     ++j;
		 });
	    fromNode = null;
	}
	else {
	    var mouseStart   = cursorPoint(e);
	    fromNode = e.target.parentNode.firstChild.firstChild.nodeValue;
	    m = e.target.parentNode.firstChild;
	    while(m) {
		if(m.tagName == 'ellipse') {
		    m.setAttribute('fill','white');
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
	    g.insertBefore(n,svg.getElementById('node1'));
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
	var deletewrapper = document.createElementNS(svgNS,'foreignObject');
	deletewrapper.setAttribute('x',cursorPoint(e).x);
	deletewrapper.setAttribute('y',cursorPoint(e).y);
	deletewrapper.setAttribute('width','300em');
	deletewrapper.setAttribute('height','1em');
	var deletemenu = document.createElementNS(xhtmlNS,'ul');
	deletemenu.style.left = e.clientX + 'px';
	deletemenu.style.top = e.clientY + 'px';
	deletemenu.style.position = 'absolute';
	var deleteitem = document.createElementNS(xhtmlNS,'li');
	var deletetext = document.createTextNode('X');
	deletelink.appendChild(deletetext);
	deleteitem.appendChild(deletelink);
	deletemenu.appendChild(deleteitem);
	deletewrapper.appendChild(deletemenu);
	document.body.appendChild(deletemenu);
    }
    e.preventDefault();
    return false;
},false)