#!/usr/bin/env python3.6

import json
import os
import datetime
import subprocess
import sys
import shutil
import multiprocessing
import queue

#does a selection sort based on start time
#is already close to sorted so it's much closer to best case then worse case
def sortJson(json):
        for i in range(1,len(json)):
                j = i
                while(json[j]["t_first"] <= json[j-1]["t_first"] and j > 0):
                        temp = json[j]
                        json[j] = json[j-1]
                        json[j-1] = temp
                        j = j - 1
        return json

#checks if 2 flows are the same flow    
def looseEqual(first,second, packetLoss, timeDifference, netconfig, routingTable):
        if(first["src4_addr"] != second["src4_addr"]):
                return False
        if(first["dst4_addr"] != second["dst4_addr"]):
                return False
        if(first["proto"] != second["proto"]):
                return False
        
        if "src_port" in first and "src_port" in second:
                if first["src_port"] != second["src_port"]:
                        return False
                
        if "dst_port" in first and "dst_port" in second:
                if first["dst_port"] != second["dst_port"]:
                        return False

        #allows for minor packet loss
        upper = first["in_packets"] + packetLoss
        lower = first["in_packets"] - packetLoss
        if(second["in_packets"] < lower or second["in_packets"] > upper):
                return False
        

        #finds the path between the two collection points to be able to calculate the expected delay
        namepath = [];
        destName = second["collector"]
        currName = first["collector"]
        namepath.append(currName)
        travelTime = 0
        if(destName != currName):
                while(routingTable[currName][destName] != destName):
                        currName = routingTable[currName][destName]
                        namepath.append(currName)
                namepath.append(destName)
                i = 0
                while(i < len(namepath)-1):
                        for link in netconfig["links"]:
                                if((link["source"] == namepath[i] and link["target"] == namepath[i+1]) or (link["target"] == namepath[i] and link["source"] == namepath[i+1])):
                                        travelTime += float(link["delay"])
                        i = i+1

        #checks the start and stop time
        date1 = datetime.datetime.strptime(first["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
        date2 = datetime.datetime.strptime(second["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
        upper = date1 + datetime.timedelta(microseconds=timeDifference*1000) + datetime.timedelta(microseconds=(travelTime*1000))
        lower = date1 - datetime.timedelta(microseconds=timeDifference*1000) + datetime.timedelta(microseconds=(travelTime*1000))
        if(date2 < lower or date2 > upper):
                return False
        upper = date1 + datetime.timedelta(microseconds=timeDifference*1000) - datetime.timedelta(microseconds=(travelTime*1000))
        lower = date1 - datetime.timedelta(microseconds=timeDifference*1000) - datetime.timedelta(microseconds=(travelTime*1000))
        if(date2 < lower or date2 > upper):
                return False

        date1 = datetime.datetime.strptime(first["t_last"], "%Y-%m-%dT%H:%M:%S.%f")
        date2 = datetime.datetime.strptime(second["t_last"], "%Y-%m-%dT%H:%M:%S.%f")
        upper = date1 + datetime.timedelta(microseconds=timeDifference*1000) + datetime.timedelta(microseconds=(travelTime*1000))
        lower = date1 - datetime.timedelta(microseconds=timeDifference*1000) + datetime.timedelta(microseconds=(travelTime*1000))
        if(date2 < lower or date2 > upper):
                return False
        upper = date1 + datetime.timedelta(microseconds=timeDifference*1000) - datetime.timedelta(microseconds=(travelTime*1000))
        lower = date1 - datetime.timedelta(microseconds=timeDifference*1000) - datetime.timedelta(microseconds=(travelTime*1000))
        if(date2 < lower or date2 > upper):
                return False

        return True


#removes a port from dataset
#used to help filter my test cases
def removePort(port, data):
        i = 0
        num = 0
        while(i < len(data)):
                if 'dst_port' in data[i]:
                        if(data[i]['dst_port'] == int(port)):
                                num = num+1
                                data.remove(data[i])
                        else:
                                i = i+1
                else:
                        i = i+1 
        print("Removed: " + str(num) + " due to port")
        return data

#removes a host from dataset
def removeHost(host, data):
        i = 0
        num = 0
        while(i < len(data)):
                if(data[i]['src4_addr'] == host or data[i]['dst4_addr'] == host):
                        num = num+1
                        data.remove(data[i])
                else:
                        i=i+1   
        print("Removed: " + str(num) + " due to being localhost")
        return data

#Filters out and range of IPs
def filterIP(data):
        i = 0
        num = 0
        while(i < len(data)):
                srcSplit = data[i]['src4_addr'].split('.')
                destSplit = data[i]['dst4_addr'].split('.')
                if(srcSplit[0] == "155" or destSplit[0] == "155"):
                        num = num+1
                        data.remove(data[i])
                elif(srcSplit[0] == "172" and srcSplit[1] == "30"):
                        num = num+1
                        data.remove(data[i])
                elif(destSplit[0] == "172" and destSplit[1] == "30"):
                        num = num+1
                        data.remove(data[i])
                else:
                        i=i+1
        print("Removed: " + str(num) + " due to ip")
        return data

#Narrows down data to flows that have a start and end point that was in the topology.ns, other ip's will mess things up
def onlyKnown(data, known):
        newData = []
        for flow in data:
                once = True
                i = 0
                if(flow['src4_addr'] != flow['dst4_addr']):
                        while(i < len(known)):
                                if(known[i] == flow['src4_addr']):
                                        j = 0
                                        while(j < len(known)):
                                                if(known[j] == flow['dst4_addr'] and i != j and once):
                                                        once = False
                                                        newData.append(flow)
                                                j = j+1
                                elif(known[i] == flow['dst4_addr']):
                                        j = 0
                                        while(j < len(known)):
                                                if(known[j] == flow['src4_addr'] and i != j and once):
                                                        once = False
                                                        newData.append(flow)
                                                j = j+1
                                i = i+1
        return newData

#merges new into master
def merge(master, new, packetLoss, timeDifference, netconfig, routingTable):
        """
        Merge master and new. master is modified by this operation.

        The new master is returned.
        """
        
        #if master is empty add all from new 
        if len(master) == 0:
                return new
        elif len(new) == 0:
                return master
        else:
                toMerge = []
                for i, newFlow in enumerate(new):
                        for j, masterFlow in enumerate(master):
                                if looseEqual(masterFlow,newFlow, packetLoss, timeDifference, netconfig, routingTable):
                                        # found duplicate flow
                                        break
                        else:
                                # not found
                                master.append(newFlow)

                comment = """
                i = 0
                j = 0
                #both are sorted so preform merge in a way that mantains sorted order 
                while(i < len(master) and j < len(new)):
                        if(looseEqual(master[i],new[j])):
                                j = j+1
                        else:
                                #need back test for equals too
                                if(master[i]["t_first"] > new[j]["t_first"]):
                                        master.insert(i,new[j])
                                        j = j+1
                                i = i+1

                i = len(master)-1
                while(j < len(new)):
                        repeat = False
                        while(i > 0):
                                if(looseEqual(master[i],new[j])):
                                        repeat = True
                                        break
                                else:
                                        i = i-1
                        i = len(master)-1
                        if(not repeat):
                                master.append(new[j])
                                i = i+1
                        j = j+1"""

                return master


def do_merge(data_one, data_two, packetLoss, timeDifference, netconfig, routingTable):
        print("Merging {} and {}".format(len(data_one), len(data_two)))
        merged = merge(data_one, data_two, packetLoss, timeDifference, netconfig, routingTable)
        result_queue.put(merged)
        print("Finished merge of {} and {} -> {}".format(len(data_one), len(data_two), len(merged)))

        
def enqueue_work(data_to_merge, pool, num_pending, packetLoss, timeDifference, netconfig, routingTable):
        while len(data_to_merge) > 1:
                (data_one, data_two) = data_to_merge[:2]
                data_to_merge = data_to_merge[2:]
                
                print("Enqueueing {} and {}. pending {}".format(len(data_one), len(data_two), num_pending))
                pool.apply_async(func=do_merge, args=[data_one, data_two, packetLoss, timeDifference, netconfig, routingTable])
                num_pending = num_pending + 1
                print("Finished enqueue {} and {}. pending {}".format(len(data_one), len(data_two), num_pending))

        return data_to_merge, num_pending
                
        
def get_results():
        """
        Get as many results are currently available.
        """
        results = list()
        while True:
                try:
                        result = result_queue.get_nowait()
                        results.append(result)
                except queue.Empty:
                        break
        return results
                

def load_file(known, filename, path):
        print("Loading {}".format(filename))
        with open(path, "r") as fin:
                data = json.load(fin)

        data = sortJson(data)
        data = onlyKnown(data, known)
        #fout = open(filename + ".json", "w")
        #json.dump(data,fout)
        #fout.close()
        print("Flows from " + str(filename) +": " + str(len(data)))
        #gives every flow where it was collected
        fname, _ = os.path.splitext(filename)
        tag = fname.lower()
        for flow in data:
                flow["collector"] = tag
        return data


def load_all_data(pool, mergeFilesDir, known):
        data_to_merge = list()
        load_results = list()

        # load all of the files in parallel
        for file in os.listdir(mergeFilesDir):
                filename = os.fsdecode(file)
                path = "mergeFiles/" + str(filename)
                if filename.endswith(".json"):
                        async_result = pool.apply_async(func=load_file, args=[known, filename, path])
                        load_results.append(async_result)

        # wait for the results
        for async_result in load_results:
                result = async_result.get()
                data_to_merge.append(result)

        return data_to_merge
        

def flow_sort_key(flow):
        """
        Create a key that does a total order sort of the data.
        """
        result = "t_first_{}_{}".format(flow['t_first'], json.dumps(flow))
        return result


# needs to be global to allow the other processes to access it
result_queue = multiprocessing.Queue()

def main(argv=None):
        if argv is None:
                argv = sys.argv[1:]

        print("cleaning up mergeFiles from the last run")
        if os.path.exists("mergeFiles"):
                shutil.rmtree("mergeFiles")


        print("Merging netflow files...")
        master = []

        with open("config.json", "r") as fin:
                config = json.load(fin)
        hostDirStr = config["flowDir"]
        packetLoss = int(config["maxPacketLoss"])
        timeDifference = int(config["maxTimeDifference"])
        
        with open("netconfig.json", "r") as fin:
                netconfig = json.load(fin)

        known = []
        for node in netconfig["nodes"]:
                for ip in node["networks"]:
                        known.append(ip["ip"])

        with open("routingTable.json", "r") as fin:
                routingTable = json.load(fin)

        #Looks through flow directory for folders with nfcapd files
        hostDir = os.fsencode(hostDirStr)
        for folder in os.listdir(hostDir):
                foldername = os.fsdecode(folder)
                try:
                        flowDirStr = hostDirStr + "/" + foldername + "/agent/flows"
                        flowDir = os.fsencode(flowDirStr)
                        flows = []
                        #loops through all the nfcapd files to get their names
                        for file in os.listdir(flowDir):
                                filename = os.fsdecode(file)
                                flows.append(filename)

                        flows.sort() #sort the names so we can get the first and last file
                        start = flows[0]
                        end = flows[-1]

                        #runs a shell script that runs a command because subprocess had trouble with writing output to a file
                        #runs nfdump -o json -R start:end > foldername.txt, this groups all the flows of a node into one file to be merged later
                        script = os.path.dirname(os.path.abspath(__file__)) + "/gatherNFCAPDFiles.sh " + hostDirStr + "/" + foldername + "/agent/flows " + start + " " + end + " " + os.path.dirname(os.path.abspath(__file__)) + "/mergeFiles/" + foldername.capitalize() + ".json"
                        process = subprocess.run(script.split())


                except:
                        print("Node: " + foldername + " isn't recording flows")
                        continue

        mergeFilesDir = os.fsencode("mergeFiles")
        
        #finds every json from nfdump and merges them together
        
        with multiprocessing.Pool() as pool:
                data_to_merge = load_all_data(pool, mergeFilesDir, known)

                # merge all of the data in parallel
                num_pending = 0
                done = False
                while not done:
                        results = get_results()
                        num_pending = num_pending - len(results)
                        data_to_merge.extend(results)

                        data_to_merge, num_pending = enqueue_work(data_to_merge, pool, num_pending, packetLoss, timeDifference, netconfig, routingTable)

                        if num_pending <= 0 and len(data_to_merge) < 2:
                                done = True
                        else:
                                # wait for at least one result, this should be
                                # better than doing a sleep
                                print("Waiting for a result")
                                result = result_queue.get()
                                num_pending = num_pending - 1
                                print("Got a result {}".format(len(result)))
                                
                                data_to_merge.append(result)

                # all done, shutdown the pool
                pool.close()
                pool.join()
        
        
        final_result = data_to_merge[0]        
        print("Number of total flows: " + str(len(final_result)))

        # do sort at the end
        final_result.sort(key=flow_sort_key)
        
        with open("flowData.json", "w") as fout:
                json.dump(final_result, fout, indent=4)
        print("Done")



if __name__ == "__main__":
    sys.exit(main())
