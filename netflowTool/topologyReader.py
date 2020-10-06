#!/usr/bin/env python3.6

#This script reads a .ns file and creates a json file with all the information the website needs to draw the nodes and links
import json
import sys
import os
from pathlib import Path


def main(argv=None):
        if argv is None:
                argv = sys.argv[1:]

        comment = """print("start")
        topologyLocation = "topology.ns"
        if(len(sys.argv) != 2):
                try:
                        fin = open(topologyLocation, "r")
                except FileNotFoundError:
                        print("First argument should be the path the topology.ns or topology.ns should be in the same directory as this script")
                        exit()
        else:
                topologyLocation = sys.argv[1]
                fin = open(topologyLocation, "r")"""

        with open("config.json", "r") as fin:
                config = json.load(fin)

        topologyLocation = config["nsFilePath"]


        nodes = []
        links = []

        with open(topologyLocation, "r") as fin:

                print("Generating netconfig.json...")
                for x in fin:
                        temp = x.split(" ")

                        #looks at the first word of the line and if it is set or tb-set parse the rest of the command 
                        if(temp[0] == "set"):
                                if(temp[3] == "node]\n"):
                                        #addes a node
                                        if("client" in temp[1].lower()):
                                                nodes.append({"name":temp[1].lower(),"networks":[],"type":"client"})
                                        else:
                                                nodes.append({"name":temp[1].lower(),"networks":[],"type":"node"})
                                elif(temp[3] == "make-lan"):
                                        src = temp[1]
                                        nodes.append({"name":src.lower(),"networks":[],"type":"lan"})
                                        temp = (x.split("\"")[1]).split(" ") #temp is now a list of connections to the lan
                                        for i in temp:
                                                #creates the link object to be read by the webiste
                                                links.append({"source":src.lower(),"target":i[1:].lower(),"bandwidth":(x.split("\"")[2]).split(" ")[1], "delay":float((x.split("\"")[2]).split(" ")[2][:-4]), "flows":[]})
                                elif(temp[3] == "duplex-link"):
                                        links.append({"source":temp[4][1:].lower(),"target":temp[5][1:].lower(),"bandwidth":temp[6], "delay":float(temp[7][:-2]), "flows":[]})

                        elif(temp[0] == "tb-set-ip-lan" or temp[0] == "tb-set-ip-link"):
                                name = temp[1][1:].lower()
                                network = temp[2][1:].lower()
                                ip = temp[3][:-1]
                                #assigns the ip with it's network to respective nodes
                                for i in nodes:
                                        if(name == i["name"]):
                                                i["networks"].append({"name":network,"ip":ip})
                                        if(network == i["name"]): #if node is a lan than assign the first 3 numbers of the ip
                                                l = len(ip.split(".")[-1])
                                                i["networks"].append({"name":network,"ip":ip[:-1*l-1]})


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



        dic = {
                "nodes" : nodes,
                "links" : links,
                "regionInfo" : regionInfo,
                "clientRegions" : clientRegions
        }

        
        with open("netconfig.json", "w") as fout:
                json.dump(dic, fout, indent=4)
        print("Done")
        

if __name__ == "__main__":
    sys.exit(main())
