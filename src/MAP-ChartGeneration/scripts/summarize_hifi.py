#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
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
    import multiprocessing

script_dir=os.path.abspath(os.path.dirname(__file__))

exception_re = re.compile(r'\b(\w[\w._]+Exception):')

def get_logger():
    return logging.getLogger(__name__)

    
class LazyStats(map_utils.Base):
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

    def merge(self, other_stats):
        """
        Merge the values in other_stats into this object.
        """
        if self.values is None and other_stats.values is not None:
            raise RuntimeError("Attempting to merge stats that have different values for track_median. Self is none, other is not none.")
        elif self.values is not None and other_stats.values is None:
            raise RuntimeError("Attempting to merge stats that have different values for track_median. Self is not none and other stats is none.")
        
        self.sum = self.sum + other_stats.sum
        self.count = self.count + other_stats.count
        if self.values is not None:
            self.values.extend(other_stats.values)

    
class Node(map_utils.Base):
    def __init__(self, name):
        self.name = name
        self.region = None
        self.dcop = False
        self.dns = False
        self.rlg = False
        self.client = False
        self.ips = set()


    def read_extra_data(self, json_data):
        if 'region' in json_data:
            self.regionName = json_data['region']

        if 'client' in json_data:
            self.client = json_data['client']

        self.dcop = json_data.get('DCOP', False)
        self.rlg = json_data.get('RLG', False)
        self.dns = json_data.get('dns', False)

        
class Region(map_utils.Base):
    
    def __init__(self, name):
        self.name = name
        self.unknown_host = dict()
        self.dcop_plan_count = 0
        self.rlg_plan_count = 0
        self.rlg_plan_invalid_count = 0
        self.oom_count = 0
        self.servers_contacted = dict()
        self.subnets = set()
        self.client_latency_stats = dict()
        self.clients_contacted = dict()
        self.server_latency_stats = dict()
        self.git_version = None
        self.min_time = None
        self.max_time = None
        # remote server -> count
        self.dns_delegate_errors = dict()
        self.dns_socket_timeouts = 0
        self.requests = 0
        self.requests_failed = 0
        self.requests_attempt_count = dict()
        # exception -> count 
        self.agent_container_exceptions = dict() 
        self.client_exceptions = dict() 
        self.agent_exceptions = dict() 


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


    def merge(self, other_region):
        """
        Merge the values of other_region into this object.
        """
        if self.name != other_region.name:
            raise RuntimeError(f"Trying to merge 2 regions with different names {self.name} != {other_region.name}")

        for k, v in other_region.unknown_host.items():
            self.unknown_host[k] = self.unknown_host.get(k, 0) + v
            
        self.dcop_plan_count = self.dcop_plan_count + other_region.dcop_plan_count
        self.rlg_plan_count = self.rlg_plan_count + other_region.rlg_plan_count
        self.rlg_plan_invalid_count = self.rlg_plan_invalid_count + other_region.rlg_plan_invalid_count
        self.oom_count = self.oom_count + other_region.oom_count

        for k, v in other_region.servers_contacted.items():
            new_value = self.servers_contacted.get(k, set())
            new_value.update(v)
            self.servers_contacted[k] = new_value
            
        self.subnets.update(other_region.subnets)

        for k, v in other_region.client_latency_stats.items():
            if k in self.client_latency_stats:
                self.client_latency_stats[k].merge(v)
            else:
                self.client_latency_stats[k] = v

        for k, v in other_region.clients_contacted.items():
            new_value = self.clients_contacted.get(k, set())
            new_value.update(v)
            self.clients_contacted[k] = new_value

        for k, v in other_region.server_latency_stats.items():
            if k in self.server_latency_stats:
                self.server_latency_stats[k].merge(v)
            else:
                self.server_latency_stats[k] = v
        
        if self.git_version is None:
            self.git_version = other_region.git_version

        if self.min_time is None:
            self.min_time = other_region.min_time
        elif other_region.min_time is not None:
            self.min_time = min(self.min_time, other_region.min_time)
            
        if self.max_time is None:
            self.max_time = other_region.max_time
        elif other_region.max_time is not None:
            self.max_time = max(self.max_time, other_region.max_time)

        # remote server -> count
        for k, v in other_region.dns_delegate_errors.items():
            self.dns_delegate_errors[k] = self.dns_delegate_errors.get(k, 0) + v
        
        self.dns_socket_timeouts = self.dns_socket_timeouts + other_region.dns_socket_timeouts

        self.requests = self.requests + other_region.requests
        self.requests_failed = self.requests_failed + other_region.requests_failed
        for k, v in other_region.requests_attempt_count.items():
            self.requests_attempt_count[k] = self.requests_attempt_count.get(k, 0) + v
        
        for k, v in other_region.agent_container_exceptions.items():
            self.agent_container_exceptions[k] = self.agent_container_exceptions.get(k, 0) + v
        for k, v in other_region.client_exceptions.items():
            self.client_exceptions[k] = self.client_exceptions.get(k, 0) + v
        for k, v in other_region.agent_exceptions.items():
            self.agent_exceptions[k] = self.agent_exceptions.get(k, 0) + v

            
def common_logfile_matches(label, region, line):
    unknown_host_match = re.search(r'UnknownHostException: (Unknown host: )?<?(?P<host>\S+)', line)
    if unknown_host_match:
        host = unknown_host_match.group('host')
        if host.endswith(">"):
            host = host[:-1]
        region.add_unknown_host(f"{label}: {host}")

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

    with open(logfile, errors='replace') as f:
        for line in f:
            common_logfile_matches('client', region, line)

            start_request_match = re.search(r'Starting request', line)
            if start_request_match:
                region.requests = region.requests + 1

            failed_request_match = re.search(r'All attempts to connect have failed', line)
            if failed_request_match:
                region.requests_failed = region.requests_failed + 1

            attempt_request_match = re.search(r'Connection attempt (\d+)', line)
            if attempt_request_match:
                attempt_number = int(attempt_request_match.group(1))
                region.requests_attempt_count[attempt_number] = region.requests_attempt_count.get(attempt_number, 0) + 1

            match = exception_re.search(line)
            if match:
                exception = match.group(1)
                region.client_exceptions[exception] = region.client_exceptions.get(exception, 0) + 1
                
            

def process_agent_logfile(region, logfile):
    '''
    @param region the Region object for the agent
    @param logfile the file to read
    '''
    rlg_plan_re = re.compile(r'Publishing RLG plan:')
    dcop_plan_re = re.compile(r'Publishing DCOP plan:')
    git_version_re = re.compile(r'\.HiFiAgent.*Git version: (\S+)$')
    rlg_plan_invalid_re = re.compile(r'Invalid RLG plan:')
    
    with open(logfile, errors='replace') as f:
        for line in f:
            common_logfile_matches('agent', region, line)
            
            rlg_plan_match = rlg_plan_re.search(line)
            if rlg_plan_match:
                region.rlg_plan_count = region.rlg_plan_count + 1

            rlg_plan_invalid_match = rlg_plan_invalid_re.search(line)
            if rlg_plan_invalid_match:
                region.rlg_plan_invalid_count = region.rlg_plan_invalid_count + 1
                
            dcop_plan_match = dcop_plan_re.search(line)
            if dcop_plan_match:
                region.dcop_plan_count = region.dcop_plan_count + 1

            git_version_match = git_version_re.search(line)
            if git_version_match:
                region.git_version = git_version_match.group(1)

            match = exception_re.search(line)
            if match:
                exception = match.group(1)
                region.agent_exceptions[exception] = region.agent_exceptions.get(exception, 0) + 1
                
                

def process_client_latency_file(region, latency_file):
    # latency_file is container_data/<service>/<client_impulse>/app_metrics_data/processing_latency.csv
    # dependent service latency_file is container_data/<frontend service>/<container name>/<timestamp>/app_metrics_data/dependent-services/<backend service>/<timestamp>/processing_latency.csv
    if re.search(r'app_metrics_data/dependent-services', latency_file.as_posix()):
        service_dir = latency_file.parent.parent
    else:
        service_dir = latency_file.parent.parent.parent
        
    if not service_dir.exists():
        get_logger().warn("Cannot determine service for client latency file: %s", latency_file)
        
    service = service_dir.name
    with open(latency_file, errors='replace') as f:
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
    if re.search(r'app_metrics_data/dependent-services', latency_file.as_posix()):
        # this is a client latency file for a dependent service
        # server is acting as a client
        process_client_latency_file(region, latency_file)
        return
    
    service_dir = latency_file.parent.parent.parent.parent
        
    get_logger().debug("Found service '{}'".format(service_dir.name))
    service_match = re.match(r'^\S+\.(\S+)_\S+$', service_dir.name)
    if service_match:
        service = service_match.group(1)
        try:
            with open(latency_file, errors='replace') as f:
                reader = csv.DictReader(f)
                for row in reader:
                    client = row['client']
                    latency = float(row['latency'])
                    region.add_server_latency(service, client, latency)
        except Exception:
            get_logger().exception("Error parsing %s, skipping", latency_file)
    else:
        get_logger().warn("Could not find service directory for server latency file %s", latency_file)

        
def process_server_latency(region, agent_dir):
    for latency_file in agent_dir.glob("**/processing_latency.csv"):
        process_server_latency_file(region, latency_file)


def process_dns_logfile(region, logfile):
    '''
    @param region the Region object for the agent
    @param logfile the file to read
    '''

    delegate_error_re = re.compile(r'(Error querying server|Error resolving nameserver address) \'(?P<delegate>[^\.]+)(.map[^.]*.dcomp)?\'')
    socket_timeout_re = re.compile(r'java\.net\.SocketTimeoutException')
    
    with open(logfile, errors='replace') as f:
        for line in f:
            delegate_error_match = delegate_error_re.search(line)
            socket_timeout_match = socket_timeout_re.search(line)
            
            if delegate_error_match:
                delegate = delegate_error_match.group("delegate")
                region.dns_delegate_errors[delegate] = region.dns_delegate_errors.get(delegate, 0) + 1
            if socket_timeout_match:
                region.dns_socket_timeouts = region.dns_socket_timeouts + 1

def process_agent_container_logfile(node_region, logfile):
    with open(logfile, errors='replace') as f:
        for line in f:
            match = exception_re.search(line)
            if match:
                exception = match.group(1)
                node_region.agent_container_exceptions[exception] = node_region.agent_container_exceptions.get(exception, 0) + 1


def process_node_dir(node_dir, node):
    node_region = Region(node.regionName)
    try:

        if node.client:
            client_dir = node_dir / "client"
            get_logger().debug("Client directory %s", client_dir)

            process_client_latency(node_region, client_dir)

            for logfile in client_dir.glob("map-client*.log"):
                get_logger().debug("Processing client logfile %s", logfile)
                process_client_logfile(node_region, logfile)
        else:
            agent_dir = node_dir / "agent"

            process_server_latency(node_region, agent_dir)

            for logfile in agent_dir.glob("map-agent*.log"):
                get_logger().debug("Processing agent logfile %s", logfile)
                process_agent_logfile(node_region, logfile)

            for logfile in agent_dir.glob("container_data/*/*/*/logs.txt"):
                process_agent_container_logfile(node_region, logfile)

            dns_dir = node_dir / "dns"

            if dns_dir.exists():
                for logfile in dns_dir.glob("map-dns*.log"):
                    get_logger().debug("Processing dns logfile %s", logfile)
                    process_dns_logfile(node_region, logfile)

    except:
        get_logger().exception("Unexpected error")

    return node_region


def process_logs(basedir, nodes):
    all_regions = dict()

    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()

        for nodeName, node in nodes.items():
            node_dir = basedir / nodeName
            if not node_dir.exists():
                # Emulab seems to always make the directory names lowercase, not sure why.
                node_dir = basedir / nodeName.lower()

            results.append(pool.apply_async(func=process_node_dir, args=[node_dir, node]))

        for result in results:
            node_region = result.get()
            all_region = all_regions.get(node_region.name, Region(node_region.name))
            
            all_region.merge(node_region)
            all_regions[all_region.name] = all_region
        
    return all_regions


def parse_node_info(basedir):
    '''
    @return (name -> Region, name -> Node, ip -> Node)
    '''
    nodes = dict()
    ip_to_node = dict()
    
    scenario_dir = basedir / 'inputs/scenario'
    
    if not scenario_dir.exists():
        raise RuntimeError('Cannot find scenario directory ' + scenario_dir)

    topology_file = scenario_dir / 'topology.ns'
    with open(topology_file, errors='replace') as f:
        for line in f:
            node_match = re.match(r'^set\s+(\S+)\s+\[\S+\s+node\]', line)
            if node_match:
                node_name = node_match.group(1)
                node = Node(node_name)
                
                node_filename = scenario_dir / '{}.json'.format(node_name)
                if node_filename.exists():
                    with open(node_filename, errors='replace') as jf:
                        data = json.load(jf)
                        node.read_extra_data(data)

                nodes[node_name] = node

            ip_match = re.match(r'^(?:tb-set-ip-lan|tb-set-ip-link)\s+\$(?P<node>\S+)\s+\S+\s+(?P<ip>\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$', line)
            if ip_match:
                node_name = ip_match.group('node')
                ip = ip_match.group('ip')
                node = nodes[node_name]
                node.ips.add(ip)
                ip_to_node[ip] = node
                
    return nodes, ip_to_node


def parse_region_subnets(basedir):
    # name to region
    regions = dict()
    
    filename = basedir / 'inputs/region_subnet.txt'
    if filename.exists():
        with open(filename, errors='replace') as f:
            for line in f:
                tokens = line.split()
                region_name = tokens[0]
                region = regions.get(region_name, Region(region_name))
                subnet_str = tokens[1]
                subnet = ipaddress.ip_network(subnet_str)
                region.subnets.add(subnet)
                regions[region_name] = region

    return regions


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


def main_method(args):
    basedir = Path(args.dir)
    
    (nodes, ip_to_node) = parse_node_info(basedir)
    
    all_regions = parse_region_subnets(basedir)

    #get_logger().info("%s", str(regions))
    #get_logger().info(nodes)

    log_regions = process_logs(basedir, nodes)

    for name, region in log_regions.items():
        all_region = all_regions.get(name, Region(name))
        all_region.merge(region)
        all_regions[all_region.name] = all_region

    for _, region in sorted(all_regions.items()):
        get_logger().info("Region: %s", region.name)
        get_logger().info("\tunknown hosts: %s", region.unknown_host_count())
        for hostname, count in sorted(region.unknown_host.items()):
            get_logger().info("\t\t%s: %d", hostname, count)
        get_logger().info("\tOut of memory: %s", region.oom_count)
        get_logger().info("\tDCOP plans: %s", region.dcop_plan_count)
        get_logger().info("\tRLG plans: %s", region.rlg_plan_count)
        get_logger().info("\tInvalid RLG plans: %s", region.rlg_plan_invalid_count)
        
        if region.servers_contacted:
            get_logger().info("\tServers contacted by clients")
            for service, ips in sorted(region.servers_contacted.items()):
                ip_regions = list()
                for ip in ips:
                    ip_region = region_for_ip(all_regions, ip)
                    if ip_region:
                        region_name = ip_region.name
                    else:
                        region_name = 'UNKNOWN'
                    ip_regions.append(f"{ip} ({region_name})")
                servers_string = ", ".join(ip_regions)
                get_logger().info("\t\t%s: %s", service, servers_string)
                
        if region.client_latency_stats:
            get_logger().info("\tAverage client latency")
            for service, stats in sorted(region.client_latency_stats.items()):
                get_logger().info("\t\t%s: %1.2f ms median: %1.2f ms", service, stats.average(), stats.median())

        if region.clients_contacted:
            get_logger().info("\tClients contacting this region")
            for service, ips in sorted(region.clients_contacted.items()):
                ip_regions = list()
                for ip in ips:
                    ip_region = region_for_ip(all_regions, ip)
                    if ip_region:
                        region_name = ip_region.name
                    else:
                        region_name = 'UNKNOWN'
                    ip_regions.append(f"{ip} ({region_name})")
                clients_string = ", ".join(ip_regions)
                get_logger().info("\t\t%s: %s", service, clients_string)

        if region.requests > 0:
            get_logger().info("\tFake load client request information")
            get_logger().info("\t\tRequests attempted: %d", region.requests)
            get_logger().info("\t\tRequests failed: %d", region.requests_failed)
            for attempt, count in sorted(region.requests_attempt_count.items()):
                get_logger().info("\t\tNumber of %d attempts: %d", attempt, count)

        if len(region.agent_container_exceptions) > 0:
            get_logger().info("\tExceptions in containers")
            for exception, count in sorted(region.agent_container_exceptions.items()):
                get_logger().info("\t\t%s: %d", exception, count)
        if len(region.client_exceptions) > 0:
            get_logger().info("\tExceptions in clients")
            for exception, count in sorted(region.client_exceptions.items()):
                get_logger().info("\t\t%s: %d", exception, count)
        if len(region.agent_exceptions) > 0:
            get_logger().info("\tExceptions in agents")
            for exception, count in sorted(region.agent_exceptions.items()):
                get_logger().info("\t\t%s: %d", exception, count)
            
                
        if region.server_latency_stats:
            get_logger().info("\tAverage server latency")
            for service, stats in sorted(region.server_latency_stats.items()):
                get_logger().info("\t\t%s: %1.2f ms", service, stats.average())

        if region.dns_delegate_errors:
            get_logger().info("\tDNS delegation errors:")
            for delegate, count in sorted(region.dns_delegate_errors.items()):
                get_logger().info("\t\t%s -> %d", delegate, count)

        get_logger().info("\tDNS socket timeouts: %d", region.dns_socket_timeouts)
        
    get_logger().info("Git version: %s", get_git_version(all_regions))
    
    (experiment_start, experiment_stop) = get_experiment_runtime(all_regions)
    get_logger().info("Execution start: %s", map_utils.datetime_to_string(experiment_start))
    get_logger().info("Execution stop: %s", map_utils.datetime_to_string(experiment_stop))
    

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
    parser.add_argument("-d", "--dir", dest="dir", help="Results directory from hifi", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    if 'multiprocessing' in sys.modules:
        import multiprocessing_logging
        multiprocessing_logging.install_mp_handler()

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

            
if __name__ == "__main__":
    sys.exit(main())
    
