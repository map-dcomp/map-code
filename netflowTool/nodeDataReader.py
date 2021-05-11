#!/usr/bin/env python3

import sys
import os
import os.path

import json
from pathlib import Path
import csv
    
    
def main(argv=None):
    
    with open("config.json", "r") as fin:
        config = json.load(fin)
    compare_app = config["outputFolderOf_compare-app-load.py"]
    compare_app = Path(compare_app)
    if not compare_app.exists():
        print("Output folder not found.")
        return 1


    
    
    
    sim_output = Path(config["flowDir"])
    if not sim_output.exists():
        print("not found")
        return 1

    firstTime = 2000000000000
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        agent_dir = node_dir / 'agent'
        if not agent_dir.is_dir():
            continue

        for node_name_dir in agent_dir.iterdir():
            if not node_name_dir.is_dir():
                continue

            for time_dir in node_name_dir.iterdir():
                if not time_dir.is_dir():
                    continue
                try:
                    time = float(os.path.basename(time_dir))
                    if(time < firstTime):
                        firstTime = time
                except ValueError:
                    pass

    print(firstTime)
    index = 0
    actualData = {}
    for file in compare_app.iterdir():
        if(str(file).find("load-CPU-")>0 and str(file).find(".csv")>0 and not str(file).find("app")>0):
            dataArray = []
            
            print(file)
            region = str(file)[str(file).find("CPU-")+4:len(str(file))]
            region = region.replace(".csv","")
            with open(file, 'r') as f: #reads into array
                data = csv.reader(f)
                for row in data:
                    try:
                        float(row[0])
                        dataArray.append(row)
                        dataArray[len(dataArray)-1][0] = float(dataArray[len(dataArray)-1][0])*60000
                    except:
                        apps = row
            
            actualData[region] = {}

            for i in range(len(apps)): 
                if(apps[i] != "relative minutes"):
                    apps[i] = apps[i].replace(" ","_") #spaces cause problems with javascript classes
                    apps[i] = apps[i].replace("app","")
                    actualData[region][apps[i]] = []

            time = 10000
            i = 0
            while(i<len(dataArray)): #colapses data into 10 second intervals
                hasPoint = []
                for num in range(len(dataArray[0])-1):
                    hasPoint.append(False)
                
                while(i < len(dataArray) and dataArray[i][0]<time):
                    for point in range(len(dataArray[i])-1):
                        if(dataArray[i][point+1] != '' and not hasPoint[point]):
                            actualData[region][apps[point+1]].append({"x": (time - 10000)+firstTime, "y":float(dataArray[i][point+1])})
                            hasPoint[point] = True
                    i+=1
                time+=10000
                i+=1
    
    allRegionData = {}
    for region in actualData:
        length = 0
        for app in actualData[region]:
            length = len(actualData[region][app])
        allRegionData[region] = []
    for region in allRegionData:  
        for i in range(length):
            allRegionData[region].append({"x":0, "y":0})

    for region in actualData:
        for app in actualData[region]:
            if(not(app.find("capacity") > 0)):
                index = 0
                for point in actualData[region][app]:
                    allRegionData[region][index]["x"] = point["x"]
                    allRegionData[region][index]["y"] += point["y"]
                    index+=1
            
    actualData["All"] = allRegionData
    with open("nodeData.json", "w") as fout:
        json.dump(actualData, fout, indent=4)

if __name__ == "__main__":
    sys.exit(main())
