var clientColor = "red";
var lanColor = "grey";
var chordBlue = "#1f77b4";
//Gets timezone information
var d = new Date();
var timezoneOffset = d.getTimezoneOffset();
var color = d3.scaleOrdinal(["#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"]);




//Various functions\\
//checks if link is equal to another even if src and dest are swaped
var linksEqual = function(src1, dest1, src2, dest2){
	if(src1 == src2 && dest1 == dest2){
		return true;
	}else if(src1 == dest2 && src2 == dest1){
		return true;
	}else{
		return false;
	}
}

//returns position of a link in the d3 object "link"
var findLink = function(src,dest){
	for(var i = 0; i < link["_groups"][0].length; i++){
		if(linksEqual(link["_groups"][0][i]["__data__"]["source"]["name"],link["_groups"][0][i]["__data__"]["target"]["name"],src,dest)){
			return i;
		}
	}
	return -1;
}

//changes color of node 
var changeNodeColor = function(name,color){
	for(var i = 0; i < node["_groups"][0].length; i++){
		if(name == node["_groups"][0][i]["__data__"]["name"]){
			d3.select(node["_groups"][0][i].getElementsByTagName("circle")[0]).style("fill", color);
		}
	}
}

//uses the routing table to make a path of names
var findPath = function(src,dest){
	var namepath = [];
	var srcPos = findNodebyIP(src);
	var destPos = findNodebyIP(dest);
	var destName = node["_groups"][0][destPos]["__data__"].name;
	var currName = node["_groups"][0][srcPos]["__data__"].name;
	if(srcPos == -1){
		return -1;
	}
	
	namepath.push(currName);
	while(routingTable[currName][destName] != destName){
		currName = routingTable[currName][destName];
		namepath.push(currName);
	}
	namepath.push(destName);
	return namepath;
}

var findNodebyName = function(name){
	for(var i = 0; i < node["_groups"][0].length; i++){
		if(name == node["_groups"][0][i]["__data__"]["name"]){
			return i;
		}
	}
	return -1;
}

var findNodebyIP = function(ip){
	
	for(var i = 0; i < node["_groups"][0].length; i++){
		for(var j = 0; j < node["_groups"][0][i]["__data__"].networks.length; j++){
			if(node["_groups"][0][i]["__data__"].networks[j].ip == ip){
				return i;
			}
		}
	}
	console.log("no node by that ip");
	return -1;
}

//creates green to red gradient for color of the links, 0 is green, 100 and above is red
function perc2color(perc) {
    if(perc > 100){
	perc = 100;
    }
    var r, g, b = 0;
    // HACK: this value is customized for the tree scenario and showing things at the fall PI meeting
    var lowerLimit = 15;
    if(perc < lowerLimit) {
	var multiplier = 255 / lowerLimit;
	g = 255;
	r = Math.round(multiplier * perc);
    }
    else {
	var remainingRange = 100 - lowerLimit;
	var modPerc = perc - lowerLimit;
	var multiplier = 255 / remainingRange;
	r = 255;
	g = Math.round(multiplier * modPerc);
    }
    var h = r * 0x10000 + g * 0x100 + b * 0x1;
    return '#' + ('000000' + h.toString(16)).slice(-6);
}

var regionIndex = function(region){
        for(var i = 0; i < regionNames.length; i++){
		if(regionNames[i] == region){
                        return i;
                }
	}
	console.log("No region by that name");
	return -1;
}




//Data importing functions\\

var portNames;
$.getJSON("webdata/portNames.json",function(data){
	portNames = data;
});
var routingTable;
$.getJSON("webdata/routingTable.json",function(data){
	routingTable = data;
});

//Loads netconfig.json which contains the link and node layout information
var netconfig;
var regionInfo;
$.getJSON("webdata/netconfig.json",function(data){
	netconfig = data;
	links = data.links;
	nodes = data.nodes;
    regionInfo = data.regionInfo;
    clientRegions = data.clientRegions;
	console.log(links);
	console.log(nodes);
        console.log(regionInfo);
        temp = Object.keys(regionInfo);
        console.log(temp);
        regionNames = [];

        //Gets a list of all the regions
        for(var i in temp){
                var bool = 1
                for(var j in regionNames){
                        if(regionNames[j] == regionInfo[temp[i]]){
                                bool = 0
                                break;
                        }
                }
                if(bool){
                        regionNames.push(regionInfo[temp[i]]);
                }
        }
        console.log(regionNames);
	var hasPos = true; //If netconfig is directly from topologyReader.py it won't have xy information and we have to treat it slightly differently
	if(data.nodes[0].x == undefined){
		hasPos = false;
	}
	refresh(hasPos);//This draws the network
});

//Saves the current configuration of the network map. Use this to rearange the graph and save it's position

document.getElementById("saveLayout").addEventListener("click",function(event){
    	var element = document.createElement('a');
	var savedata = {};
	var n = [];
	var l = [];
	for(var i = 0; i < node["_groups"][0].length; i++){
		n.push(node["_groups"][0][i]["__data__"]);
	}
	for(var i = 0; i < link["_groups"][0].length; i++){
		l.push(link["_groups"][0][i]["__data__"]);
						
	}
	for(var i = 0; i < l.length; i++){
		l[i].source = l[i].source.name;
		l[i].target = l[i].target.name;		
	}

	savedata.nodes = n;
	savedata.links = l;
	savedata.regionInfo = regionInfo;
	savedata.clientRegions = clientRegions;

	var text = JSON.stringify(savedata);

	
	element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
	element.setAttribute('download', "netconfig.json");

	element.style.display = 'none';
	document.body.appendChild(element);

	element.click();

	document.body.removeChild(element);
},false);

//Loads the flows from flowData.json and sets some global variables. It also creates the time slider and attempts to load nodeData.json

document.getElementById("loadFlows").addEventListener("click",function(event){
    	$.getJSON("webdata/flowData.json",function(data){
		console.log(data);
		for(var i = 0; i < data.length; i++){
			//TODO move to python scripts
			switch(data[i].proto){
				case 6:
					data[i].proto = "TCP"; break;
				case 17:
					data[i].proto = "UDP"; break;
				case 1:
					data[i].proto = "ICMP"; break;
				case 2:
					data[i].proto = "IGMP"; break;
				default:
					console.log("Proto: " + data[i].proto  + " not handled in switch");
			}
		}
		for(var i = 0; i < data.length; i++){
			var path = [];
			//finds the path that a flow will take
			path = findPath(data[i].src4_addr, data[i].dst4_addr);
			if(path != -1){
				//uses path to assign flow data and color to links
				for(var k = 0; k < path.length-1; k++){
					var pos = findLink(path[k],path[k+1]);
					link["_groups"][0][pos]["__data__"].flows.push({"bytes":data[i].in_bytes,"proto":data[i].proto,"src":data[i].src4_addr, "dest":data[i].dst4_addr, "startTime":data[i].t_first, "endTime": data[i].t_last,"flow":data[i]});
					link["_groups"][0][pos]["__data__"]["totalBytes"] += data[i].in_bytes;

				}
			}
		}
		
		trueStart = new Date(data[0]["t_first"]);		
		trueEnd = new Date(data[data.length-1]["t_last"]);
		//gives dates to all the data and finds the trueEnd
		for(var i = 0; i < data.length; i++){
			data[i]["startTime"] = new Date(data[i]["t_first"]);
			data[i]["endTime"] = new Date(data[i]["t_last"]);
			if(data[i].endTime.getTime() > trueEnd.getTime()){
				trueEnd = new Date(data[i].endTime);
			}
		}
		flowBackup = data;
		flowData = data;
		//creates the range slider for active time range
		$(function(){
		    var timeslider = $( "#slider" ).slider({
		      range: true,
		      min: trueStart.getTime(),
		      max: trueEnd.getTime(),
		      values: [ trueStart.getTime(), trueEnd.getTime()],
		      slide: function( event, ui ) { //Everytime slider moves call this function
			var left = new Date(ui.values[0]);
			var right = new Date(ui.values[1]);
			  narrowView(left,right,flowData); //Tells the visualizations the time bounds
			  syncTimeSliders(left,right);
		      }
		    });
		    displayTimeRange(trueStart, trueEnd);
		    displayCurrentTime(trueEnd);
		});
		
		
	});

	//Trys to load nodeData.json which is unnecessary for the visualization
	$.getJSON("webdata/nodeData.json",function(data){
		nodeData = data;
		for(var i = 0; i < nodeData.length; i++){
			nodeData[i].time = new Date(nodeData[i].time);
			nodeData[i].time.setMinutes(nodeData[i].time.getMinutes() + timezoneOffset); //Applies timezoneOffset to the timing of nodeData
		}
	});
},false);




//FLow Filters\\
//Filters stack and won't overwright each other
var flowFilters = []
var main = document.getElementById("main");//Selects the main visualization svg

//Overwrites the rightclick to bring of the flow filters
main.addEventListener("contextmenu",function(event){
    	event.preventDefault();
    	var ctxMenu = document.getElementById("ctxMenu");
    	ctxMenu.style.display = "block";
    	ctxMenu.style.left = (event.pageX - 10)+"px";
    	ctxMenu.style.top = (event.pageY - 10)+"px";
},false);

//Left clicking hides contextmenu 
main.addEventListener("click",function(event){
    	var ctxMenu = document.getElementById("ctxMenu");
    	ctxMenu.style.display = "";
   	ctxMenu.style.left = "";
    	ctxMenu.style.top = "";
},false);

//Adds a filter that filters flows to only those between the minimum and maximum
document.getElementById("includeSize").addEventListener("click",function(event){
	var min = prompt("Minimum in bytes:");
	var max = prompt("Maximum in bytes:");
	if(min == "" || max == ""){
		return false;
	}
	flowFilters.push({"type": "includeSize", "min": Number(min), "max": Number(max)});
	applyFilters();//Applies filters immediately
},false);

//Adds a filter that removes flows between the minimum and maximum
document.getElementById("excludeSize").addEventListener("click",function(event){
	var min = prompt("Minimum in bytes:");
	var max = prompt("Maximum in bytes:");
	if(min == "" || max == ""){
		return false;
	}
	flowFilters.push({"type": "excludeSize", "min": Number(min), "max": Number(max)});
	applyFilters();//Applies filters immediately
},false);

//Adds a filter that filters flows to only those with a source or destination port that matches what is given
document.getElementById("includePort").addEventListener("click",function(event){
	var port = prompt("Include Port:");
	if(port == ""){
		return false;
	}
	flowFilters.push({"type": "includePort", "port": port});
	applyFilters();//Applies filters immediately
},false);

//Adds a filter that removes flows with a source or destination port that matches what is given
document.getElementById("excludePort").addEventListener("click",function(event){
	var port = prompt("Exclude Port:");
	if(port == ""){
		return false;
	}
	flowFilters.push({"type": "excludePort", "port": port});
	applyFilters();//Applies filters immediately
},false);

//Removes all filters
document.getElementById("removeFilters").addEventListener("click",function(event){
	flowFilters = []
	applyFilters();
},false);

//Applies all filters to flowData
function applyFilters(){
	flowData = []
	for(var i = 0; i < flowBackup.length; i++){ //Creates a shallow copy
		flowData.push(flowBackup[i]);
	}
	console.log(flowFilters);
	console.log("Starting with " + flowData.length + " flows");

	//Loops through all filters and applies them to flowData
	for(var i = 0; i < flowFilters.length; i++){
		switch(flowFilters[i].type){
			case "includeSize":
				for(var j = 0; j < flowData.length; j++){
					if(flowData[j]["in_bytes"] < flowFilters[i].min || flowData[j]["in_bytes"] > flowFilters[i].max){
						flowData.splice(j, 1);
						j--;
					}
					
				}break;
			case "excludeSize":
				for(var j = 0; j < flowData.length; j++){
					if(flowData[j]["in_bytes"] >= flowFilters[i].min && flowData[j]["in_bytes"] <= flowFilters[i].max){
						flowData.splice(j, 1);
						j--;
					}
				}break;
			case "includePort":
				for(var j = 0; j < flowData.length; j++){
					if(flowData[j]["src_port"] != flowFilters[i].port && flowData[j]["dst_port"] != flowFilters[i].port){
						flowData.splice(j, 1);
						j--;
					}					
				}break;
			case "excludePort":
				for(var j = 0; j < flowData.length; j++){
					if(flowData[j]["src_port"] == flowFilters[i].port || flowData[j]["dst_port"] == flowFilters[i].port){
						flowData.splice(j, 1);
						j--;
					}
					
				}break;
		}
	}
	//Resets max y on graphs
	//numBytesMax = 0;
	//numFlowsMax = 0;
	//numBytes.maxY = 0;
	console.log("Ending with " + flowData.length + " flows");
	for(var i = 0; i < visualizations.length; i++){
		visualizations[i].maxY = 0;
	}
	narrowView(trueStart,trueEnd,flowData); //Update the visualizations with new filtered data
}









//Code for the main network graph\\

var width = window.innerWidth - 10, height = 900;
var networkGraph;
var nodes;
var links;
var sim;
var link;
var node;

//Sets the foundtation for refresh to add onto
function makeNetGraph(){
	
	networkGraph = d3.select("#main")
	networkGraph =	networkGraph.append("svg")
	    	.attr("width", width)
	    	.attr("height", height)
		.attr("class", "everything");	

	var div = d3.select("body").append("div")	
		.attr("class", "tooltip")				
		.style("opacity", 0);

	nodes = []
	links = []
	sim = d3.forceSimulation(nodes)
	    	.force("charge", d3.forceManyBody())
		.force("link", d3.forceLink(links).distance(50))
		.force("center", d3.forceCenter(width/2,height/2));

	sim
	      	.nodes(nodes)
	     	.stop();

	link;
	node;
	node = networkGraph.selectAll(".node");
	link = networkGraph.selectAll(".link");
	nodes = [];
	links = [];

	sim.on("tick", function() {
    	link.attr("x1", function(d) { return d.source.x; })
	.attr("y1", function(d) { return d.source.y; })
	.attr("x2", function(d) { return d.target.x; })
	.attr("y2", function(d) { return d.target.y; });
    	node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
	
});
}
makeNetGraph();

//redraws nodes and links
function refresh(hasPos){
	link = networkGraph.selectAll(".link")
	.data(links)
	.enter().append("line")
	.attr("id", "links")
	.attr("stroke", "lightgrey")
	.style("stroke-width", 5)
	.on("click", function(d){
		selectedLink = d;
		console.log("selected link: " + selectedLink);
	    updateGraphs(d,trueStart,trueEnd);
		displaySelectedLink(selectedLink);
		for(var i = 0; i < visualizations.length; i++){
			visualizations[i].maxY = 0;
		}
		for(var i = 0; i < otherWindows.length; i++){
			otherWindows[i].thisVis.maxY = 0;
			
		}
	})
	
	
	link.exit().remove();
	node = networkGraph.selectAll(".node")
	      	.data(nodes)
	    	.enter().append("g")
		.attr("id", "nodes")
	     	.attr("class", "node").merge(node)
		.call(d3.drag().on("drag", dragged));
		
	node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

	

	sim.nodes(nodes);

	for(var i = 0; i < link["_groups"][0].length; i++){
		for(var j = 0; j < node["_groups"][0].length; j++){
			if(link["_groups"][0][i]["__data__"].source == node["_groups"][0][j]["__data__"].name){
				link["_groups"][0][i]["__data__"].source = node["_groups"][0][j]["__data__"];
			}else if(link["_groups"][0][i]["__data__"].target == node["_groups"][0][j]["__data__"].name){
				link["_groups"][0][i]["__data__"].target = node["_groups"][0][j]["__data__"];
			}
		}
		link["_groups"][0][i]["__data__"]["totalBytes"] = 0;
	}

	node.append("circle")
		.attr("r","10")
		.attr("fill","green")


	node.append("text")
	      	.attr("dx", 12)
	.attr("dy", ".15em")
	.attr("fill", d => {
	    if(d.type == "lan") {
		return lanColor;
	    } else if(d.type == "client") {
		return clientColor;
	    }
	})
	.attr("stroke", "none")
	.attr("stroke-width", "0")
	.attr("font-weight", d => {
	    if(d.type == "lan") {
		return "normal";
	    } else if(d.type == "client") {
		return "bold";
	    } else {
		return "normal";
	    }
	})
	      	.text(function(d) { return d.name });
	node.on("click", function(d) {
		var str = "";
		for(var i = 0; i < d.networks.length; i++){
			str += d.networks[i].ip + "\n";
		}
		alert(str);
	});

    // color lans and clients
    for(var i = 0; i < node["_groups"][0].length; i++){
	if(node["_groups"][0][i]["__data__"]["type"] == "lan"){
	    d3.select(node["_groups"][0][i].getElementsByTagName("circle")[0]).style("fill", lanColor);
	}else if(node["_groups"][0][i]["__data__"]["type"] == "client"){
		d3.select(node["_groups"][0][i].getElementsByTagName("circle")[0]).style("fill", "blue");
	}
    }

    
	if(hasPos){
		sim.alpha(0).restart();
	}else{
		sim.force("link", d3.forceLink(links).distance(100));
		sim.alpha(.5).restart();
	}
	
}

//Adds border to main svg
var borderPath = networkGraph.append("rect")
		.attr("x", 0)
		.attr("y", 0)
		.attr("height", height)
		.attr("width", width)
		.style("stroke", "black")
		.style("fill", "none")
		.style("stroke-width", 2);

//allows nodes to be dragged around
function dragged(d) {
	console.log("in drag");
	d.x = d3.event.x, d.y = d3.event.y;
	d3.select(this).attr("cx", d.x).attr("cy", d.y);
	link.filter(function(l) { return l.source === d; }).attr("x1", d.x).attr("y1", d.y);
	link.filter(function(l) { return l.target === d; }).attr("x2", d.x).attr("y2", d.y);
	node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
}

document.getElementById("move").addEventListener("click",function(event){
	var title = prompt("Title of vis:");
	//var destination = "#" + prompt("name of slot you are moving to");
	for(var i = 0; i < visualizations.length; i++){
		if(visualizations[i].titleText == title){
			visualizations[i].move();
			visualizations.splice(i,1);
		}
	}

},false);

//Swap between main netgraph and chord diagram\\

var netgraph = true;

document.getElementById("swap").addEventListener("click",function(event){
	swap();
},false);

//Removes previous vis and adds other one
function swap(){
	d3.select("svg").remove();
	if(netgraph){
		makeChordDiagram();
		netgraph = false;
		narrowView(trueStart,trueEnd,flowData);
	}else{
		makeNetGraph();
		links = netconfig.links;
		nodes = netconfig.nodes;

		//hasPos tracks if loading something from SaveConfig or straight from parse.py
		var hasPos = true;
		if(nodes[0].x == undefined){
			hasPos = false;
		}
		refresh(hasPos);
		netgraph = true;
		var svg = d3.select("svg");
		//Adds border to main svg
		var borderPath = svg.append("rect")
			.attr("x", 0)
			.attr("y", 0)
			.attr("height", height)
			.attr("width", width)
			.style("stroke", "black")
			.style("fill", "none")
			.style("stroke-width", 2);
	}
	
}

//Set up for chordDiagram
var matrix = []
var chordHeight = height;
var chordDiagram;
var outerRadius = Math.min(width, chordHeight) * 0.30;
var innerRadius = outerRadius - 10;
var clientRegions;

//Makes Diagram with data
function makeChordDiagram(){
	chord = d3.chord()
	    .padAngle(.04)
	    .sortSubgroups(d3.descending)
	    .sortChords(d3.descending)
	ribbon = d3.ribbon()
	    .radius(innerRadius)

	//color = d3.scaleOrdinal(d3.schemeCategory10)
	  
	chordDiagram = d3.select("#main").append("svg")
	      .attr("viewBox", [-width / 2, -height / 2, width, chordHeight])
	      .attr("font-size", 10)
	      .attr("font-family", "sans-serif")
	      .style("width", width)
	      .style("height", chordHeight);
		
	  var chords = chord(matrix);

	  var group = chordDiagram.append("g")
	    .selectAll("g")
	    .data(chords.groups)
	    .join("g");

	  group.append("path")
	      .attr("fill", d => color(d.index))
	      .attr("stroke", d => color(d.index))
	      	.attr("d", d3.arc()
	      		.innerRadius(innerRadius)
	      		.outerRadius(outerRadius)
	    	);

	  group.append("text")
	      .each(d => { d.angle = (d.startAngle + d.endAngle) / 2; })
	      .attr("dy", ".35em")
	      .attr("transform", d => `
		rotate(${(d.angle * 180 / Math.PI - 90)})
		translate(${innerRadius + 26})
		${d.angle > Math.PI ? "rotate(180)" : ""}
	      `)
	      .attr("text-anchor", d => d.angle > Math.PI ? "end" : null)
		.attr("font-size", "20px")
	      .text(d => regionNames[d.index]);

	  chordDiagram.append("g")
	      .attr("fill-opacity", 0.67)
	    .selectAll("path")
	    .data(chords)
	    .join("path")
	      .attr("stroke", d => d3.rgb(color(d.source.index)).darker())
	      .attr("fill", d => color(d.source.index))
	      .attr("d", d3.ribbon()
	    		.radius(innerRadius)
		);	
}






//Timing And Replay Functionality\\
//Some global variables to keep track of timing and selection so everything can update at once
var flowBackup;
var flowData;
var trueStart;
var trueEnd;
var selectedSource;
var selectedTarget;
var selectedLink;
var nodeData;
var byteColorModifier = 400; //The total bytes on a link divided by this should roughly represent a precentage of how used the link is

//Is called whenever the time range changes
function narrowView(start,end,data) {
    //figure out what data show be showing up
    activeData = []
    for(var i = 0; i < data.length; i++){
	if(data[i].startTime.getTime() <= end.getTime() && data[i].endTime.getTime() >= start.getTime()){
	    activeData.push(data[i]);
	}
    }

    if(netgraph){
	var window;// = new Date();

	// size of the window in seconds
	window = (end.getTime() - start.getTime())/1000;
	
	// clear state of links
	for(var i = 0; i < link["_groups"][0].length; i++){
	    link["_groups"][0][i]["__data__"].flows = [];
	    link["_groups"][0][i]["__data__"]["totalBytes"] = 0;
	    d3.select(link["_groups"][0][i]).style("stroke", "lightgrey");
	}

	// compute total bytes and flows for each link
	for(var i = 0; i < activeData.length; i++){
	    var path = [];
	    path = findPath(activeData[i].src4_addr, activeData[i].dst4_addr);
	    if(path != -1){
		for(var k = 0; k < path.length-1; k++){
		    var pos = findLink(path[k],path[k+1]);
		    link["_groups"][0][pos]["__data__"].flows.push({"bytes":activeData[i].in_bytes,"proto":activeData[i].proto,"src":activeData[i].src4_addr, "dest":activeData[i].dst4_addr, "startTime":activeData[i].t_first, "endTime": activeData[i].t_last,"flow":activeData[i]});
		    link["_groups"][0][pos]["__data__"]["totalBytes"] += activeData[i].in_bytes; //Adds up the bytes on a link 

		}
	    } else {
		console.log("No path found between " + activeData[i].src4_addr + " and " + activeData[i].dst4_addr);
	    }
	}

	// update colors of the links
	for(var i = 0; i < link["_groups"][0].length; i++){
	    var totalBytes = link["_groups"][0][i]["__data__"]["totalBytes"];
	    var colorValue;
	    if( 0 == totalBytes) {
		colorValue = "lightgrey";
	    } else {
		var rawBandwidth = link["_groups"][0][i]["__data__"].bandwidth;
		var bandwidth = bandwidthAsBytesPerSecond(rawBandwidth);
		var colorPrec = (totalBytes / (bandwidth*window))*100;
		colorValue = perc2color(colorPrec);
	    }
	    // update link color
	    d3.select(link["_groups"][0][i]).style("stroke", colorValue);
	}
	
    }else{
	chordReplay(activeData);
    }
    
    //gives all the graphs the new active dataset
    updateGraphs(selectedLink,new Date(start),new Date(end));
}


var simTime;
var play = false;
var replayWindow = 60;
var sps = 1;

document.getElementById("replay").addEventListener("click",function(event){
	play = true;
	replayWindow = prompt("Window Size (secs):"); //Gets the window/tail length
	if(replayWindow == ""){
		replayWindow = 60;
	}
	sps = prompt("Seconds per Second:"); //Deturmines how fast the replay goes in relation to real time
	if(sps == ""){
		sps = 1;
	}
	simTime = new Date(trueStart);
	replay();
},false);

//Allows a replay to be paused and then restarted
var pause = d3.select("#pause");
pause.text("Pause");
pause.on("click", function(){
	if(play){
		pause.text("Play");
		play = false;
	}else{
		pause.text("Pause");
		play = true;
		replay();
	}

});

//Replay time controler
function replay(){
	for(var i = 0; i < visualizations.length; i++){
		visualizations[i].maxY = 0;
	}
	var simStop = new Date(trueEnd);
	var time = function(){ //Recurisive funtion that runs every playbackSpeed milliseconds, default is 50ms
		setTimeout(function(){
			if(simTime.getTime() < simStop.getTime() && play){
				var step = (sps*1000)/(1000/playbackSpeed) //Calculates time step based on the seconds per second and how often this function runs
				simTime.setMilliseconds(simTime.getMilliseconds() + step);				
				
			    var timeWindowStart = new Date(simTime.getTime() - 1000*replayWindow);
			    var timeWindowEnd = new Date(simTime);
			    narrowView(timeWindowStart, timeWindowEnd, flowData, true); //Trigger to update visualizations
			    
			    syncTimeSliders(timeWindowStart, timeWindowEnd);
				if(netgraph){
					//nodeReplay(simTime); //currently no data
				}
				time();
			}
		}, playbackSpeed);
	}
	time();			
};

//Function that handels updating the sliders and it's text
function updateTimeSlider(left, right){
	$(function(){
		$( "#slider" ).slider("values",[ left.getTime(), right.getTime()]); //updates time slider
	});
	displayTimeRange(left, right);
	displayCurrentTime(right);
}

function syncTimeSliders(left,right){
	updateTimeSlider(left,right);
	for(var i = 0; i < otherWindows.length; i++){
		try{
			otherWindows[i].updateTimeSlider(left,right);
		}catch(err){
			console.log("window not ready or gone");
		}
		
	}
}

//Updates the chordReplay from dataset
function chordReplay(activeData){
	//creates and populates matrix with data
	matrix = [];
	for(var i = 0; i < regionNames.length; i++){
		(arr = []).length = regionNames.length; arr.fill(0);
		matrix.push(arr);
	}
	for(var i = 0; i < activeData.length; i++){
		srcIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["src4_addr"])]["__data__"].name]);
		destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["dst4_addr"])]["__data__"].name]);
		matrix[srcIndex][destIndex] += activeData[i].in_bytes;
		matrix[destIndex][srcIndex] += activeData[i].in_bytes;
	}
	
	//Removes old chord diagram and completely recreates it
	d3.select("svg").remove();
	chord = d3.chord()
	    .padAngle(.04)
	    .sortSubgroups(d3.descending)
	    .sortChords(d3.descending)
	ribbon = d3.ribbon()
	    .radius(innerRadius)

	//color = d3.scaleOrdinal(d3.schemeCategory10)
	  
	chordDiagram = d3.select("#main").append("svg")
	      .attr("viewBox", [-width / 2, -height / 2, width, chordHeight])
	      .attr("font-size", 10)
	      .attr("font-family", "sans-serif")
	      .style("width", width)
	      .style("height", chordHeight);
		
	  	var chords = chord(matrix);

	  	var group = chordDiagram.append("g")
			.selectAll("g")
			.data(chords.groups)
			.join("g");

	  	group.append("path")
	      	.attr("fill", d => {
				var regionName = regionNames[d.index];
				if(clientRegions.includes(regionName)) {
					return chordBlue;
				} else {
					return color(d.index);
				}
			})
	      	.attr("stroke", d => {
				var regionName = regionNames[d.index];
				if(clientRegions.includes(regionName)) {
					return chordBlue;
				} else {
					return color(d.index);
				}
			})
	      	.attr("d", d3.arc()
	      		.innerRadius(innerRadius)
	      		.outerRadius(outerRadius)
	    	);

    group.append("text")
	.each(d => { d.angle = (d.startAngle + d.endAngle) / 2; })
	.attr("dy", ".35em")
	.attr("transform", d => `
		rotate(${(d.angle * 180 / Math.PI - 90)})
		translate(${innerRadius + 26})
		${d.angle > Math.PI ? "rotate(180)" : ""}
	      `)
	.attr("text-anchor", d => d.angle > Math.PI ? "end" : null)
	.attr("font-size", "20px")
	.attr("font-weight", d => {
	    var regionName = regionNames[d.index];
	    if(clientRegions.includes(regionName)) {
		return "bold";
	    } else {
		return "normal";
	    }
	})
	.attr("fill", d => {
	    var regionName = regionNames[d.index];
	    if(clientRegions.includes(regionName)) {
		return clientColor;
	    } else {
		return "black";
	    }
	})
	.attr("stroke", "none")
	.attr("stroke-width", "0")
	.text(d => regionNames[d.index]);

	chordDiagram.append("g")
	    .attr("fill-opacity", 0.67)
	    .selectAll("path")
	    .data(chords)
	    .join("path")
	    .attr("stroke", d => {
			var regionName = regionNames[d.index];
			if(clientRegions.includes(regionName)) {
				return (chordBlue);
			} else {
				return d3.rgb(color(d.source.index)).darker();
			}
		})
	    .attr("fill", d => {
			var regionName = regionNames[d.source.index];
			if(clientRegions.includes(regionName)) {
				return chordBlue;
			} else {
				return color(d.source.index);
			}
		})
	    .attr("d", d3.ribbon()
	    	.radius(innerRadius)
		);

}


//Finds the corresponding timestamp with the node usage info at that point and applies it
var nodeColorModifier = 500
function nodeReplay(time){
	//Sets all nodes to default green
	for(var i = 0; i < node["_groups"][0].length; i++){
		d3.select(node["_groups"][0][i].getElementsByTagName("circle")[0]).style("fill", "green");
	}
	var i = 0;
	while(i < nodeData.length && time.getTime() > nodeData[i].time.getTime()){ //Moves through nodeData until we arrive at the current timestamp
		i++
	}
	if(i != nodeData.length){	
		for(var key in nodeData[i]){ //For every node with usage data update its color
			if(key != "time"){
				if(nodeData[i][key] != 0){
					var perc = nodeData[i][key]*nodeColorModifier; //calculate node color percent
					changeNodeColor(key,perc2color(perc));
				}
			}
		}
	}
}




//Graph Functions\\

var granularity = 1000;	//Number of data points in graphs
var playbackSpeed = 50; //Affects speed of sim


function updateGraphs(data,start,end){
	//if data is not empty update graphs
	if(data != undefined){
		for(var i = 0; i < visualizations.length; i++){
			visualizations[i].update(data,start,end);
		}
		for(var i = 0; i < otherWindows.length; i++){
			try{
				console.log("Updating External Window");
				otherWindows[i].thisVis.update(data,start,end);
				otherWindows[i].redraw();
			}catch(err){
				console.log("window not ready or gone");
			}
		}
	}else{
		console.log("no Data");
	}
}


//Changes the amount of points plotted on a graph

document.getElementById("setDetail").addEventListener("click",function(event){
	var response = prompt("How many data points:");
	if(response != null){
		granularity = response;
		//granularity affects playbackSpeed, the smaller the granularity the less time it takes to update the graphs and we can afford to run the sim clock faster
		playbackSpeed = .1 * granularity;
		if(playbackSpeed < 50){ //Under 50 and the updating redrawing becomes an issue
			playbackSpeed = 50;
		}
		if(playbackSpeed > 200){ //Anything over 200 doesn't look great and it is likely that the sim clock will be off anyways
			playbackSpeed = 200;
		}
		for(var i = 0; i < visualizations.length; i++){
			visualizations[i].maxY = 0;
		}
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
},false);

//Classes\\
//Main parent class for all the graphs 
class Vis {
	constructor(svg,title){
		this.margin = {top: 50, right: 50, bottom: 50, left: 50};
		this.width = 798 - this.margin.left - this.margin.right 
		this.height = 400 - this.margin.top - this.margin.bottom;
		this.data = []
		this.titleText = title;
		this.svgText = svg;
		this.svg = d3.select(svg).append("svg")
		   	.attr("width", this.width + this.margin.left + this.margin.right)
		    	.attr("height", this.height + this.margin.top + this.margin.bottom)
			.attr("class", "graph")
			.attr("id", "svg")
			.append("g")
		    		.attr("transform","translate(" + this.margin.left + "," + this.margin.top + ")");
		this.title = this.svg.append("text")
        		.attr("x", (this.width / 2))             
        		.attr("y", 0 - (this.margin.top / 2))
        		.attr("text-anchor", "middle")  
        		.style("font-size", "16px") 
        		.style("text-decoration", "underline")  
        		.text(this.titleText);
	}
}

//This class will create a pie chart
class pieChart extends Vis {
	//Svg tells what div the chart should display in, title gives the title, and makeData is a function that returns the data to build the chart
	//The data that makeData returns should be a object with the keys as the slice labels and the value in the keys as a number. 
	constructor(svg, title, makeData){
		super(svg,title);
		this.svg.transition() //Need to override the default position of the transform so the pie is centered
      			.duration(10)
      			.attr("transform", "translate(" + (this.width/2 + this.margin.left) + "," + (this.height/2 +this.margin.top) +")")
		this.title.transition()
			.duration(10)
      			.attr("transform", "translate(" + -this.width/2 + "," + -this.height/2 +")")

		this.makeData = makeData;
		this.radius = Math.min(this.width, this.height) / 2 
	}
	update(flows,start,end){
		this.data = this.makeData(flows);
		this.pie = d3.pie()
			.value(function(d) {return d.value; })
		this.data_ready = this.pie(d3.entries(this.data))
		this.arcGenerator = d3.arc()
  			.innerRadius(0)
  			.outerRadius(this.radius);
		var local_arcGenerator = this.arcGenerator; //Creates local copy because the definition of "this" will be different  
		this.slices = this.svg.selectAll("path")
	    		.data(this.data_ready);

		this.slices
	    		.exit()
	    		.remove()
		this.slices
			.enter()
			.append('path')
    			.merge(this.slices)
    			.attr('d', local_arcGenerator)
    			.attr('fill', function(d){ return(color(d.data.key)) })
			.on("click",function(d){ //Makes a popup with information about the slice you clicked pop up 
				var h = "40";
				var name = "none";
				if(d["data"].key != "other" && d["data"].key != "icmp"){
					if(portNames[d["data"].key]["tcp"] != undefined){
						if(portNames[d["data"].key]["udp"] != undefined){
							if(portNames[d["data"].key]["tcp"].name != portNames[d["data"].key]["udp"].name){
								name = portNames[d["data"].key]["tcp"].name + " or " + portNames[d["data"].key]["udp"].name;
							}else{
								name = portNames[d["data"].key]["tcp"].name;
							}
						}else{
							name = portNames[d["data"].key]["tcp"].name
						}
					}else{
						name = portNames[d["data"].key]["udp"].name
					}
				}else if(d["data"].key == "icmp"){
					name = "ICMP"
				}
				var w = "" + (100 + 10*name.length);
			
				var htmlString = "Servive Name: " + name + "<br>Bytes: " + d["data"].value;
				var piePopup = d3.select("#piePopup");
			    	piePopup.transition()		
					.duration(200)		
					.style("opacity", .9)
					.style("height", h)
					.style("width", w);
			
				var left = (d3.event.pageX + 20) + "px";
				var fromTop = (d3.event.pageY - 20) + "px";
			    	piePopup.html(htmlString);
				var div = document.getElementById('piePopup')
				div.style.position = "absolute";
				div.style.display = "block";
				div.style.left = left;
				div.style.top = fromTop;
			})					
			.on("mouseout", function(d) {
				document.getElementById('piePopup').style.display = "none";	
			});


		if(this.text != undefined){
			this.text.remove()
		}
		this.text = this.svg
		  	.selectAll('.label')
		  	.data(this.data_ready)
		  	.enter()
		  	.append('text')
		  	.text(function(d){ return d.data.key})
		  	.attr("transform", function(d) { return "translate(" + local_arcGenerator.centroid(d) + ")";  })
			.attr("class", "label")
		  	.style("text-anchor", "middle")
		  	.style("font-size", 19)


	}
	async move(){ //Creates a new simulation that is the same as this one in the new location and then deletes the current one
		//visualizations.push(new pieChart(location,this.titleText,this.makeData));
		const result = await openNewTab(""+otherWindows.length-1);
		otherWindows[otherWindows.length-1].makePieChart("#mainVis",this.titleText,this.makeData);
		d3.select(this.svgText).select('svg').remove();
		otherWindows[otherWindows.length-1].portNames = portNames;
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}

//This class creates a basic linegraph
class lineGraph extends Vis {
	//Svg tells where to display the chart, title gives the title, unit is what is displayed on the y-axis, and makeData is a function that returns the data to build the chart
	//The data that makeData returns should be a array of objects with the keys x and y, with x as a time, and y as a number
	constructor(svg, title, unit, makeData){
		super(svg,title);
		this.unit = unit;
		this.maxY = 0;
		this.makeData = makeData;
		this.graphX = d3.scaleTime()
    			.domain([0, 10]) // input
    			.range([0, this.width]);
		this.graphY = d3.scaleLinear()
    			.domain([0, 100]) // input 
    			.range([this.height, 0]);
		this.line = d3.line()
    			.x(function(d) { return this.graphX(d.x); }) // set the x values for the line generator
    			.y(function(d) { return this.graphY(d.y); }) // set the y values for the line generator 
    			.curve(d3.curveMonotoneX);
		this.xAxis = this.svg.append("g")
    			.attr("class", "myXaxis")
    			.attr("transform", "translate(0," + this.height + ")")
    			.call(d3.axisBottom(this.graphX));
		this.yAxis = this.svg.append("g")
    			.attr("class", "myYaxis")
    			.call(d3.axisLeft(this.graphY));
		this.xLabel = this.svg.append("text")
		      	.attr("transform",
			    "translate(" + (this.width/2) + " ," + 
				           (this.height+this.margin.top-5) + ")")
		      	.style("text-anchor", "middle")
		      	.text("Time");
		this.yLabel = this.svg.append("text")
		      .attr("transform", "rotate(-90)")
		      .attr("y", 0 - this.margin.left)
		      .attr("x",0 - (this.height / 2))
		      .attr("dy", "1em")
		      .style("text-anchor", "middle")
		      .text(this.unit);
		
		this.path = this.svg.append("path") 
    			.attr("class", "line");
	}
	update(flows,start,end){
		this.data = this.makeData(flows,start,end);
		this.data = [this.data];
		var max = d3.max(this.data[0], function(d) { return d.y; });
		if(max > this.maxY){
			this.maxY = max;
		}
		this.graphX
			.domain([start,end])
	    		.range([0, this.width]);
		
		this.graphY 
	    		.domain([0, this.maxY]).nice() 
	    		.range([this.height, 0]);

		var local_graphX = this.graphX; //Creates local copies because the definition of "this" will be different
		var local_graphY = this.graphY;
		
		this.line = d3.line()
	    		.x(function(d) { return local_graphX(d.x); }) // set the x values for the line generator
	    		.y(function(d) { return local_graphY(d.y); }) // set the y values for the line generator 
	    		.curve(d3.curveMonotoneX);
		
		this.xAxis.call(d3.axisBottom(this.graphX));
		this.yAxis.call(d3.axisLeft(this.graphY));
		this.path = this.svg.selectAll(".line")
		    	.data(this.data) 
		    	.attr("class", "line") // Assign a class for styling 
		   	.attr("d", this.line)	
			.attr("clip-path", "url(#clip)");
	}
	async move(){//Creates a new simulation that is the same as this one in the new location and then deletes the current one
		//visualizations.push(new lineGraph(location,this.titleText,this.unit,this.makeData));
		const result = await openNewTab(""+otherWindows.length-1);
		otherWindows[otherWindows.length-1].makeLineGraph("#mainVis",this.titleText,this.unit,this.makeData,this.keys);
		d3.select(this.svgText).select('svg').remove();
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}

//Creates a stacked area graph
class stackedAreaGraph extends lineGraph {
	//Svg tells where to display the chart, title gives the title, unit is what is displayed on the y-axis, makeData is a function that returns the data to build the chart, and keys is an array of the legend
	//The data that makeData returns should be an array of arrays in the same format as the lineGraph. The "stacking" is done in makeData by added the lower areas' data to the upper areas. The top area would be the sum of all legends' data, where the second from the top would be the sum of all the legends', excluding the top one, data.

	constructor(svg, title, unit, makeData, keys){
		super(svg,title,unit,makeData);
		this.lines = [];
		this.paths = [];
		this.legends = [];
		this.keys = keys;
		this.graphY = d3.scaleSymlog()
    			.domain([0, 100]) 
    			.range([this.height, 0]);
		var local_graphX = this.graphX; //Creates local copies because the definition of "this" will be different
		var local_graphY = this.graphY;
		var lineColor = d3.scaleOrdinal(d3.schemeCategory10);
		for(var i = keys.length-1; i >= 0; i--){
			this.lines.push(d3.area()
    				.x(function(d) { return local_graphX(d.x); }) 
				.y0(function(d) { return local_graphY(0); })
    				.y1(function(d) { return local_graphY(d.y); }));
			
			this.paths.push(this.svg.append("path") 
    				.attr("class", this.keys[i])
				.attr("fill", lineColor(i))
				.attr("stroke", lineColor(i))
    				.attr("stroke-width", 1));
			this.svg.append("g").attr("class","legend")
				.attr("id",this.keys[i]);
			this.legends.push(d3.select(document.getElementById(this.keys[i])));
			
			this.legends[keys.length-1-i].append("circle")
				.attr("cx", this.width -60)             
				.attr("cy", 0 - (this.margin.top / 2) + (this.margin.top / 2)*(this.keys.length-1-i))
				.attr("stroke", lineColor(i))
				.attr("fill", lineColor(i))
				.attr("r",6)
			this.legends[keys.length-1-i].append("text")
				.attr("x", this.width -60 + 10)             
				.attr("y", 0 - (this.margin.top / 2) + (this.margin.top / 2)*(this.keys.length-1-i))
				.attr("text-anchor", "left")
			     	.attr("alignment-baseline", "middle")
				.text(this.keys[i]);

		}
		

	}
	update(flows,start,end){
		this.data = this.makeData(flows,start,end);
		var max = d3.max(this.data[this.data.length-1], function(d) { return d.y; });
		if(max > this.maxY){
			this.maxY = max;
		}
		this.graphX
			.domain([start,end])
	    		.range([0, this.width]);
		
		this.graphY 
	    		.domain([0, this.maxY]).nice() 
	    		.range([this.height, 0]);
		this.xAxis.call(d3.axisBottom(this.graphX));
		this.yAxis.call(d3.axisLeft(this.graphY));
		
		var local_graphX = this.graphX; //Creates local copies because the definition of "this" will be different
		var local_graphY = this.graphY;

		for(var i = this.lines.length-1; i >= 0; i--){
			this.lines[i] = d3.area()
    				.x(function(d) { return local_graphX(d.x); }) 
				.y0(function(d) { return local_graphY(0); })
    				.y1(function(d) { return local_graphY(d.y); });
		}

		for(var i = this.paths.length-1; i >= 0; i--){
			this.paths[i] = this.svg.selectAll("." + this.keys[i])
			    	.data([this.data[i]]) 
			    	.attr("class", this.keys[i]) // Assign a class for styling 
			   	.attr("d", this.lines[i])	
				.attr("clip-path", "url(#clip)");
		}
	}
	async move(){//Creates a new simulation that is the same as this one in the new location and then deletes the current one
		//visualizations.push(new stackedAreaGraph(location,this.titleText,this.unit,this.makeData,this.keys));
		const result = await openNewTab(""+otherWindows.length-1);
		otherWindows[otherWindows.length-1].makeStackedAreaGraph("#mainVis",this.titleText,this.unit,this.makeData,this.keys);
		d3.select(this.svgText).select('svg').remove();
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}


var visualizations = [];
visualizations.push(new lineGraph("#slot0","Number of Flows Over Time","Flows",makeNumFlowsData));
visualizations.push(new stackedAreaGraph("#slot1","Bytes Over Time","kB",makeNumBytesData,["ShortFlows","MediumFlows","LongFlows"]));
visualizations.push(new lineGraph("#slot2","Fair Use Over Time","Mb",makeFairUseData));
visualizations.push(new pieChart("#slot3","Destination Port Distribution",makePortPieData));
var otherWindows = [];

function openNewTab(name){
	otherWindows.push(window.open("graph.html",name,"height=500,width=975",false));
	return new Promise(resolve => {
		setTimeout(() => {
			otherWindows.portNames = portNames;
		  	resolve('resolved');
		}, 1000);
	});
}
//Functions to create data for graphs\\

function makePortPieData(flows){
	var ports = {}
	
	//keyports deturmines what shows up as not other
	var totalBytes = 0;
	ports['other'] = 0;
	var allPorts = {}
	if(flows.flows != undefined){
		for(var i = 0; i < flows.flows.length; i++){
			if(flows.flows[i].flow["dst_port"] == undefined){
				if(flows.flows[i].flow["icmp_type"] != undefined){
					if(ports['icmp'] == undefined){
						ports['icmp'] = flows.flows[i].bytes;
					}else{
						ports['icmp'] += flows.flows[i].bytes;
					}
					totalBytes += flows.flows[i].bytes;
				}
			}else{
				if(portNames[flows.flows[i].flow["dst_port"]] != undefined){
					if(ports[flows.flows[i].flow["dst_port"]] == undefined){
						ports[flows.flows[i].flow["dst_port"]] = flows.flows[i].bytes;
					}else{
						ports[flows.flows[i].flow["dst_port"]] += flows.flows[i].bytes;
					}
				}else{
					ports['other'] += flows.flows[i].bytes;
				}
				totalBytes += flows.flows[i].bytes;
			}
			allPorts[flows.flows[i].flow["dst_port"]] = "10"
		}
			
	}
	return ports;
}
function makeNumFlowsData(flows,start,end){
	for(var i = 0; i < flows.flows.length; i++){
		flows.flows[i].startTime = new Date(flows.flows[i].startTime);
		flows.flows[i].endTime = new Date(flows.flows[i].endTime);
	}
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds() - steprate);
	end.setMilliseconds(end.getMilliseconds() + steprate);
	var time = new Date(start);
	var data = [];
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() <= end.getTime()){
		temp = {}
		temp.y = numFlowsOverRange(time,steprate,flows);
		temp.x = new Date(time);
		data.push(temp);
		time.setMilliseconds(time.getMilliseconds() + steprate);

	}
	return data;
}

function makeFairUseData(flows,start,end){
	for(var i = 0; i < flows.flows.length; i++){
		flows.flows[i].startTime = new Date(flows.flows[i].startTime);
		flows.flows[i].endTime = new Date(flows.flows[i].endTime);
	}
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds() - steprate);
	end.setMilliseconds(end.getMilliseconds() + steprate);
	var time = new Date(start);
	var data =[];
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() < end.getTime()){
		temp = {}
		temp.y = fairUseOverRange(time,steprate,flows);
		temp.x = new Date(time);
		data.push(temp);
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	return data;
}

function makeNumBytesData(flows,start,end){
	for(var i = 0; i < flows.flows.length; i++){
		flows.flows[i].startTime = new Date(flows.flows[i].startTime);
		flows.flows[i].endTime = new Date(flows.flows[i].endTime);
	}
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds() - steprate);
	end.setMilliseconds(end.getMilliseconds() + steprate);
	var time = new Date(start);
	var data = [[],[],[]]

	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() < end.getTime()){
		array = numBytesOverRange(time,steprate,flows);
		for(var i = 0; i < array.length; i++){
			temp = {}
			temp.y = array[i]
			temp.x = new Date(time);
			data[i].push(temp);
		}
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	return data;
}

//This is really just a rough approximation and should be replace with a better formula 
function fairUseOverRange(time, steprate, data){
	var num = 0;
	var bandwidth = data.bandwidth.substring(0,data.bandwidth.length-2);
	if(data.bandwidth.substring(data.bandwidth.length-2,data.bandwidth.length) == "kb"){
		bandwidth = bandwidth/1000;
	}
	var rangeEnd = new Date(time);
	rangeEnd.setMilliseconds(rangeEnd.getMilliseconds() + steprate);
	for(var i = 0; i < data.flows.length; i++){
		if(data.flows[i].startTime.getTime() <= rangeEnd && data.flows[i].endTime.getTime() >= time.getTime()){
			num++;
		} 
	}
	
	if(num == 0){
		return bandwidth;
	}
	return bandwidth/num;
}

function numBytesOverRange(time, steprate, data){
	var short = 100;
	var med = 1000;

	var shortNum = 0;
	var medNum = 0;
	var longNum = 0;
	
	var rangeEnd = new Date(time);
	rangeEnd.setMilliseconds(rangeEnd.getMilliseconds() + steprate);
	for(var i = 0; i < data.flows.length; i++){
		if(data.flows[i].startTime.getTime() <= rangeEnd.getTime() && data.flows[i].endTime.getTime() >= time.getTime()){
			if(data.flows[i].bytes <= short){
				shortNum += data.flows[i].bytes/1000;
				medNum += data.flows[i].bytes/1000;
				longNum += data.flows[i].bytes/1000;
			}else if(data.flows[i].bytes > short && data.flows[i].bytes <= med){
				medNum += data.flows[i].bytes/1000;
				longNum += data.flows[i].bytes/1000;
			}else{
				longNum += data.flows[i].bytes/1000;
			}
		} 
	}
	temp = []
	temp.push(shortNum);
	temp.push(medNum);
	temp.push(longNum);
	return temp;
}

//gets the number of flows that itersect the time start and the next point
function numFlowsOverRange(time, steprate, data){
	var num = 0;
	var bandwidth = data.bandwidth.substring(0,data.bandwidth.length-2);
	var rangeEnd = new Date(time);
	rangeEnd.setMilliseconds(rangeEnd.getMilliseconds() + steprate);
	for(var i = 0; i < data.flows.length; i++){
		if(data.flows[i].startTime.getTime() <= rangeEnd.getTime() && data.flows[i].endTime.getTime() >= time.getTime()){
			num++;
		} 
	}
	return num;
}


//Draggable Popup Code\\

document.getElementById("popup").style.display = "none";
document.getElementById("popup2").style.display = "none";
//reveals popup



function sleep (time) {
  return new Promise((resolve) => setTimeout(resolve, time));
}

//hides popup when X is clicked
var closer = document.getElementById("closer");
closer.onclick = function(){
	var openSlots = ["#slot0","#slot1","#slot2","#slot3"];
	var iPos;
	for(var i = 0; i < visualizations.length; i++){
		if(visualizations[i].svgText == "#popup"){
			iPos = i;
		}
		for(var j = 0; j < openSlots.length; j++){
			if(visualizations[i].svgText == openSlots[j]){
				openSlots.splice(j,1);
			}
		}
		
	}

	visualizations[iPos].move(openSlots[0]);
	visualizations.splice(iPos,1);
	sleep(500).then(() => {
    		document.getElementById("popup").style.display = "none";
	});
	
	
};

dragElement(document.getElementById("popup"));
dragElement(document.getElementById("popup2"));

function dragElement(elmnt) {

  var pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
  if (document.getElementById(elmnt.id + "header")) {
    // if present, the header is where you move the DIV from:
    document.getElementById(elmnt.id + "header").onmousedown = dragMouseDown;
  } else {
    // otherwise, move the DIV from anywhere inside the DIV: 
    elmnt.onmousedown = dragMouseDown;
  }
	
  function dragMouseDown(e) {
    e = e || window.event;
    e.preventDefault();
    // get the mouse cursor position at startup:
    pos3 = e.clientX;
    pos4 = e.clientY;
    document.onmouseup = closeDragElement;
    // call a function whenever the cursor moves:
    document.onmousemove = elementDrag;
  }

  function elementDrag(e) {
    e = e || window.event;
    e.preventDefault();
    // calculate the new cursor position:
    pos1 = pos3 - e.clientX;
    pos2 = pos4 - e.clientY;
    pos3 = e.clientX;
    pos4 = e.clientY;
    // set the element's new position:
    elmnt.style.top = (elmnt.offsetTop - pos2) + "px";
    elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
  }

  function closeDragElement() {
    // stop moving when mouse button is released:
    document.onmouseup = null;
    document.onmousemove = null;
  }
}

// convert milliseconds to minutes and then format to display
function msToMinutesDisplay(ms) {
    var minutes = ms / 1000.0 / 60.0;
    return minutes.toFixed(2);
}

function displayTimeRange(start, end) {
    var startDiffMs = start.getTime() - trueStart.getTime();
    var endDiffMs = end.getTime() - trueStart.getTime();

    $("#time").val(msToMinutesDisplay(startDiffMs) + " - " + msToMinutesDisplay(endDiffMs));
}

function displayCurrentTime(time) {
    var msFromStart = time.getTime() - trueStart.getTime();
    document.getElementById("current_time").innerHTML = msToMinutesDisplay(msFromStart);
}

function displaySelectedLink(link) {
    if(link != undefined) {
	var element = document.getElementById("selected_link");
	element.innerHTML = link.source.name + " - " + link.target.name + " " + link.bandwidth;
    }
}

function bandwidthAsBytesPerSecond(rawBandwidth) {
    var bandwidthExp = /^(\d+\.?\d*)(\S+)$/;
    var matchResult = rawBandwidth.match(bandwidthExp);
    if(undefined == matchResult) {
	console.log("Bandwidth is not parsable '" + rawBandwidth + "'");
	return 0;
    }
    
    var bandwidthValue = matchResult[1];
    var bandwidthUnits = matchResult[2].toLowerCase();
    
    if("mb" == bandwidthUnits) {
	var result =  bandwidthValue * 1024 * 1024 / 8;
	//console.log(rawBandwidth + " -> " + result);
	return result;
    } else if("kb" == bandwidthUnits) {
	var result = bandwidthValue * 1024 / 8;
	return result;
    } else {
	console.log("Unknown bandwidth units " + bandwidthUnits);
	return 0;
    }
}
