#!/usr/bin/env python3.6

import json
import os
import datetime
import csv



#If total flow records exceed 1GB then the file will need to be split up to be read in by the website
sections = 2
sections = int(input("Split into how many sections: "))


with open("flowData.json", "r") as fin:
    flowData = json.load(fin)
    print(len(flowData))
    end = datetime.datetime.strptime(flowData[-1]["t_last"], "%Y-%m-%dT%H:%M:%S.%f")
    for i,flow in enumerate(flowData):
        flow.pop('collector', None)
        flow.pop('in_packets', None)
        if(datetime.datetime.strptime(flow["t_last"], "%Y-%m-%dT%H:%M:%S.%f") > end):
            end = datetime.datetime.strptime(flow["t_last"], "%Y-%m-%dT%H:%M:%S.%f")

    
flowData = sorted(flowData, key = lambda i: i["t_first"])
start = datetime.datetime.strptime(flowData[0]["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
timeLength = (end-start)/sections
midpoint = start+timeLength


dataSets = []
for i in range(sections):
    dataSets.append([])
index = 0

for i,flow in enumerate(flowData):
    start = datetime.datetime.strptime(flow["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
    end = datetime.datetime.strptime(flow["t_last"], "%Y-%m-%dT%H:%M:%S.%f")
    if(end <= midpoint):
        dataSets[index].append(flow)
    elif(start >= midpoint):
        index+=1
        midpoint = midpoint + timeLength
        dataSets[index].append(flow)
    else:
        fullTime = end-start
        percentFirstHalve = (midpoint-start)/fullTime
        firstHalve = flow.copy()
        secondHalve = flow.copy()
        firstHalve["t_last"] = midpoint.strftime("%Y-%m-%dT%H:%M:%S.%f")
        secondHalve["t_first"] = midpoint.strftime("%Y-%m-%dT%H:%M:%S.%f")
        firstHalve["in_bytes"] = int(flow["in_bytes"]*percentFirstHalve)
        secondHalve["in_bytes"] = flow["in_bytes"]-firstHalve["in_bytes"]

        dataSets[index].append(firstHalve)
        dataSets[index+1].append(secondHalve)
        


for i in range(sections):
    print("Set " + str(i) + " has " + str(len(dataSets[i])) + " flows")
    with open("flowData" + str(i) + ".json", "w") as fout:
        json.dump(dataSets[i], fout, indent=4)




