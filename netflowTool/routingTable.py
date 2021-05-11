#!/usr/bin/env python3.6

#This script creates a routing table from the output of topologyReader.py. This table is not matched to the actual simulation and should be replaced by some Quagga routing file in the future

import json
import copy
import sys

links = None
graph = None
table = dict()

#returns node based on name
def findNode(name,nodes):
        for node in nodes:
                if(node["name"] == name):
                        return node

        print("node not found " + name + " \n\n\n")

#returns link based on names
def findLink(src,dest):
        global links
        for link in links:
                if(link["source"] == src and link["target"] == dest):
                        return link
                elif(link["source"] == dest and link["target"] == src):
                        return link
        print("link not found\n\n\n")

#checks if node has be visited in a run of findPath yet
def visited(name,pile):
        for i in pile:
                if(i == name):
                        return True
        return False

def findPath(source, target):
        global graph
        
        queue = []
        visit = []
        path = []
        nodes = copy.deepcopy(graph)
        #print(source["name"])
        queue.append(findNode(source["name"], nodes))
        
        #preforms a BFS to find the target from the source
        while(len(queue) != 0):
                temp = queue[0]
                queue.pop(0)
                if(temp["name"] == target["name"]):
                        break
                for link in temp["links"]:
                        if(temp["name"] == link["source"]):
                                if(not visited(link["target"],visit)):
                                        visit.append(link["target"])
                                        #print(link)
                                        node = findNode(link["target"], nodes)
                                        queue.append(node)
                                        try:
                                                if(int(findLink(node["back"],node["name"])["bandwidth"][:-2]) > int(link["bandwidth"][:-2])): #compares the old link with new link to try and choose the                                                                                                                                                      #better link
                                                        node["back"] = temp["name"]
                                                
                                        except KeyError:
                                                node["back"] = temp["name"] #if there was a key error then that means that the node doesn't have a back yet
                        elif(temp["name"] == link["target"]):
                                if(not visited(link["source"],visit)):
                                        visit.append(link["source"])
                                        #print(link)
                                        node = findNode(link["source"], nodes)
                                        queue.append(node)
                                        try: 
                                                if(int(findLink(node["back"],node["name"])["bandwidth"][:-2]) > int(link["bandwidth"][:-2])): #compares the old link with new link to try and choose the                                                                                                                                                      #better link
                                                        node["back"] = temp["name"]
                                        except KeyError:
                                                node["back"] = temp["name"] #if there was a key error then that means that the node doesn't have a back yet
                                              
        #back tracks from dest to source
        while(temp["name"] != source["name"]):
                
                path.insert(0,temp["name"])
                temp = findNode(temp["back"], nodes)
                
        path.insert(0,source["name"])
        i = 0

        #populates the table with info of this run
        while(i < len(path)-1):
                try: 
                        table[path[i]][target["name"]] = path[i+1]
                except KeyError:
                        table[path[i]] = {}
                        table[path[i]][target["name"]] = path[i+1]
                i = i+1

                
def main(argv=None):
        if argv is None:
                argv = sys.argv[1:]
                
        with open("netconfig.json", "r") as fin:
                data = json.load(fin)

        global links
        links = data["links"]
        global graph
        graph = data["nodes"]


        print("Generating routingTable.json...")

        #gives every node a list of links that it has
        for node in graph:
                node["links"] = []
                for link in links:
                        if(node["name"] == link["source"] or node["name"] == link["target"]):
                                node["links"].append(link)

        #finds paths between all the nodes      
        for node in graph:
                for node2 in graph:
                        if(not (node == node2)):
                                findPath(node,node2)
                                        


        with open("routingTable.json", "w") as fout:
                json.dump(table, fout, indent=4)
        print("Done")
                
if __name__ == "__main__":
    sys.exit(main())
