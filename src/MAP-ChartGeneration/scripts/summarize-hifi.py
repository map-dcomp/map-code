#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
# the exception of the dcop implementation identified below (see notes).
# 
# Dispersed Computing (DCOMP)
# Mission-oriented Adaptive Placement of Task and Data (MAP) 
# 
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
# TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#BBN_LICENSE_END
#!/usr/bin/env python3

import warnings
with warnings.catch_warnings():
    import re
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    from pathlib import Path
    import csv
    import ipaddress
    import statistics
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)

class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"


class LazyStats(Base):
    """
    @param track_median if true, track the median value. This requires all values to be stored.
    """
    def __init__(self, track_median=False):
        self.sum = 0
        self.count = 0
        if track_median:
            self.values = list()
        else:
            self.values = None

        
    def add_value(self, value):
        self.sum = self.sum + float(value)
        self.count = self.count + 1
        if self.values is not None:
            self.values.append(value)


    def average(self):
        return self.sum / self.count

    def median(self):
        if self.values is None:
            return float('nan')
        else:
            return statistics.median(self.values)


    @staticmethod
    def combine_stats(stats):
        '''
        Combine multiple stats objects into a single stats object.
        
        @param stats a collection of LazyStats objects
        @return a new LazyStats object
        '''
        combined = LazyStats()
        for stat in stats:
            combined.sum = combined.sum + stat.sum
            combined.count = combined.count + stat.count
        return combined
    
            
class Node(Base):
    def __init__(self, name):
        self.name = name
        self.region = None
        self.dcop = False
        self.dns = False
        self.rlg = False
        self.client = False
        self.ips = set()


    def read_extra_data(self, regions, json_data):
        if 'region' in json_data:
            regionName = json_data['region']
            if regionName not in regions:
                regions[regionName] = Region(regionName)

            self.region = regions[regionName]

        if 'client' in json_data:
            self.client = json_data['client']

        self.dcop = json_data.get('DCOP', False)
        self.rlg = json_data.get('RLG', False)
        self.dns = json_data.get('dns', False)

        
class Region(Base):
    
    def __init__(self, name):
        self.name = name
        self.unknown_host = dict()
        self.dcop_plan_count = 0
        self.rlg_plan_count = 0
        self.oom_count = 0
        self.services = set()
        self.servers_contacted = dict()
        self.subnets = set()
        self.client_latency_stats = dict()
        self.clients_contacted = dict()
        self.server_latency_stats = dict()
        self.git_version = None
        self.min_time = None
        self.max_time = None


    def update_times(self, log_time):
        if log_time is not None:
            if self.min_time is None:
                self.min_time = log_time
            elif log_time < self.min_time:
                self.min_time = log_time
            
            if self.max_time is None:
                self.max_time = log_time
            elif log_time > self.max_time:
                self.max_time = log_time

    
    def add_unknown_host(self, hostname):
        self.unknown_host[hostname] = self.unknown_host.get(hostname, 0) + 1

        
    def unknown_host_count(self):
        return sum(self.unknown_host.values())

    
    def add_client_service_contact(self, service, server, latency):
        if service not in self.servers_contacted:
            self.servers_contacted[service] = set()
        self.servers_contacted[service].add(server)

        if service not in self.client_latency_stats:
            self.client_latency_stats[service] = LazyStats(track_median=True)
        self.client_latency_stats[service].add_value(latency)

        
    def add_server_latency(self, service, client, latency):
        if service not in self.clients_contacted:
            self.clients_contacted[service] = set()
        self.clients_contacted[service].add(client)

        if service not in self.server_latency_stats:
            self.server_latency_stats[service] = LazyStats()
        self.server_latency_stats[service].add_value(latency)
        

def common_logfile_matches(region, line):
    unknown_host_match = re.search(r'Unable to find host (\S+)\.\s+', line)
    if unknown_host_match:
        host = unknown_host_match.group(1)
        region.add_unknown_host(host)

    oom_match = re.search(r'OutOfMemory', line)
    if oom_match:
        region.oom_count = region.oom_count + 1

    log_time = map_utils.log_line_to_time(line, None)
    region.update_times(log_time)

    
def process_client_logfile(region, logfile):
    '''
    @param region the Region object for the client
    @param logfile the file to read
    '''

    with open(logfile) as f:
        for line in f:
            common_logfile_matches(region, line)


def process_agent_logfile(region, logfile):
    '''
    @param region the Region object for the agent
    @param logfile the file to read
    '''
    rlg_plan_re = re.compile(r'Publishing RLG plan:')
    dcop_plan_re = re.compile(r'Publishing DCOP plan:')
    git_version_re = re.compile(r'\.HiFiAgent.*Git version: (\S+)$')
    
    with open(logfile) as f:
        for line in f:
            common_logfile_matches(region, line)
            
            rlg_plan_match = rlg_plan_re.search(line)
            if rlg_plan_match:
                region.rlg_plan_count = region.rlg_plan_count + 1
                
            dcop_plan_match = dcop_plan_re.search(line)
            if dcop_plan_match:
                region.dcop_plan_count = region.dcop_plan_count + 1

            git_version_match = git_version_re.search(line)
            if git_version_match:
                region.git_version = git_version_match.group(1)
                

def process_client_latency_file(region, latency_file):
    # latency_file is container_data/<service>/<client_impulse>/app_metrics_data/processing_latency.csv
    service_dir = latency_file.parent.parent.parent
    if not service_dir.exists():
        get_logger().warn("Cannot determine service for %s", latency_file)
        
    service = service_dir.name
    with open(latency_file) as f:
        reader = csv.DictReader(f)
        for row in reader:
            server = row['server']
            latency = float(row['latency'])
            region.add_client_service_contact(service, server, latency)
        

def process_client_latency(region, client_dir):
    for latency_file in client_dir.glob("**/processing_latency.csv"):
        process_client_latency_file(region, latency_file)


def process_server_latency_file(region, latency_file):
    # latency_file is container_data/<service directory>/<container name>/<container start timestamp>/app_metrics_data/processing_latency.csv
    service_dir = latency_file.parent.parent.parent.parent
    get_logger().debug("Found service '{}'".format(service_dir.name))
    service_match = re.match(r'^\S+\.(\S+)_\S+$', service_dir.name)
    if service_match:
        service = service_match.group(1)
        with open(latency_file) as f:
            reader = csv.DictReader(f)
            for row in reader:
                client = row['client']
                latency = float(row['latency'])
                region.add_server_latency(service, client, latency)
    else:
        get_logger().warn("Could not find service directory for server latency file %s", latency_file)

        
def process_server_latency(region, agent_dir):
    for latency_file in agent_dir.glob("**/processing_latency.csv"):
        process_server_latency_file(region, latency_file)
    
            
def process_logs(basedir, nodes, regions):
    for nodeName, node in nodes.items():
        node_dir = basedir / nodeName
        if node.client:
            client_dir = node_dir / "client"
            get_logger().debug("Client directory %s", client_dir)

            process_client_latency(node.region, client_dir)

            for logfile in client_dir.glob("map-client*.log"):
                get_logger().debug("Processing client logfile %s", logfile)
                process_client_logfile(node.region, logfile)
        else:
            agent_dir = node_dir / "agent"

            process_server_latency(node.region, agent_dir)
            
            for logfile in agent_dir.glob("map-agent*.log"):
                get_logger().debug("Processing agent logfile %s", logfile)
                process_agent_logfile(node.region, logfile)


def parse_node_info(basedir):
    '''
    @return (name -> Node, name -> Region, ip -> Node)
    '''
    regions = dict()
    nodes = dict()
    ip_to_node = dict()
    
    scenario_dir = basedir / 'inputs/scenario'
    
    if not scenario_dir.exists():
        raise RuntimeError('Cannot find scenario directory ' + scenario_dir)

    topology_file = scenario_dir / 'topology.ns'
    with open(topology_file) as f:
        for line in f:
            node_match = re.match(r'^set\s+(\S+)\s+\[\S+\s+node\]', line)
            if node_match:
                node_name = node_match.group(1)
                node = Node(node_name)
                
                node_filename = scenario_dir / '{}.json'.format(node_name)
                if node_filename.exists():
                    with open(node_filename) as jf:
                        data = json.load(jf)
                        node.read_extra_data(regions, data)

                nodes[node_name] = node

            ip_match = re.match(r'^(?:tb-set-ip-lan|tb-set-ip-link)\s+$(?P<node>\S+)\s+\S+\s+(?P<ip>\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$', line)
            if ip_match:
                node_name = ip_match.group('node')
                ip = ip_match.group('ip')
                node = nodes[node_name]
                node.ips.add(ip)
                ip_to_node[ip] = node
                
    return regions, nodes, ip_to_node


def gather_region_service_information(basedir, regions):
    for filename in basedir.glob("**/regionNodeState.json"):
        get_logger().debug("Reading %s", filename)
        try:
            with open(filename) as f:
                regionNodeState = json.load(f)
        except json.decoder.JSONDecodeError:
            get_logger().warn("Error parsing %s, skipping", filename)
            continue
        
        regionName = regionNodeState['region']['name']
        if regionName not in regions:
            raise RuntimeError(f"Found a regionNodeState file '{filename}' referencing region '{regionName}' and it's not in the list of known regions")
        region = regions[regionName]
        resourceReports = regionNodeState['nodeResourceReports']
        for resourceReport in resourceReports:
            containerReports = resourceReport['containerReports']
            for container, creport in containerReports.items():
                serviceTuple = creport['service']
                region.services.add(serviceTuple['artifact'])

                
def parse_region_subnets(basedir, regions):
    filename = basedir / 'inputs/region_subnet.txt'
    if filename.exists():
        with open(filename) as f:
            for line in f:
                tokens = line.split()
                region_name = tokens[0]
                region = regions[region_name]
                subnet_str = tokens[1]
                subnet = ipaddress.ip_network(subnet_str)
                region.subnets.add(subnet)

def region_for_ip(regions, ip_str):
    """
    @param regions name -> Region
    @param ip_str ip address as a string
    @return the region for the IP or None
    """
    ip = ipaddress.ip_address(ip_str)
    for region_name, region in regions.items():
        for subnet in region.subnets:
            if ip in subnet:
                return region
    return None


def get_git_version(regions):
    """
    Arguments:
        regions (dict): region_name, Region object

    Returns:
        str: git version
    """
    for _, region in regions.items():
        if region.git_version is not None:
            return region.git_version
        
    # Nothing found
    return None


def get_experiment_runtime(regions):
    """
    Arguments:
        regions (dict): region_name, Region object

    Returns:
        datetime: min_time or None if no region has a min time
        datetime: max_time or None if no region has a max time
    """
    min_time = None
    max_time = None
    for _, region in regions.items():
        if region.min_time is not None:
            if min_time is None:
               min_time = region.min_time
            elif region.min_time < min_time:
                min_time = region.min_time

        if region.max_time is not None:
            if max_time is None:
               max_time = region.max_time
            elif region.max_time > max_time:
                max_time = region.max_time
                
    return min_time, max_time


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-d", "--dir", dest="dir", help="Results directory from hifi", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    basedir = Path(args.dir)
    
    (regions, nodes, ip_to_node) = parse_node_info(basedir)
    parse_region_subnets(basedir, regions)

    #get_logger().info("%s", str(regions))
    #get_logger().info(nodes)

    process_logs(basedir, nodes, regions)

    gather_region_service_information(basedir, regions)

    for _, region in regions.items():
        get_logger().info("Region: %s", region.name)
        get_logger().info("\tunknown hosts: %s", region.unknown_host_count())
        for hostname, count in region.unknown_host.items():
            get_logger().info("\t\t%s: %d", hostname, count)
        get_logger().info("\tOut of memory: %s", region.oom_count)
        get_logger().info("\tDCOP plans: %s", region.dcop_plan_count)
        get_logger().info("\tRLG plans: %s", region.rlg_plan_count)
        get_logger().info("\tServices run in region: %s", region.services)
        
        if region.servers_contacted:
            get_logger().info("\tServers contacted by clients")
            for service, ips in region.servers_contacted.items():
                ip_regions = list()
                for ip in ips:
                    ip_region = region_for_ip(regions, ip)
                    if ip_region:
                        region_name = ip_region.name
                    else:
                        region_name = 'UNKNOWN'
                    ip_regions.append(f"{ip} ({region_name})")
                servers_string = ", ".join(ip_regions)
                get_logger().info("\t\t%s: %s", service, servers_string)
                
        if region.client_latency_stats:
            get_logger().info("\tAverage client latency")
            for service, stats in region.client_latency_stats.items():
                get_logger().info("\t\t%s: %1.2f ms median: %1.2f ms", service, stats.average(), stats.median())

        if region.clients_contacted:
            get_logger().info("\tClients contacting this region")
            for service, ips in region.clients_contacted.items():
                ip_regions = list()
                for ip in ips:
                    ip_region = region_for_ip(regions, ip)
                    if ip_region:
                        region_name = ip_region.name
                    else:
                        region_name = 'UNKNOWN'
                    ip_regions.append(f"{ip} ({region_name})")
                clients_string = ", ".join(ip_regions)
                get_logger().info("\t\t%s: %s", service, clients_string)
                
        if region.server_latency_stats:
            get_logger().info("\tAverage server latency")
            for service, stats in region.server_latency_stats.items():
                get_logger().info("\t\t%s: %1.2f ms", service, stats.average())

    get_logger().info("Git version: %s", get_git_version(regions))
    
    (experiment_start, experiment_stop) = get_experiment_runtime(regions)
    get_logger().info("Execution start: %s", map_utils.datetime_to_string(experiment_start))
    get_logger().info("Execution stop: %s", map_utils.datetime_to_string(experiment_stop))
    
        
if __name__ == "__main__":
    sys.exit(main())
    
