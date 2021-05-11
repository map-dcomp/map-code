#!/usr/bin/env python3.6

import warnings
with warnings.catch_warnings():
        import json
        import os
        import os.path
        import datetime
        import subprocess
        import sys
        import shutil
        import multiprocessing
        import queue
        import math
        import logging
        import logging.config
        import argparse
        

#sorts the flows by start time
def sortJson(json):
        sort_orders = sorted(json, key = lambda i: i["t_first"])
        return sort_orders

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
        namepath = []
        destName = second["collector"]
        currName = first["collector"]
        namepath.append(currName)
        travelTime = 0

        if(destName != currName):
                while(routingTable[currName][destName] != destName):
                        currName = routingTable[currName][destName]
                        namepath.append(currName)
                        
                        if(len(namepath) >= 25):
                                get_logger().info("Flow Path Exceeded 25 Which means we are stuck")
                                get_logger().error("Flow Path Exceeded 25 Which means we are stuck")
                                sys.exit(-1)
                        

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
        get_logger().info("Removed: " + str(num) + " due to port")
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
        get_logger().info("Removed: " + str(num) + " due to being localhost")
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
        get_logger().info("Removed: " + str(num) + " due to ip")
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
        try:
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
                                if(i%100000 == 0):
                                        get_logger().info("Merged " + str(i) +" of " + str(len(new)))
                                newTime = datetime.datetime.strptime(newFlow["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
                                endTime = newTime + datetime.timedelta(microseconds=20000) #create bounds on both sides so only certain flows from master are checked against
                                newTime = newTime - datetime.timedelta(microseconds=20000)
                                
                                r = len(master)-1
                                l = 0
                                mid = 0
                                checkTime = datetime.datetime.strptime(master[mid]["t_first"], "%Y-%m-%dT%H:%M:%S.%f")

                                #finds the closest start time in master compaired to the flow from new
                                while(l<=r):
                                        mid = int(l + (r - l) / 2)
                                        checkTime = datetime.datetime.strptime(master[mid]["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
                                        if(checkTime<newTime):
                                                l=mid+1
                                        elif(checkTime>newTime):
                                                r=mid-1
                                        else:
                                                break 
                                startIndex = mid
                                
                                #puts the index at the begining of the check range
                                while(checkTime>=newTime and startIndex>=0):
                                        startIndex = startIndex-1
                                        checkTime = datetime.datetime.strptime(master[startIndex]["t_first"], "%Y-%m-%dT%H:%M:%S.%f")

                                #loop through range of flows that could match and check to see if they do match
                                repeat = False
                                while(checkTime<endTime and startIndex<len(master)-1):
                                        if looseEqual(master[startIndex],newFlow, packetLoss, timeDifference, netconfig, routingTable):
                                                repeat = True
                                                break
                                        startIndex = startIndex+1
                                        checkTime = datetime.datetime.strptime(master[startIndex]["t_first"], "%Y-%m-%dT%H:%M:%S.%f")
                                if(not repeat):
                                        toMerge.append(newFlow)

                        
                
                        for i, newFlow in enumerate(toMerge):
                                master.append(newFlow)
                        return master
        except:
                get_logger().error("Problem in merge, will need to be rerun:", sys.exc_info()[0])


def do_merge(data_one, data_two, packetLoss, timeDifference, netconfig, routingTable):
        get_logger().info("Merging {} and {}".format(len(data_one), len(data_two)))
        merged = merge(data_one, data_two, packetLoss, timeDifference, netconfig, routingTable)
        get_logger().info("Sorting new merge")
        merged.sort(key=flow_sort_key)
        result_queue.put(merged)
        get_logger().info("Finished merge of {} and {} -> {}".format(len(data_one), len(data_two), len(merged)))

        
def enqueue_work(data_to_merge, pool, num_pending, packetLoss, timeDifference, netconfig, routingTable):
        while len(data_to_merge) > 1:
                (data_one, data_two) = data_to_merge[:2]
                data_to_merge = data_to_merge[2:]
                
                get_logger().info("Enqueueing {} and {}. pending {}".format(len(data_one), len(data_two), num_pending))
                pool.apply_async(func=do_merge, args=[data_one, data_two, packetLoss, timeDifference, netconfig, routingTable])
                num_pending = num_pending + 1
                get_logger().info("Finished enqueue {} and {}. pending {}".format(len(data_one), len(data_two), num_pending))
        using("After enqueing")
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
        get_logger().info("Loading {}".format(filename))
        with open(path, "r") as fin:
                data = json.load(fin)
    
        data = sortJson(data)
        #get_logger().info("Finished Sorting {}".format(filename))
        data = onlyKnown(data, known)
        
        #fout = open(filename + ".json", "w")
        #json.dump(data,fout)
        #fout.close()
        get_logger().info("Flows from " + str(filename) +": " + str(len(data)))
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

def main_method(args):
        get_logger().info("cleaning up mergeFiles from the last run")
        if os.path.exists("mergeFiles"):
                shutil.rmtree("mergeFiles")


        get_logger().info("Merging netflow files...")
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
                        known.append(node["networks"][ip])

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
                        get_logger().info(os.path.dirname(os.path.abspath(__file__)) + "/mergeFiles/" + foldername.capitalize() + ".json")
                        
                        process = subprocess.run(script.split())
                        
                        
                except:
                        get_logger().warn("Node: " + foldername + " isn't recording flows")
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
                                get_logger().info("Waiting for a result")
                                result = result_queue.get()
                                num_pending = num_pending - 1
                                get_logger().info("Got a result {}".format(len(result)))
                                using("After a Result with len "+ str(len(result)))
                                data_to_merge.append(result)

                # all done, shutdown the pool
                pool.close()
                pool.join()
        
        
        final_result = data_to_merge[0]        
        get_logger().info("Number of total flows: " + str(len(final_result)))

        # do sort at the end
        final_result.sort(key=flow_sort_key)
        
        with open("flowData.json", "w") as fout:
                json.dump(final_result, fout, indent=4)
        get_logger().info("Done")


def get_logger():
    return logging.getLogger(__name__)

        
def setup_logging(
    default_path='logging.json',
    default_level=logging.INFO,
    env_key='LOG_CFG'
):
    """
    Setup logging configuration
    """
    path = default_path
    value = os.getenv(env_key, None)
    if value:
        path = value
    if os.path.exists(path):
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)

        
def multiprocess_logging_handler(logging_queue, logconfig, running):
    import time
    setup_logging(default_path=logconfig)

    def process_queue():
        while not logging_queue.empty():
            try:
                record = logging_queue.get(timeout=1)
                logger = logging.getLogger(record.name)
                logger.handle(record)
            except (multiprocessing.Queue.Empty, multiprocessing.TimeoutError) as e:
                # timeout was hit, just return
                pass
        
    while running.value > 0:
        process_queue()

    # process any last log messages
    process_queue()

    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    class ArgumentParserWithDefaults(argparse.ArgumentParser):
        '''
        From https://stackoverflow.com/questions/12151306/argparse-way-to-include-default-values-in-help
        '''
        def add_argument(self, *args, help=None, default=None, **kwargs):
            if help is not None:
                kwargs['help'] = help
            if default is not None and args[0] != '-h':
                kwargs['default'] = default
                if help is not None:
                    kwargs['help'] += ' (default: {})'.format(default)
            super().add_argument(*args, **kwargs)
        
    parser = ArgumentParserWithDefaults(formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("--debug", dest="debug", help="Enable interactive debugger on error", action='store_true')

    args = parser.parse_args(argv)

    if 'multiprocessing' in sys.modules:
        running = multiprocessing.Value('b', 1)
        logging_queue = multiprocessing.Queue()
        logging_listener = multiprocessing.Process(target=multiprocess_logging_handler, args=(logging_queue, args.logconfig,running,))
        logging_listener.start()

        h = logging.handlers.QueueHandler(logging_queue)
        root = logging.getLogger()
        root.addHandler(h)
        root.setLevel(logging.DEBUG)
    else:
        logging_listener = None
        setup_logging(default_path=args.logconfig)

    try:
        if args.debug:
            import pdb, traceback
            try:
                return main_method(args)
            except:
                extype, value, tb = sys.exc_info()
                traceback.print_exc()
                pdb.post_mortem(tb)    
        else:
            return main_method(args)
    finally:
        if logging_listener:
            running.value = 0

            
if __name__ == "__main__":
    sys.exit(main())
