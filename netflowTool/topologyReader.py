#!/usr/bin/env python3.6

#This script reads a .ns file and creates a json file with all the information the website needs to draw the nodes and links
import json
import sys
import os
from pathlib import Path
import csv

def main(argv=None):
        if argv is None:
                argv = sys.argv[1:]

        with open("config.json", "r") as fin:
                config = json.load(fin)

        topologyLocation = config["nsFilePath"]

        nodes = []
        links = []

        with open(topologyLocation, "r") as fin:

                print("Generating netconfig.json...")
                for line in fin:
                        temp = line.split(" ")

                        #looks at the first word of the line and if it is set or tb-set parse the rest of the command 
                        if(temp[0] == "set"):
                                if("node]" in line):
                                        #addes a node
                                        firstSpace = line.find(" ")
                                        endOfName = line.find("[")
                                        name = line[firstSpace:endOfName].replace(" ","").lower()
                                        if("client" in name):
                                                nodes.append({"name":name,"networks":{},"type":"client"})
                                        else:
                                                nodes.append({"name":name,"networks":{},"type":"node"})
                                elif("make-lan" in line):
                                        firstSpace = line.find(" ")
                                        endOfName = line.find("[")
                                        src = line[firstSpace:endOfName].replace(" ","").lower()
                                        nodes.append({"name":src,"networks":{},"type":"lan"})
                                        firstQuote = line.find("\"")
                                        lastQuote = line.rfind("\"")
                                        temp = line[firstQuote:lastQuote].replace("\"","").split(" ")
                                        for i in temp:
                                                links.append({"source":src,"target":i.replace("$","").lower(),"bandwidth":(line.split("\"")[2]).split(" ")[1], "delay":float((line.split("\"")[2]).split(" ")[2][:-4]), "flows":[]})
                                elif("duplex-link" in line):
                                        firstDollar = line.find("$")
                                        srcStart = line.find("$",firstDollar+1)
                                        srcEnd = line.find(" ",srcStart)
                                        src = line[srcStart:srcEnd].replace(" ","").replace("$","").lower()
                                        destStart = line.find("$",srcEnd)
                                        destEnd = line.find(" ",destStart)
                                        dest = line[destStart:destEnd].replace(" ","").replace("$","").lower()

                                        temp = line[destEnd+1:].split(" ")
                                        
                                        links.append({"source":src,"target":dest,"bandwidth":temp[0], "delay":float(temp[1][:-2]), "flows":[]})

                        elif(temp[0] == "tb-set-ip-lan" or temp[0] == "tb-set-ip-link"):
                                name = temp[1].replace("$","").lower()
                                network = temp[2].replace("$","").lower()
                                ip = temp[3][:-1]
                                #assigns the ip with it's network to respective nodes
                                for i in nodes:
                                        if(name == i["name"]):
                                                i["networks"][network] = ip
                                        if(network == i["name"]): #if node is a lan than assign the first 3 numbers of the ip
                                                l = len(ip.split(".")[-1])
                                                i["networks"][network] = ip[:-1*l-1]


        regionInfo = {}
        regionDirStr = config["flowDir"]
        regionDirStr = regionDirStr + "/inputs/scenario"
        regionDir = os.fsencode(regionDirStr)
        clientRegions = list()
        for file in os.listdir(regionDir):
                filename = os.fsdecode(file)
                if filename.endswith(".json"):
                        with open(regionDirStr + "/" + filename, "r") as fin:
                                nodeJson = json.load(fin)

                        if 'region' in nodeJson:
                                nodeName = (filename.split(".")[0]).lower()
                                region = nodeJson["region"]
                                regionInfo[nodeName] = region

                                if 'client' in nodeJson:
                                        
                                        clientNode = nodeJson['client']
                                        if clientNode and region not in clientRegions:
                                                clientRegions.append(region)

        failuresFile = config["flowDir"] + "/inputs/scenario/node-failures.json"
        nodeFailures = []
        try:
                with open(failuresFile, "r") as fin:
                        nodeFailures = json.load(fin)
                for failure in nodeFailures:
                        failure["node"] = failure["node"].lower()
        except:
                print("Run has no node failures.")
                
        dic = {
                "nodes" : nodes,
                "links" : links,
                "regionInfo" : regionInfo,
                "clientRegions" : clientRegions,
                "nodeFailures": nodeFailures
        }

        
        with open("netconfig.json", "w") as fout:
                json.dump(dic, fout, indent=4)
        print("Done")
        

if __name__ == "__main__":
    sys.exit(main())
