//view-source:http://phrogz.net/svg/drag_under_transformation.xhtml
var svg   = document.getElementsByTagName('svg')[0];
var svgNS = svg.getAttribute('xmlns');
var xlinkNS = 'http://www.w3.org/1999/xlink';
var pt    = svg.createSVGPoint();
var fromElement;
var g = document.getElementById('graph1');

function cursorPoint(evt){
    pt.x = evt.clientX; pt.y = evt.clientY;
    return pt.matrixTransform(evt.target.getScreenCTM().inverse());
}

var onmove;
var onmouseup = function(e){
  if(e.target.parentNode.getAttribute('class') == 'node') {
      var mouseStart   = cursorPoint(e);
      fromElement = e.target.parentNode.firstChild.firstChild.nodeValue;
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

document.body.addEventListener('mouseup',onmouseup,false);

document.body.addEventListener('mousedown',function(e){
    if(e.target.parentNode.getAttribute('class') == 'node'
       && fromElement) {
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
		var url = 'save/' + fromElement + '/' +
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
	fromElement = null;
    }
    return false;
},false);