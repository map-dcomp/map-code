var clientColor = "red";
var lanColor = "grey";
var chordBlue = "#1f77b4";
//Gets timezone information
var d = new Date();
var timezoneOffset = d.getTimezoneOffset();
var color = d3.scaleOrdinal(['#4363d8', '#f58231', '#3cb44b','#e6194B' , '#ffe119', '#911eb4', '#42d4f4', '#f032e6', '#bfef45', '#fabed4', '#469990', '#dcbeff', '#9A6324', '#fffac8', '#800000', '#aaffc3', '#808000', '#ffd8b1', '#000075', '#5f5f5f']);




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

//changes color of node //todo find max node posible usage
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
		var networks = Object.keys(node["_groups"][0][i]["__data__"].networks);
		for(var j = 0; j < networks.length; j++){
			if(node["_groups"][0][i]["__data__"].networks[networks[j]] == ip){
				return i;
			}
		}
	}
	console.log("no node by that ip ",ip);
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

function compareEndTimes(a, b) {
	if(a.endTime < b.endTime) {
	  return -1;
	}
	if(a.endTime > b.endTime) {
	  return 1;
	}
	return 0;
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
var nodeFailures = [];
var keyTimes = [];
var regionNames;
$.getJSON("webdata/netconfig.json",function(data){
	netconfig = data;
	links = data.links;
	nodes = data.nodes;
    regionInfo = data.regionInfo;
	clientRegions = data.clientRegions;
	nodeFailures = data.nodeFailures;
	console.log(data)
	console.log(links);
	console.log(nodes);
	console.log(regionInfo);
	temp = Object.keys(regionInfo);
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
	regionNames.sort()
	console.log(regionNames);
	var hasPos = true; //If netconfig is directly from topologyReader.py it won't have xy information and we have to treat it slightly differently
	if(data.nodes[0].x == undefined){
		hasPos = false;
	}
	
	//Region Delay graph added after we have all the region names
	visualizations.push(new multiLineGraph("#slot4","Average Delay of Region External Traffic","delay",makeAverageDelayExternal, regionNames,"linear"));
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
	savedata.nodeFailures = nodeFailures;
	var text = JSON.stringify(savedata);

	
	element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
	element.setAttribute('download', "netconfig.json");

	element.style.display = 'none';
	document.body.appendChild(element);

	element.click();

	document.body.removeChild(element);
},false);

//Adds more detail to the information
function processFlowData(data){
	console.log(data);
	trueStart = new Date(data[0]["t_first"]);
	currStart = trueStart;
	trueEnd = new Date(data[data.length-1]["t_last"]);
	currEnd = trueEnd;

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
		
		//gives dates to all the data and finds the trueEnd
		data[i]["startTime"] = new Date(data[i]["t_first"]);
		data[i]["endTime"] = new Date(data[i]["t_last"]);
		if(data[i].endTime.getTime() > trueEnd.getTime()){
			trueEnd = new Date(data[i].endTime);
		}
		var length = data[i].endTime.getTime()-data[i].startTime.getTime();
		length /= 1000;
		length++;
		
		data[i].bytes = data[i].in_bytes;
		data[i].bytesPreSec = data[i].in_bytes/length;
		data[i]["index"] = i;
	}

	//creates a dataset sorted by the endtime instead of starttime
	endFlowData = [];
	for(var i = 0; i < data.length; i++){
		endFlowData.push(data[i]);
	}
	endFlowData.sort(function (a, b){
		if(a.endTime < b.endTime) {
			return -1;
		}
		if(a.endTime > b.endTime) {
			return 1;
		}
		return 0;
	});
	for(var i = 0; i < endFlowData.length; i++){
		data[endFlowData[i].index].reverseIndex = i;
		endFlowData[i].reverseIndex = i;
	}
	

	for(var i = 0; i < link["_groups"][0].length; i++){
		link["_groups"][0][i]["__data__"].allFlows = [];
		
		link["_groups"][0][i]["__data__"]["totalBytes"] = 0;
		d3.select(link["_groups"][0][i]).style("stroke", "lightgrey");
	}
	
	for(var i = 0; i < data.length; i++){
		var path = [];
		//finds the path that a flow will take
		path = findPath(data[i].src4_addr, data[i].dst4_addr);
		if(path != -1){
			var delay = 0;
			//uses path to assign flow data to links
			//caculates the delay of the flow based on the path it travels
			for(var k = 0; k < path.length-1; k++){
				//Assigns the delay of the flows based on the delay of the links they flow through
				var pos = findLink(path[k],path[k+1]);
				if(parseFloat(link["_groups"][0][pos]["__data__"].delay) == 0){
					//delay+=100;
					//console.log("No Delay");
				}else{
					delay+=parseFloat(link["_groups"][0][pos]["__data__"].delay);
				}
				
				link["_groups"][0][pos]["__data__"].allFlows.push(data[i]);
				link["_groups"][0][pos]["__data__"].flows.push(data[i]);
				link["_groups"][0][pos]["__data__"]["totalBytes"] += data[i].bytes;
				data[i].delay = delay;
			}
		}
	}
	
	//Adds the node failures to the list of key times that can be overlayed as verical lines on the graphs
	for(var i = 0; i < nodeFailures.length; i++){
		keyTimes.push(new Date((trueStart.getTime())+nodeFailures[i].time));
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
	
	//Create time ranges for the delay CDF to be split upon //Change this for custom time ranges
	times = []
	if(nodeFailures.length> 0){
		times.push({"start": 0,"end": nodeFailures[0].time, "name": "BeforeNodeFailure"});
		times.push({"start": nodeFailures[0].time,"end": 99999999, "name": "AfterNodeFailure"});
	}else{
		halfway = (trueEnd.getTime()-trueStart.getTime())/2;
		times.push({"start": 0,"end": halfway, "name": "FirstHalf"});
		times.push({"start": halfway,"end": halfway*2, "name": "SecondHalf"});
	}
	
	//Adds the CDF after the time ranges are specified
	visualizations.push(new CDF("#slot28","Cumulative Delay Distribution","delay",makeDelayCDFData, times));
}


//Loads the flows in from multiple jsons
var data1 = []
var pieceIndex = 0
function loadPiece(jsondata){
	console.log("loadPiece")
	data1 = data1.concat(jsondata);
	pieceIndex+=1
	$.ajax({url: "webdata/flowData"+pieceIndex+".json",async:true, dataType: 'json',method: "Get",success:function(jsondata){
		loadPiece(jsondata);
	},
	error: function (xhr, ajaxOptions, thrownError) {
		console.log("Finished");
		processFlowData(data1)
	}});
}

//Loads the flows from flowData.json and sets some global variables. It also creates the time slider and attempts to load nodeData.json
document.getElementById("loadFlows").addEventListener("click",function(event){
	console.log(performance.memory.usedJSHeapSize);

	//If the flow data fails to load because it is too big, it will try to load the flows in chunks by calling loadPiece
	$.ajax({url: "webdata/flowData.json",dataType: 'json',method: "Get",success:function(jsondata){
		processFlowData(jsondata);
	},error: function (xhr, ajaxOptions, thrownError) {
		console.log(xhr);
		console.log(ajaxOptions);
		console.log(thrownError);
		//alert(thrownError);
		data1 = []
		$.ajax({url: "webdata/flowData0.json",async:true, dataType: 'json',method: "Get",success:function(jsondata){
			loadPiece(jsondata);
		}});
		
		
		
	  }
	});


	//Trys to load nodeData.json which is unnecessary for the visualization
	$.getJSON("webdata/nodeData.json",function(data){
		console.log(data);
		var regions = Object.keys(data).sort()
		console.log(regions)
		for(var i = 0; i < regions.length; i++){
			var apps = Object.keys(data[regions[i]])
			for(var j = 0; j < apps.length; j++){
				for(var k = 0; k <data[regions[i]][apps[j]].length; k++){
					data[regions[i]][apps[j]][k].x = new Date(data[regions[i]][apps[j]][k].x);
					data[regions[i]][apps[j]][k].x.setMinutes(data[regions[i]][apps[j]][k].x.getMinutes() + 0);//timezoneOffset); //Applies timezoneOffset to the timing of data
				}
			}
		}
		nodeData = data;
		console.log(regions)
		var slot = 6; //Number of graphs before node graphs

		//Creates a number of node usage graphs from the nodeData/json
		for(var i = 0; i < regions.length; i++){
			visualizations.push(new subRegionGraph("#slot"+(slot+i),"Region " + regions[i],"Usage",singleRegionLoadData,Object.keys(nodeData[regions[i]]),nodeData[regions[i]],"linear"));
		}
		hasNodeData = true;
	});
	
},false);

//Toggles wether or not the key times should show up on the graphs
var showKeyTimes = false;
document.getElementById("keyTimes").addEventListener("click",function(event){
	showKeyTimes = !showKeyTimes;
	updateGraphs(selectedLink,currStart,currEnd);
});


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
		for(var i = 0; i < visualizations.length; i++){
			visualizations[i].maxY = 0;
		}
		for(var i = 0; i < otherWindows.length; i++){
			otherWindows[i].thisVis.maxY = 0;
			
		}
		if(activeData == undefined){
			activeData=flowData;
		}
	    updateGraphs(selectedLink,currStart,currEnd);
		displaySelectedLink(selectedLink);
		
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
	
	node.on("mouseover", nodeDetailsOver)
            .on("mouseout", nodeDetailOut);
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

	//Addes red line to node that have a failure
	for(var i = 0; i < nodeFailures.length; i++){
		d3.select(node["_groups"][0][findNodebyName(nodeFailures[i].node)]).append("line")
		.attr("class", "Xmarker")
		.attr("x1", -10)
		.attr("y1", -10)
		.attr("x2", 10)
		.attr("y2", 10)
		.attr("stroke-width", 5)
		.attr("stroke", "red");
	}
}

//Function for displaying a node's info when hovering over it
function nodeDetailsOver(d){
	
	var htmlString = "" + d.name + " IPs: ";
	
	var networks = Object.keys(d.networks);
	for(var i = 0; i < networks.length; i++){
		htmlString += d.networks[networks[i]] + "<br>";
	}
	for(var i = 0; i < nodeFailures.length; i++){
		if(nodeFailures[i].node == d.name){
			htmlString += "Fails " + nodeFailures[i].time/60000 + " mins in";
		}
	}
	var nodePopup = d3.select("#nodePopup");
	nodePopup.transition()		
		.duration(200)		
		.style("opacity", .9)
		.style("height", 100)
		.style("width", 150);

	nodePopup.html(htmlString);
	var div = document.getElementById('nodePopup')
	div.style.position = "absolute";
	div.style.display = "block";
	var left = (d3.event.pageX + 20) + "px";
	var fromTop = (d3.event.pageY - 20) + "px";
	div.style.left = left;
	div.style.top = fromTop;
}

//Hides the div with the node's info when not hovered over it
function nodeDetailOut(d){
	d3.select("#nodePopup")
		.transition()		
		.duration(200)		
		.style("opacity", .9)
		.style("height", 100)
		.style("width", 100);
	var div = document.getElementById('nodePopup')
	div.style.display = "none";
	div.style.left = 0;
	div.style.top = 0;
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
var chordBytes = [];
var totalChordBytes = [];
var chordHeight = height;
var chordDiagram;
var outerRadius = Math.min(width, chordHeight) * 0.30;
var innerRadius = outerRadius - 10;
var clientRegions;
var cachedMatrix = {};
//Makes Diagram with data
function makeChordDiagram(){
	chord = d3.chord()
	    .padAngle(.04)
	    .sortSubgroups(d3.descending)
	    .sortChords(d3.descending)
	ribbon = d3.ribbon()
	    .radius(innerRadius)

	//color = d3.scaleOrdinal().range(d3.schemeCategory20)
	  
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
	      .text(d => chordBytes[d.index] + " " + regionNames[d.index]);

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
var currStart;
var currEnd;
var selectedSource;
var selectedTarget;
var selectedLink;
var nodeData;
var regionNodeData;
var regionNodeDataKeys;
var hasNodeData = false;
var byteColorModifier = 400; //The total bytes on a link divided by this should roughly represent a precentage of how used the link is
var activeData;
//Is called whenever the time range changes
function narrowView(start,end,data) {
	currStart = start;
	currEnd = end;
	//figure out what data show be showing up
	activeData = [];
	endActiveData = [];
    for(var i = 0; i < data.length; i++){
		if(data[i].startTime.getTime() <= end.getTime() && data[i].endTime.getTime() >= start.getTime()){
			activeData.push(data[i]);
			data[i].active = true;
		}else{
			data[i].active = false;
		}
		
    }

    if(netgraph){
		newNetgraph(data,start,end);
    }else{
		chordReplay(activeData);
    }
    
	//gives all the graphs the new active dataset
	updateGraphs(selectedLink,new Date(start),new Date(end));
	
	
}


function newNetgraph(data,start,end){
	var window;// = new Date();

	// size of the window in seconds
	window = (end.getTime() - start.getTime())/1000;
	
	for(var i = 0; i < link["_groups"][0].length; i++){
		link["_groups"][0][i]["__data__"].flows = [];
		link["_groups"][0][i]["__data__"].totalBytes = 0;
		for(var j = 0; j < link["_groups"][0][i]["__data__"].allFlows.length; j++){
			if(link["_groups"][0][i]["__data__"].allFlows[j].active){
				link["_groups"][0][i]["__data__"].flows.push(link["_groups"][0][i]["__data__"].allFlows[j]);
				link["_groups"][0][i]["__data__"].totalBytes += link["_groups"][0][i]["__data__"].allFlows[j].bytes;
			}
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
	
}


var simTime;
var play = false;
var replayWindow;
var sps = 1;
var replayStep;
var replayStartOffset;
document.getElementById("replay").addEventListener("click",function(event){
	play = true;
	replayWindow = prompt("Window Size (secs):"); //Gets the window/tail length
	if(replayWindow == ""){
		replayWindow = 60;
	}
	
	replayStep = prompt("Window moves by ___ seconds:"); //How much the window moves per update
	if(replayStep == ""){
		replayStep = 1000;
	}else{
		replayStep *= 1000;
	}

	replayStartOffset = prompt("Start at ___ seconds from beginning :"); //Where to start the replay
	if(replayStartOffset == ""){
		replayStartOffset = 0;
	}else{
		replayStartOffset *= 1000;
	}

	simTime = new Date(trueStart);
	simTime.setMilliseconds(simTime.getMilliseconds() + replayStartOffset);
	replay();
},false);

//Allows a replay to be paused and then restarted
var pause = d3.select("#pause");
pause.text("Pause");
pause.on("click", function(){
	console.log("-----Times-----");
	console.log(trueStart.getTime());
	console.log(nodeData[0].time.getTime());
	console.log(trueStart.getTime()-nodeData[0].time.getTime());
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
				//var step = (sps*1000)/(1000/playbackSpeed) //Calculates time step based on the seconds per second and how often this function runs
				simTime.setMilliseconds(simTime.getMilliseconds() + replayStep);				
				
			    var timeWindowStart = new Date(simTime.getTime() - 1000*replayWindow);
			    var timeWindowEnd = new Date(simTime);
			    narrowView(timeWindowStart, timeWindowEnd, flowData, true); //Trigger to update visualizations
			    
			    syncTimeSliders(timeWindowStart, timeWindowEnd);
				if(netgraph && hasNodeData){
					nodeReplay(simTime); //currently no data
				}
				time();
			}
		//}, playbackSpeed);
		}, 50);
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

//This function syncs the time sliders of the other windows
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
	if(cachedMatrix[currEnd.getTime()] == null){
		matrix = [];
		chordBytes = [];
		totalChordBytes = [];
		chordBytes.length = regionNames.length; 
		chordBytes.fill(0);
		totalChordBytes.length = regionNames.length; 
		totalChordBytes.fill(0);
		for(var i = 0; i < regionNames.length; i++){
			(arr = []).length = regionNames.length; arr.fill(0);
			matrix.push(arr);
		}
		
		for(var i = 0; i < activeData.length; i++){
			srcIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["src4_addr"])]["__data__"].name]);
			destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["dst4_addr"])]["__data__"].name]);
			if(srcIndex != destIndex){
				chordBytes[srcIndex] += activeData[i].in_bytes;
				chordBytes[destIndex] += activeData[i].in_bytes;
				matrix[srcIndex][destIndex] += activeData[i].in_bytes;
				matrix[destIndex][srcIndex] += activeData[i].in_bytes;
			}
			totalChordBytes[srcIndex] += activeData[i].in_bytes;
			totalChordBytes[destIndex] += activeData[i].in_bytes;
		}

		cachedMatrix[currEnd.getTime()] = {"matrix": matrix, "chordBytes":chordBytes,"totalChordBytes": totalChordBytes };
	}else{
		matrix = cachedMatrix[currEnd.getTime()]["matrix"];
		chordBytes = cachedMatrix[currEnd.getTime()]["chordBytes"];
		totalChordBytes = cachedMatrix[currEnd.getTime()]["totalChordBytes"];
	}
	
	
	//Removes old chord diagram and completely recreates it
	d3.select("svg").remove();
	chord = d3.chord()
	    .padAngle(.04)
	    .sortSubgroups(d3.descending)
	    .sortChords(d3.descending)
	ribbon = d3.ribbon()
	    .radius(innerRadius)

	//color = d3.scaleOrdinal().range(d3.schemeCategory20)
	  
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
	.attr("dy", "0em")
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
	.text(d => regionNames[d.index] + " " + (chordBytes[d.index]/1000000).toFixed(2) + "MB");

	group.append("text")
	.each(d => { d.angle = (d.startAngle + d.endAngle) / 2; })
	.attr("dy", "1em")
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
	.text(d => ((chordBytes[d.index]/totalChordBytes[d.index])*100).toFixed(2)+"% External");

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
var nodeColorModifier = 100/3

function nodeReplay(time){
	//Sets all nodes to default green
	for(var i = 0; i < node["_groups"][0].length; i++){
		d3.select(node["_groups"][0][i].getElementsByTagName("circle")[0]).style("fill", "green");
	}
	var i = 0;
	while(i < nodeData.length && time.getTime() > nodeData[i].time.getTime()){ //Moves through nodeData until we arrive at the current timestamp
		i++;
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
var playbackSpeed = 100; //Affects speed of sim

var tempDataHolder;

//Updates all graphs based on a start and end time and data that is a link's flows
function updateGraphs(data,start,end){
	var startTime = new Date();
	//if data is not empty update graphs
	if(data != undefined){
		var currTime;
		var oldTime;
		for(var i = 0; i < visualizations.length; i++){
			if(visualizations[i].enabled){
				oldTime = new Date();
				visualizations[i].update(data,start,end);
				currTime = new Date();
				//console.log(visualizations[i].titleText+" took",currTime.getTime()-oldTime.getTime());
			}
		}
		for(var i = 0; i < otherWindows.length; i++){
			try{
				console.log("Updating External Window");
				console.log(otherWindows[i]);
				console.log(otherWindows[i].thisVis);
				otherWindows[i].thisVis.update(data,start,end);
				otherWindows[i].redraw();
			}catch(err){
				console.log("window not ready or gone",err);
			}
		}
	}else{
		console.log("no Data");
	}
	var endTime = new Date();
	console.log("Graph Update took", endTime.getTime()-startTime.getTime());
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
		if(playbackSpeed > 400){ //Anything over 400 doesn't look great and it is likely that the sim clock will be off anyways
			playbackSpeed = 400;
		}
		for(var i = 0; i < visualizations.length; i++){
			visualizations[i].maxY = 0;
		}
		updateGraphs(selectedLink,new Date(currStart),new Date(currEnd));
	}
},false);

//Classes\\
//Main parent class for all the graphs 
class Vis {
	
	constructor(svg,title){
		var vis = this;
		this.margin = {top: 50, right: 50, bottom: 50, left: 50};
		this.width = 798 - this.margin.left - this.margin.right 
		this.height = 400 - this.margin.top - this.margin.bottom;
		this.data = []
		this.titleText = title;
		this.svgText = svg;
		this.enabled = true;
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
		this.button = this.svg.append("rect")
			.attr("x", this.width+20)
			.attr("y", this.height+20)
			.attr("width", 20)
			.attr("height",20)
			.attr("fill", "grey")
			.attr("stroke-width", 2)
			.attr("stroke", "grey")
			.on("click",function(d){
				vis.toggle();
			});
			
		this.buttonGraphic = this.svg.append("rect")
			.attr("x", this.width+23)
			.attr("y", this.height+23)
			.attr("width", 14)
			.attr("height",14)
			.attr("fill", "lime")
			.attr("stroke-width", 1)
			.attr("stroke", "grey")
			.on("click",function(d){
				vis.toggle();
			});
	}
	toggle(){
		if(this.enabled){
			this.buttonGraphic.style('fill', 'red');
			this.enabled = false;
		}else{
			this.buttonGraphic.style('fill', 'lime');
			this.enabled = true;
		}
		
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
		this.radius = Math.min(this.width, this.height) / 2;
		this.button.attr("x", this.width/2 + 20)
		.attr("y", this.height/2 +20);
		this.buttonGraphic.attr("x", this.width/2 + 23)
		.attr("y", this.height/2 +23);
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
				.call(d3.axisLeft(this.graphY))
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
		this.timeMarkers = [];
		
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

		while(keyTimes.length > this.timeMarkers.length){
			this.timeMarkers.push(this.svg.append("line"))
		}

		if(showKeyTimes){	
			for(var i = 0; i < keyTimes.length; i++){
				this.timeMarkers[i]
					.attr("x1", this.graphX(keyTimes[i]))
					.attr("y1", 300)
					.attr("x2", this.graphX(keyTimes[i]))
					.attr("y2", 0)
					.attr("stroke-width", 1)
					.attr("stroke", "black");
			}
		}else{
			for(var i = 0; i < keyTimes.length; i++){
				this.timeMarkers[i].attr("stroke-width", 0);
			}
		}

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
		
		const result = await openNewTab(""+otherWindows.length-1);
		otherWindows[otherWindows.length-1].makeLineGraph("#mainVis",this.titleText,this.unit,this.makeData);
		d3.select(this.svgText).select('svg').remove();
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}

//Most similar to a stacked area graph but with changes to the scales and axis labels
class CDF extends Vis{
	constructor(svg, title, unit, makeData, times){
		super(svg,title);
		this.unit = unit;
		this.cachedData = {};
		this.maxY = 0;
		this.makeData = makeData;
		this.graphX = d3.scaleLinear()
    		.domain([0, 100]) // input
    		.range([0, this.width]);
		this.graphY = d3.scaleLinear()
    		.domain([0, 1]) // input 
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
				.call(d3.axisLeft(this.graphY))
		this.xLabel = this.svg.append("text")
		    .attr("transform",
			"translate(" + (this.width/2) + " ," + 
						(this.height+this.margin.top-5) + ")")
		    .style("text-anchor", "middle")
		    .text("Delay");
		this.yLabel = this.svg.append("text")
		    .attr("transform", "rotate(-90)")
		    .attr("y", 0 - this.margin.left)
		    .attr("x",0 - (this.height / 2))
		    .attr("dy", "1em")
		    .style("text-anchor", "middle")
		    .text("Percent");
		this.path = this.svg.append("path") 
			.attr("class", "line");
		this.timeMarkers = [];
		
	}
	update(flows, start, end){
		console.log(end);
		if(this.cachedData[end.getTime()] == null){
			console.log("no data for this end");
			this.data = this.makeData(flows,start,end);
			this.data = [this.data];
			this.cachedData[end.getTime()] = this.data;
		}else{
			this.data = this.cachedData[end.getTime()];
		}
		
		var max = d3.max(this.data[0], function(d) { return d.x; });
		
		this.graphX
			.domain([0,max])
	    	.range([0, this.width]);
		
		this.graphY 
			.domain([0, 1])
			.range([this.height, 0]);

		while(keyTimes.length > this.timeMarkers.length){
			this.timeMarkers.push(this.svg.append("line"))
		}


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
		const result = await openNewTab(""+otherWindows.length-1);
		otherWindows[otherWindows.length-1].makeCDF("#mainVis",this.titleText,this.unit,this.makeData, this.times);
		d3.select(this.svgText).select('svg').remove();
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}


//Creates a stacked area graph
class stackedAreaGraph extends lineGraph {
	//Svg tells where to display the chart, title gives the title, unit is what is displayed on the y-axis, makeData is a function that returns the data to build the chart, and keys is an array of the legend
	//The data that makeData returns should be an array of arrays in the same format as the lineGraph. The "stacking" is done in makeData by added the lower areas' data to the upper areas. The top area would be the sum of all legends' data, where the second from the top would be the sum of all the legends', excluding the top one, data.

	constructor(svg, title, unit, makeData, keys, scaleType){
		super(svg,title,unit,makeData);
		this.lines = [];
		this.paths = [];
		this.legends = [];
		this.keys = keys;
		this.activeKeys = [];
		this.scaleType = scaleType;
		if(scaleType == "log"){
			this.graphY = d3.scaleSymlog()
				.domain([0, 100]) 
				.range([this.height, 0]);
		}else{
			this.graphY = d3.scaleLinear()
				.domain([0, 100]) 
				.range([this.height, 0]);
			
		}
		var local_graphX = this.graphX; //Creates local copies because the definition of "this" will be different
		var local_graphY = this.graphY;
		var tester = this;
		var lineColor = color
		console.log(lineColor);
		var actKeys;
		for(var i = 0; i < keys.length; i++){
			var keyObject = {};
			keyObject["id"] = title+":"+this.keys[i];
			keyObject["active"] = true;
			this.activeKeys.push(keyObject);
		}
		
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
				.attr("id",title+":"+this.keys[i]);
			this.legends.push(d3.select(document.getElementById(title+":"+this.keys[i])));
			this.legends[keys.length-1-i].append("circle")
				.attr("cx", this.width -60)             
				.attr("cy", 0 - (this.margin.top / 1.5) + (this.margin.top / 3)*(this.keys.length-1-i))
				.attr("stroke", lineColor(i))
				.attr("fill", lineColor(i))
				.attr("r",6)
				.on("click",function(d){
					
					for(var j = 0; j < actKeys.length; j++){
						if(actKeys[j].id == this.parentNode.id){
							actKeys[j].active = !actKeys[j].active;
						}
					}
					updateGraphs(selectedLink,currStart,currEnd);
				});
			this.legends[keys.length-1-i].append("text")
				.attr("x", this.width -60 + 10)             
				.attr("y", 0 - (this.margin.top / 1.5) + (this.margin.top / 3)*(this.keys.length-1-i))
				.attr("text-anchor", "left")
			     	.attr("alignment-baseline", "middle")
				.text(this.keys[i]);
			
		}
		actKeys = this.activeKeys;
		this.timeMarker = [];
	}
	update(flows,start,end){
		this.data = this.makeData(flows,start,end);
		
		var startTime  = new Date();
		
		
		var max = 0;
		var j = this.data.length-1;
		while(j>=0){
			max = d3.max(this.data[j], function(d) { return d.y; });
			if(max > this.maxY){
				this.maxY = max;
			}
			j--;
		}
		
		
		this.graphX
			.domain([start,end])
	    	.range([0, this.width]);
		
		this.graphY 
			.domain([0, this.maxY]).nice() 
			.range([this.height, 0]);
				
		while(keyTimes.length > this.timeMarkers.length){
			this.timeMarkers.push(this.svg.append("line"))
		}

		if(showKeyTimes){	
			for(var i = 0; i < keyTimes.length; i++){
				this.timeMarkers[i]
					.attr("x1", this.graphX(keyTimes[i]))
					.attr("y1", 300)
					.attr("x2", this.graphX(keyTimes[i]))
					.attr("y2", 0)
					.attr("stroke-width", 1)
					.attr("stroke", "black");
			}
		}else{
			for(var i = 0; i < keyTimes.length; i++){
				this.timeMarkers[i].attr("stroke-width", 0);
			}
		}

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
		otherWindows[otherWindows.length-1].makeStackedAreaGraph("#mainVis",this.titleText,this.unit,this.makeData,this.keys,this.scaleType);
		d3.select(this.svgText).select('svg').remove();
		updateGraphs(selectedLink,new Date(trueStart),new Date(trueEnd));
	}
}

//Gets rid of the fill of a stackedAreaGraph to create a graph with multi lines
class multiLineGraph extends stackedAreaGraph {
	constructor(svg, title, unit, makeData, keys, scaleType){
		super(svg, title, unit, makeData, keys, scaleType);
		for(var i = 0; i < this.paths.length; i++){
			var pathColor=d3.rgb("blue");
			pathColor.opacity = 0.0;
			this.paths[i].attr("fill", pathColor);
			
		}
	}
}

//Version of the stackedAreaGraph that has special coloring node usage and node capacities
class subRegionGraph extends lineGraph {
	//Svg tells where to display the chart, title gives the title, unit is what is displayed on the y-axis, makeData is a function that returns the data to build the chart, and keys is an array of the legend
	//The data that makeData returns should be an array of arrays in the same format as the lineGraph. The "stacking" is done in makeData by added the lower areas' data to the upper areas. The top area would be the sum of all legends' data, where the second from the top would be the sum of all the legends', excluding the top one, data.

	constructor(svg, title, unit, makeData, keys, data, scaleType){
		super(svg,title,unit,makeData);
		this.lines = [];
		this.paths = [];
		this.legends = [];
		this.keys = keys.sort();
		this.activeKeys = [];
		this.rawData = data;
		if(scaleType == "log"){
			this.graphY = d3.scaleSymlog()
				.domain([0, 100]) 
				.range([this.height, 0]);
		}else{
			this.graphY = d3.scaleLinear()
				.domain([0, 100]) 
				.range([this.height, 0]);
			
		}
		var local_graphX = this.graphX; //Creates local copies because the definition of "this" will be different
		var local_graphY = this.graphY;
		var tester = this;
		var lineColor = color;
		var actKeys;
		for(var i = 0; i < keys.length; i++){
			var keyObject = {};
			keyObject["id"] = title+":"+this.keys[i];
			keyObject["active"] = true;
			this.activeKeys.push(keyObject);
		}
		
		var darkerInt = 0;
		for(var i = keys.length-1; i >= 0; i--){
			
			this.lines.push(d3.area()
    				.x(function(d) { return local_graphX(d.x); }) 
				.y0(function(d) { return local_graphY(0); })
					.y1(function(d) { return local_graphY(d.y); }));
			var pathColor;
			
			if(keys[i].includes("capacity")){
				pathColor=d3.rgb(lineColor(i/2-1)).brighter(0.5);
				//pathColor.opacity = 0.5;
			}else{
				pathColor=d3.rgb(lineColor(i/2)).darker(0.5);
			}
			
			
			this.paths.push(this.svg.append("path") 
    				.attr("class", this.keys[i])
				.attr("fill", pathColor)
				.attr("stroke", pathColor)
    				.attr("stroke-width", 1));
			this.svg.append("g").attr("class","legend")
				.attr("id",title+":"+this.keys[i])
			this.legends.push(d3.select(document.getElementById(title+":"+this.keys[i])));
			
			this.legends[keys.length-1-i].append("circle")
				.attr("cx", this.width -75)             
				.attr("cy", 0 - (this.margin.top / 1.5) + (this.margin.top / 3)*(this.keys.length-1-i))
				.attr("stroke", pathColor)
				.attr("fill", pathColor)
				.attr("r",6)
				.on("click",function(d){
					
					for(var i = 0; i < actKeys.length; i++){
						if(actKeys[i].id == this.parentNode.id){
							actKeys[i].active = !actKeys[i].active;
						}
					}
					tester.update(selectedLink,currStart,currEnd);
					//updateGraphs(selectedLink,currStart,currEnd);
				});
			this.legends[keys.length-1-i].append("text")
				.attr("x", this.width -75 + 10)             
				.attr("y", 0 - (this.margin.top / 1.5) + (this.margin.top / 3)*(this.keys.length-1-i))
				.attr("text-anchor", "left")
			     	.attr("alignment-baseline", "middle")
				.text(this.keys[i]);
			
		}
		actKeys = this.activeKeys;
		this.timeMarker = this.svg.append("line")
	}
	update(flows,start,end){
		this.data = this.makeData(this.rawData,start,end);
		
		var startTime  = new Date();
		
		this.maxY = 0;
		var j = this.data.length-1;
		while(j>=0){
			if(this.data[j].length!=0){
				var max = d3.max(this.data[j], function(d) { return d.y; });
				if(max > this.maxY){
					this.maxY = max
				}
			}
			j--;
		}
		
		this.graphX
			.domain([start,end])
	    	.range([0, this.width]);
		
		this.graphY 
			.domain([0, this.maxY]).nice() 
			.range([this.height, 0]);

		if(showKeyTimes){	
			this.timeMarker
				.attr("x1", this.graphX(keyTimes[0]))
				.attr("y1", 300)
				.attr("x2", this.graphX(keyTimes[0]))
				.attr("y2", 0)
				.attr("stroke-width", 1)
				.attr("stroke", "black");
		}else{
			this.timeMarker.attr("stroke-width", 0);
		}

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
//visualizations.push(new stackedAreaGraph("#slot1","Bytes Over Time","kB",makeNumBytesData,["ShortFlows","MediumFlows","LongFlows"],"log"));
visualizations.push(new stackedAreaGraph("#slot1","Bytes Per Sec Over Time","kB",makeNumBPSData,["ShortFlows","MediumFlows","LongFlows"],"linear"));
visualizations.push(new lineGraph("#slot2","Fair Use Over Time","MB",makeFairUseData));
visualizations.push(new pieChart("#slot3","Destination Port Distribution",makePortPieData));



//makeRegionNodeCompareData(nodeData,start,end)
var otherWindows = [];

//Opens a new window
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
			if(flows.flows[i]["dst_port"] == undefined){
				if(flows.flows[i]["icmp_type"] != undefined){
					if(ports['icmp'] == undefined){
						ports['icmp'] = flows.flows[i].bytes;
					}else{
						ports['icmp'] += flows.flows[i].bytes;
					}
					totalBytes += flows.flows[i].bytes;
				}
			}else{
				if(portNames[flows.flows[i]["dst_port"]] != undefined){
					if(ports[flows.flows[i]["dst_port"]] == undefined){
						ports[flows.flows[i]["dst_port"]] = flows.flows[i].bytes;
					}else{
						ports[flows.flows[i]["dst_port"]] += flows.flows[i].bytes;
					}
				}else{
					ports['other'] += flows.flows[i].bytes;
				}
				totalBytes += flows.flows[i].bytes;
			}
			allPorts[flows.flows[i]["dst_port"]] = "10"
		}
			
	}
	return ports;
}



//Makes the data for number of flows over time
function makeNumFlowsData(flows,start,end){
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds());
	end.setMilliseconds(end.getMilliseconds());
	var time = new Date(start);
	var data = [];
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() <= end.getTime()){
		temp = {}
		temp.y = 0;
		
		temp.x = new Date(time);
		data.push(temp);
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}

	var startIndex = 0;
	var index;
	for(var i = 0; i < flows.flows.length; i++){
		while(startIndex<data.length-1 && flows.flows[i].startTime > data[startIndex+1].x){
			startIndex++;
		}
		
		index = startIndex;
		while(index < data.length && flows.flows[i].endTime > data[index].x){
			data[index].y++;
			index++;
		}
	}
	data.pop();
	return data;
}

//Creates data for the average link delay over time
function makeAverageLinkDelay(flows,start,end){
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds());
	end.setMilliseconds(end.getMilliseconds());
	var time = new Date(start);
	var data = [];
	var amount = []
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() <= end.getTime()){
		temp = {}
		temp.y = 0;
		temp.x = new Date(time);
		amount.push(0);
		data.push(temp);
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	
	var startIndex = 0;
	var index;
	for(var i = 0; i < flows.flows.length; i++){
		
		destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(flows.flows[i]["dst4_addr"])]["__data__"].name]);
		
		while(startIndex<data.length-1 && flows.flows[i].startTime > data[startIndex+1].x){
			startIndex++;
		}
		
		index = startIndex;
		while(index < data.length && flows.flows[i].endTime > data[index].x){
			
			data[index].y+=flows.flows[i].delay;
			amount[index]++;
			index++;
		}
		
	}

	for(var i = 0; i< data.length; i++){
		if(amount[i]!= 0){
			data[i].y = data[i].y/amount[i];
		}else{
			console.log(data[i].y)
			data[i].y = 0;
		}
	}
	data.pop();
	return data;
}

//Same as makeAverageLinkDelay but only includes data from flows between regions
function makeAverageDelayExternal(flows,start,end){
	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds());
	end.setMilliseconds(end.getMilliseconds());
	var time = new Date(start);
	var data = [];
	var amount = []
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	for(var i = 0; i < regionNames.length; i++){
		data.push([]);
		amount.push([]);
	}
	while(time.getTime() <= end.getTime()){
		for(var i = 0; i < data.length; i++){
			temp = {}
			temp.y = 0;
			temp.x = new Date(time);
			amount[i].push(0);
			data[i].push(temp);
		}
		
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	var startIndex = 0;
	var index;
	var amountD = 0
	for(var i = 0; i < activeData.length; i++){
		srcIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["src4_addr"])]["__data__"].name]);
		destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["dst4_addr"])]["__data__"].name]);
		if(srcIndex != destIndex){
			while(startIndex<data[0].length-1 && activeData[i].startTime > data[0][startIndex+1].x){
				startIndex++;
			}
			
			index = startIndex;
			while(index < data[0].length && activeData[i].endTime > data[0][index].x){
				data[srcIndex][index].y+=activeData[i].delay;
				amount[srcIndex][index]++;
				data[destIndex][index].y+=activeData[i].delay;
				amount[destIndex][index]++;
				index++;
			}
		}
	}
	
	for(var i = 0; i< data.length; i++){
		for(var j = 0; j < data[i].length; j++){
			if(amount[i][j]!= 0){
				data[i][j].y = data[i][j].y/amount[i][j];
			}else{
				data[i][j].y = 0;
			}
		}
		
	}
	for(var i = 0; i< data.length; i++){
		data[i].pop();
	}
	
	return data;
}

//Creates CDF out of the delays of the flows
function makeDelayCDFData(flows,start,end){
	
	
	var data = [];
	var temp = {}
	
	
	//Splits the data into j groups based on time ranges
	delaysObj = {}
	var index = 0;
	
	for(var i = 0; i < activeData.length; i++){
		srcIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["src4_addr"])]["__data__"].name]);
		destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(activeData[i]["dst4_addr"])]["__data__"].name]);
		
		if(srcIndex!=destIndex){
			data.push(activeData[i].delay);
		}
	}
		
	
	

	
	data.sort(function(a, b){
		return a - b;
	});
	for(var i = 0; i < data.length; i++){
		if(delaysObj[data[i]] == undefined){
			delaysObj[data[i]] = 0;
		}else{
			delaysObj[data[i]]++;
		}
	}
	delays = Object.keys(delaysObj);
	delays.sort(function(a, b){
		return a - b;
	});

	//Adds the first point
	data = [{"y":0,"x":0}];

	for(var i = 0; i < delays.length; i++){
		var index = i;
		temp = {"y":0,"x":parseInt(delays[i])}
		while(index>=0){
			temp.y+=delaysObj[delays[index]];
			index--;
		}
		data.push({"y":data[data.length-1].y,"x":temp.x})//Addes another point with the same y as the previous to get the vertical stair effect of a CDF
		data.push(temp);
	}
	
	for(var i = 0; i < data.length; i++){
		data[i].y/=data[data.length-1].y;
	}
	
	data.push({"y":1,"x":data[data.length-1].x+1});
	console.log(data);
	return data;
}


function makeFairUseData(flows,start,end){
	var bandwidth = flows.bandwidth.substring(0,flows.bandwidth.length-2);
	if(flows.bandwidth.substring(flows.bandwidth.length-2,flows.bandwidth.length) == "kb"){
		bandwidth = bandwidth/1000;
	}

	var steprate = (end.getTime() - start.getTime())/granularity;
	start.setMilliseconds(start.getMilliseconds());
	end.setMilliseconds(end.getMilliseconds());
	var time = new Date(start);
	var data = [];
	var temp = {}
	if(steprate < 1){
		steprate = 1;
	}
	while(time.getTime() <= end.getTime()){
		temp = {}
		temp.y = 0;
		temp.x = new Date(time);
		data.push(temp);
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	
	var startIndex = 0;
	var index;
	for(var i = 0; i < flows.flows.length; i++){
		while(startIndex<data.length-1 && flows.flows[i].startTime > data[startIndex+1].x){
			startIndex++;
		}
		index = startIndex;
		while(index < data.length &&  flows.flows[i].endTime > data[index].x ){
			data[index].y++;
			index++;
		}
	}
	for(var i = 0; i < data.length; i++){
		if(data[i].y != 0){
			data[i].y=bandwidth/data[i].y;
		}else{
			data[i].y=bandwidth;
		}
		
	}
	data.pop();
	
	return data;
}


function makeNumBytesData(flows,start,end){
	var short = 100;
	var med = 1000;

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
		for(var i = 0; i < 3; i++){
			temp = {}
			temp.y = 0;
			temp.x = new Date(time);
			data[i].push(temp);
		}
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	var startIndex = 0;
	var index;
	var num = 0;
	for(var i = 0; i < flows.flows.length; i++){
		while(flows.flows[i].startTime > data[0][startIndex+1].x){
			startIndex++;
		}
		index = startIndex;
		while(index < data[0].length && flows.flows[i].endTime > data[0][index].x){
			num++;
			if(flows.flows[i].bytes <= short){
				if(this.activeKeys[0].active){
					data[0][index].y += flows.flows[i].bytes/1000;
				}
				if(this.activeKeys[0].active){
					data[1][index].y += flows.flows[i].bytes/1000;
				}
				if(this.activeKeys[0].active){
					data[2][index].y += flows.flows[i].bytes/1000;
				}
			}else if(flows.flows[i].bytes > short && flows.flows[i].bytes <= med){
				if(this.activeKeys[1].active){
					data[1][index].y += flows.flows[i].bytes/1000;
				}
				if(this.activeKeys[1].active){
					data[2][index].y += flows.flows[i].bytes/1000;
				}
			}else{
				if(this.activeKeys[2].active){
					data[2][index].y += flows.flows[i].bytes/1000;
				}
			}
			index++;
		}
	}
	data[2].pop();
	data[1].pop();
	data[0].pop();
	
	return data;
	
}

function makeNumBPSData(flows,start,end){ //Alerternate to makeNumBytesData that creates a better representation of what is going on at a specific time
	var short = 100;
	var med = 1000;

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
		for(var i = 0; i < 3; i++){
			temp = {}
			temp.y = 0;
			temp.x = new Date(time);
			data[i].push(temp);
		}
		time.setMilliseconds(time.getMilliseconds() + steprate);
	}
	var startIndex = 0;
	var index;
	var num = 0;
	for(var i = 0; i < flows.flows.length; i++){
		while(flows.flows[i].startTime > data[0][startIndex+1].x){
			startIndex++;
		}
		index = startIndex;
		while(index < data[0].length && flows.flows[i].endTime > data[0][index].x){
			num++;
			if(flows.flows[i].bytes <= short){
				if(this.activeKeys[0].active){
					data[0][index].y += flows.flows[i].bytesPreSec/1000;
				}
				if(this.activeKeys[0].active){
					data[1][index].y += flows.flows[i].bytesPreSec/1000;
				}
				if(this.activeKeys[0].active){
					data[2][index].y += flows.flows[i].bytesPreSec/1000;
				}
			}else if(flows.flows[i].bytes > short && flows.flows[i].bytes <= med){
				if(this.activeKeys[1].active){
					data[1][index].y += flows.flows[i].bytesPreSec/1000;
				}
				if(this.activeKeys[1].active){
					data[2][index].y += flows.flows[i].bytesPreSec/1000;
				}
			}else{
				if(this.activeKeys[2].active){
					data[2][index].y += flows.flows[i].bytesPreSec/1000;
				}
			}
			index++;
		}
	}
	
	data[2].pop();
	data[1].pop();
	data[0].pop();
	return data;
}

//Handles the node data for the multiple region graph
function makeRegionNodeCompareData(nodeData,start,end){
	this.maxY = 3;
	this.graphY = d3.scaleLinear()
    			.domain([0, 3]) // input 
    			.range([this.height, 0]);
	dataSet = [];
	for(var i = 0; i < this.keys.length; i++){
		dataSet.push([]);
	}
	
	for(var i = 0; i < regionNodeData[nodeData][this.keys[0]].length; i++){
		if(regionNodeData[nodeData][this.keys[0]][i].x >= start && regionNodeData[nodeData][this.keys[0]][i].x <= end){
			for(var j = 0; j < this.keys.length; j++){
				var sum = 0;
				
				
			
				for(var k = 0; k <= j; k++){
					if(this.activeKeys[k].active){
						sum += parseFloat(regionNodeData[nodeData][this.keys[k]][i].y);
					}
				}
				
				dataSet[j].push({"x":regionNodeData[nodeData][this.keys[0]][i].x,"y":sum});
			}
		}
	}
	console.log(dataSet);
	return dataSet;
}

//Handles the data for the single region node usage graphs
function singleRegionLoadData(nodeData,start,end){
	dataSet = [];
	for(var i = 0; i < this.keys.length; i++){
		dataSet.push([]);
	}

	for(var i = 0; i < nodeData[this.keys[0]].length; i++){
		if(nodeData[this.keys[0]][i].x >= start.getTime() && nodeData[this.keys[0]][i].x <= end.getTime()){
			
			for(var j = 0; j < this.keys.length; j++){
				var sum = 0;
				for(var k = 0; k <= j; k++){
					if(this.activeKeys[k].active){
						sum += parseFloat(nodeData[this.keys[k]][i].y);
					}
				}
				dataSet[j].push({"x":nodeData[this.keys[0]][i].x,"y":sum});
			}
		}
	}
	return dataSet;
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



	
  
	
  
