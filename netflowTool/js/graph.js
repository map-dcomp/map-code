var color = d3.scaleOrdinal(["#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"]);
var thisVis;
var portNames;
//Classes\\
//Main parent class for all the graphs 
class Vis {
	constructor(svg,title){
		this.margin = {top: 50, right: 50, bottom: 50, left: 50};
		this.width = document.getElementById("mainVis").getBoundingClientRect().width - this.margin.left - this.margin.right 
		this.height = document.getElementById("mainVis").getBoundingClientRect().height - this.margin.top - this.margin.bottom;
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
		console.log("Updating Pie Chart");
		this.rawData = flows;
		this.start = start;
		this.end = end;
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
				console.log(portNames);
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
	move(location){ //Creates a new simulation that is the same as this one in the new location and then deletes the current one
		visualizations.push(new pieChart(location,this.titleText,this.makeData));

		d3.select(this.svgText).select('svg').remove();

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
		this.timeMarkers = [];
	}
	update(flows,start,end){
		console.log("Updating Line Graph");
		this.rawData = flows;
		this.start = start;
		this.end = end;
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
	move(location){//Creates a new simulation that is the same as this one in the new location and then deletes the current one
		visualizations.push(new lineGraph(location,this.titleText,this.unit,this.makeData));
		d3.select(this.svgText).select('svg').remove();
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
		var lineColor = d3.scaleOrdinal(d3.schemeCategory10);
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
				.attr("cy", 0 - (this.margin.top / 2) + (this.margin.top / 2)*(this.keys.length-1-i))
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
				.attr("y", 0 - (this.margin.top / 2) + (this.margin.top / 2)*(this.keys.length-1-i))
				.attr("text-anchor", "left")
			     	.attr("alignment-baseline", "middle")
				.text(this.keys[i]);
			
		}
		actKeys = this.activeKeys;
		this.timeMarker = [];
	}
	update(flows,start,end){
		console.log("Updating Stacked Area Graph");
		console.log("what");
		this.rawData = flows;
		this.start = start;
		this.end = end;
		console.log("before making data");
		this.data = this.makeData(flows,start,end);
		console.log("after making data");
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
	move(location){//Creates a new simulation that is the same as this one in the new location and then deletes the current one
		
		visualizations.push(new stackedAreaGraph(location,this.titleText,this.unit,this.makeData,this.keys,this.scaleType));
		d3.select(this.svgText).select('svg').remove();
	}
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
	console.log("flows length",flows.flows.length)
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

function makeDelayCDF(times){
	var startTime = new Date(flowData[0].startTime);
	var data = [];
	var temp = {}
	for(var i = 0; i < times.length; i++){
		data.push([])
	}
	
	delaysObj = {}
	var index = 0;
	for(var i = 0; i < flowData.length; i++){
		for(var j = 0; j < times.length; j++){
			if(flowData[i].startTime.getTime() <= (startTime.getTime() + times[j].end) && flowData[i].endTime.getTime() >= startTime.getTime() + times[j].start){
				srcIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(flowData[i]["src4_addr"])]["__data__"].name]);
				destIndex = regionIndex(regionInfo[node["_groups"][0][findNodebyIP(flowData[i]["dst4_addr"])]["__data__"].name]);
				if(srcIndex!=destIndex){
					data[j].push(flowData[i].delay);
				}
			}
		}
	}
	console.log(data);

	for(var j = 0; j < times.length; j++){
		data[j].sort(function(a, b){
			return a - b;
		});
		for(var i = 0; i < data[j].length; i++){
			if(delaysObj[data[j][i]] == undefined){
				delaysObj[data[j][i]] = 0;
			}else{
				delaysObj[data[j][i]]++;
			}
		}
		delays = Object.keys(delaysObj);
		delays.sort(function(a, b){
			return a - b;
		});
		data[j] = [{"y":0,"x":0}];
		for(var i = 0; i < delays.length; i++){
			var index = i;
			temp = {"y":0,"x":parseInt(delays[i])}
			while(index>=0){
				temp.y+=delaysObj[delays[index]];
				index--;
			}
			data[j].push({"y":data[j][data[j].length-1].y,"x":temp.x})
			data[j].push(temp);
		}
		
		for(var i = 0; i < data[j].length; i++){
			data[j][i].y/=data[j][data[j].length-1].y;
		}
		
		data[j].push({"y":1,"x":data[j][data[j].length-1].x+1});
	}
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
				if(this.activeKeys[1].active){
					data[1][index].y += flows.flows[i].bytes/1000;
				}
				if(this.activeKeys[2].active){
					data[2][index].y += flows.flows[i].bytes/1000;
				}
			}else if(flows.flows[i].bytes > short && flows.flows[i].bytes <= med){
				if(this.activeKeys[1].active){
					data[1][index].y += flows.flows[i].bytes/1000;
				}
				if(this.activeKeys[2].active){
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

function makeNumBPSData(flows,start,end){ //Alerternate 
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
	debug = {}
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
				debug[flows.flows[i]["src_port"]] = 0;
				debug[flows.flows[i]["dst_port"]] = 0
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

function makeRegionNodeCompareData(nodeData,start,end){
	this.maxY = 3;
	this.graphY = d3.scaleLinear()
    			.domain([0, 3]) // input 
    			.range([this.height, 0]);
	dataSet = [];
	for(var i = 0; i < this.keys.length; i++){
		dataSet.push([]);
	}
	//console.log(this.keys);
	
	for(var i = 0; i < regionNodeData[nodeData][this.keys[0]].length; i++){
		if(regionNodeData[nodeData][this.keys[0]][i].x >= start && regionNodeData[nodeData][this.keys[0]][i].x <= end){
			for(var j = 0; j < this.keys.length; j++){
				var sum = 0;
				
				
			
				for(var k = 0; k <= j; k++){
					if(this.activeKeys[k].active){
						sum += parseFloat(regionNodeData[nodeData][this.keys[k]][i].y);
					}
				}
				
				//console.log(regionNodeData[this.keys[0]][i].x);
				dataSet[j].push({"x":regionNodeData[nodeData][this.keys[0]][i].x,"y":sum});
			}
		}
	}
	
	//console.log(dataSet);
	
	return dataSet;
}

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
				
				//console.log(regionNodeData[this.keys[0]][i].x);
				dataSet[j].push({"x":nodeData[this.keys[0]][i].x,"y":sum});
			}
		}
	}
	/*for(var i = 0; i <this.keys.length;i++){
		if(this.activeKeys[i].active){
			if(nodeData[this.keys[i]].length!=0){
				dataSet.push(nodeData[this.keys[i]]);
			}else{
				dataSet.push([{"x":0,"y":0}]);
			}
		}else{
			dataSet.push([{"x":0,"y":0}]);
		}
	}*/
	
	
	return dataSet;
}


//This is really just a rough approximation and should be replace with a better formula 
function fairUseOverRange(time, steprate, data){
	var num = 0;
	var rangeEnd = new Date(time);
	rangeEnd.setMilliseconds(rangeEnd.getMilliseconds() + steprate);

	var r = data.flows.length-1
	var l = 0
	var mid = 0
	var checkTime = data.flows[mid].startTime

	while(l<=r){
		mid = ~~(l + (r - l) / 2)
		checkTime = data.flows[mid].startTime
		if(checkTime<rangeEnd){
			l=mid+1
		}else if(checkTime>rangeEnd){
			r=mid-1
		}else{
			break;
		}
	}
	startIndex = mid
	while(startIndex < data.flows.length && data.flows[startIndex].startTime.getTime() <= rangeEnd){
		if(data.flows[startIndex].startTime.getTime() <= rangeEnd && data.flows[startIndex].endTime.getTime() >= time.getTime()){
			num++;
		}
		startIndex++;
	}
	startIndex = mid-1
	while(startIndex >= 0){
		if(data.flows[startIndex].startTime.getTime() <= rangeEnd && data.flows[startIndex].endTime.getTime() >= time.getTime()){
			num++;
		}
		startIndex--;
	}
	return num;
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

function redraw(){
	console.log("redrawing");
	if(document.getElementById("svg") != null){
		document.getElementById("svg").remove();
	}
	
	var tempData = thisVis.rawData;
	var tempStart = thisVis.start;
	var tempEnd = thisVis.end;
	document.getElementById("mainVis").style.bottom = "" + document.getElementById("sliderBar").clientHeight + "px";
	
	if(thisVis instanceof stackedAreaGraph){
		thisVis = new stackedAreaGraph("#mainVis", thisVis.titleText, thisVis.unit, thisVis.makeData, thisVis.keys);
	}else if(thisVis instanceof lineGraph){
		thisVis = new lineGraph("#mainVis",thisVis.titleText, thisVis.unit, thisVis.makeData);
	}else if(thisVis instanceof pieChart){
		thisVis = new pieChart("#mainVis", thisVis.titleText, thisVis.makeData);
	}
	thisVis.update(tempData,tempStart,tempEnd);
}

window.addEventListener("resize", redraw);

//Creation Scripts
function makeLineGraph(svg, title, unit, makeData){
	thisVis = new lineGraph(svg, title, unit, makeData);
	return thisVis;
}

function makeStackedAreaGraph(svg, title, unit, makeData, keys, scaleType){
	thisVis = new stackedAreaGraph(svg, title, unit, makeData, keys, scaleType);
    return thisVis;
}
function makePieChart(svg, title, makeData){
	thisVis = new pieChart(svg, title, makeData);
	return thisVis;
}

document.getElementById("mainVis").style.bottom = "" + document.getElementById("sliderBar").clientHeight + "px";

// convert milliseconds to minutes and then format to display
function msToMinutesDisplay(ms) {
    var minutes = ms / 1000.0 / 60.0;
    return minutes.toFixed(2);
}

function displayTimeRange(start, end) {
    var startDiffMs = start.getTime() - window.opener.trueStart.getTime();
    var endDiffMs = end.getTime() - window.opener.trueStart.getTime();

    $("#time").val(msToMinutesDisplay(startDiffMs) + " - " + msToMinutesDisplay(endDiffMs));
}

function displayCurrentTime(time) {
    var msFromStart = time.getTime() - window.opener.trueStart.getTime();
    document.getElementById("current_time").innerHTML = msToMinutesDisplay(msFromStart);
}

function displaySelectedLink(link) {
    if(link != undefined) {
	var element = document.getElementById("selected_link");
	element.innerHTML = link.source.name + " - " + link.target.name + " " + link.bandwidth;
    }
}

$(function(){
	var timeslider = $( "#slider" ).slider({
	  range: true,
	  min: window.opener.trueStart.getTime(),
	  max: window.opener.trueEnd.getTime(),
	  values: [ window.opener.trueStart.getTime(), window.opener.trueEnd.getTime()],
	  slide: function( event, ui ) { //Everytime slider moves call this function
	var left = new Date(ui.values[0]);
	var right = new Date(ui.values[1]);
	  //narrowView(left,right,flowData); //Tells the visualizations the time bounds
		window.opener.syncTimeSliders(left,right);
	  	window.opener.narrowView(left,right,window.opener.flowData);
	  }
	});
	displayTimeRange(window.opener.trueStart, window.opener.trueEnd);
	displayCurrentTime(window.opener.trueEnd);
});

function printTime(left, right){
	console.log(left,right);
}

//Function that handels updating the sliders and it's text
function updateTimeSlider(left, right){
	$(function(){
		$( "#slider" ).slider("values",[ left.getTime(), right.getTime()]); //updates time slider
	});
	displayTimeRange(left, right);
	displayCurrentTime(right);
}

