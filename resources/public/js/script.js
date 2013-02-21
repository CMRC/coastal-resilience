//view-source:http://phrogz.net/svg/drag_under_transformation.xhtml
var svg   = document.getElementById('graph').getElementsByTagName('svg')[0];
var map   = document.getElementById('mainMap');
var svgNS = svg.getAttribute('xmlns');
var xlinkNS = 'http://www.w3.org/1999/xlink';
var xhtmlNS = 'http://www.w3.org/1999/xhtml';
var pt    = svg.createSVGPoint();
var fromNode;
var g = document.getElementById('graph0');

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
	    infotext("Information Panel: Select a connection strength, positive or negative, or delete this link. Refresh browser to cancel operation");
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
	    };
	    document.body.addEventListener('mousemove',onmove,false);
	}
    }
    return false;
},false);

document.body.addEventListener('contextmenu',function(e){
    if(e.target.parentNode.getAttribute('class') == 'node' ||
       e.target.parentNode.getAttribute('class') == 'edge')
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


svg.addEventListener('mouseover',function(e){
    if(fromNode) {
	if (e.target.parentNode.getAttribute('class') == 'node'
	    && fromNode != e.target.parentNode.firstChild.firstChild.nodeValue) {
	    m = e.target.parentNode.firstChild;
	    infotext("Information Panel: Click to connect " + fromNode + " to " +
		     m.firstChild.nodeValue);
	    while(m) {
		if(m.tagName == 'ellipse') {
		    var oldFill = m.getAttribute('fill');
		    var oldStroke = m.getAttribute('stroke');
		    m.setAttribute('fill',oldStroke);
		    m.setAttribute('stroke',oldFill);
		}
		m = m.nextSibling;
	    }
	} else {
	    infotext("Information Panel: Mouse over a node to connect to " + fromNode
		    + " or refresh your browser to cancel operation");
	}
    } else {
	infotext("Information Panel: Select a node to begin connecting. Right click on node to delete");
    }
},false);

map.addEventListener('mouseover',function(e){
    infotext("Information Panel: Drag to pan, scroll button to zoom, or select a feature for more information");
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

function submitform(fname,elem)
{
    if(elem && elem.options[elem.selectedIndex].innerHTML == "Custom...")
    {
	document.getElementById("newconcept").style.display = "block";
	document.getElementById("newconcept-in").style.display = "block";
	var lev = document.getElementById("level");
	for(var i = 0, j = lev.options.length; i < j; ++i) {
            if(lev.options[i].innerHTML === fname) {
		lev.selectedIndex = i;
		break;
            }
	}
	elem.options.selectedIndex = 0; //set it back to heading
    }
    else
	document.getElementById(fname).submit();
}
function hideconcept()
{
    document.getElementById("newconcept").style.display = "none";
    document.getElementById("newconcept-in").style.display = "none";
}

function infotext(text)
{
    document.getElementById("info-text").innerHTML = text;
}




var _gaq = _gaq || [];
_gaq.push(['_setAccount', 'UA-33996197-1']);
_gaq.push(['_trackPageview']);

(function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
})();