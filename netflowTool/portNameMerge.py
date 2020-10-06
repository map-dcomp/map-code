import json
import sys

def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    with open("config.json", "r") as fin:
        config = json.load(fin)
    hostDirStr = config["flowDir"]

    with open("webdata/portNames.json", "r") as fin:
        portNames = json.load(fin)

    with open(hostDirStr + "/inputs/scenario/service-configurations.json", "r") as fin:
        configurations = json.load(fin)
    
    print("Adding custom ports to portNames.json...")

    #Add some default port names
    portNames["5000"] = {"tcp": {"name": "Docker", "description": "Docker Image Transfers"}, "udp": {"name": "Docker", "description": "Docker Image Transfers"}}
    portNames["50042"] = {"tcp": {"name": "Aggregrate Programming", "description": "Part of MAP’s control network"}, "udp": {"name": "Aggregrate Programming", "description": "Part of MAP’s control network"}}
    
    #Add all the services in service-configurations.json
    for service in configurations:
        if(portNames.get(service["serverPort"]) == None):
            portNames[service["serverPort"]] = {"tcp": {"name": service["service"]["artifact"], "description": ""}, "udp": {"name": service["service"]["artifact"], "description": ""}}
        else:
            portNames[service["serverPort"]] = {"tcp": {"name": service["service"]["artifact"] + " and " + portNames[service["serverPort"]]["tcp"]["name"], "description": ""}, "udp": {"name": service["service"]["artifact"] + " and " + portNames[service["serverPort"]]["udp"]["name"], "description": ""}}

    with open("webdata/portNames.json", "w") as fout:
        json.dump(portNames, fout, indent=4)
    print("Done")


if __name__ == "__main__":
    sys.exit(main())