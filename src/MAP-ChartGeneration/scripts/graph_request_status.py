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

"""
Generate graphs about request status from client
request_status.csv files and server processing_latency.csv files.

Generates
  - client_request_status-{client}.png - per client graphs
  - client_request_status.png - graph of data summed across clients

"""

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
    import map_utils
    import multiprocessing
    import csv
    import latency_analysis
    
    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    
    import matplotlib.pyplot as plt
    

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

    
class ServiceCounts(Base):
    def __init__(self, success_count, failure_count):
        self.success_count = success_count
        self.failure_count = failure_count

        
    def clone(self):
        copy = ServiceCounts(self.success_count, self.failure_count)
        return copy
    
        
    def add(self, other):
        self.success_count = self.success_count + other.success_count
        self.failure_count = self.failure_count + other.failure_count

        
def compute_service_success_sums(service_data):
    """
    Arguments:
        client_data: timestamp -> success

    Returns:
        dict: timestamp -> ServiceCounts
    """

    running_counts = ServiceCounts(0, 0)
    counts = dict()
    for time, svc_count in sorted(service_data.items()):
        running_counts.add(svc_count)
        counts[time] = running_counts.clone()

    return counts
    

def compute_success_sums(client_data):
    """
    Arguments:
        client_data: service -> timestamp -> success

    Returns:
        dict: service -> timestamp -> ServiceCounts
    """
    
    counts = dict()
    for service, service_data in client_data.items():
        service_counts = compute_service_success_sums(service_data)
        counts[service] = service_counts

    return counts


def process_server_node(node_dir, container_data_dir):
    """
    Arguments:
        node_dir (Path): directory for the node
        container_data_dir (Path): container data directory

    Returns:
        str: node name
        dict: sums over time (service -> timestamp -> ServiceCounts)
        dict: values to increment at time, can be used to compute
              global sums (service -> timestamp -> ServiceCounts)
        int: min timestamp
    """

    try:
        node_name = map_utils.node_name_from_dir(node_dir)

        # service -> timestamp -> ServiceCounts
        counts = dict()
        min_timestamp = None
        for service_dir in container_data_dir.iterdir():
            for container_dir in service_dir.iterdir():
                for time_dir in container_dir.iterdir():
                    service = latency_analysis.get_container_service(time_dir, service_dir.name)
                    status_file = time_dir / 'app_metrics_data/processing_latency.csv'
                    if status_file.exists():
                        with open(status_file) as f:
                            reader = csv.DictReader(map_utils.skip_null_lines(f))
                            for row in reader:
                                if 'timestamp' not in row or 'event' not in row:
                                    get_logger().debug("Misformed file %s, could not find timestamp or event", status_file)
                                    continue

                                time = int(row['timestamp'])
                                if min_timestamp is None or time < min_timestamp:
                                    min_timestamp = time

                                service_data = counts.get(service, dict())
                                count = service_data.get(time, ServiceCounts(0, 0))
                                if re.search('failure', row['event'].lower()):
                                    count.failure_count = count.failure_count + 1
                                else:
                                    count.success_count = count.success_count + 1
                                service_data[time] = count
                                counts[service] = service_data

        sums = compute_success_sums(counts)

        return node_name, sums, counts, min_timestamp
    except:
        get_logger().exception("Unexpected error")


def process_client_node(node_dir, container_data_dir):
    """
    Arguments:
        node_dir (Path): directory for the node
        container_data_dir (Path): container data directory

    Returns:
        str: client
        dict: sums over time (service -> timestamp -> ServiceCounts)
        dict: values to increment at time, can be used to compute
              global sums (service -> timestamp -> ServiceCounts)
        int: min timestamp
    """

    try:
        client = map_utils.node_name_from_dir(node_dir)

        # service -> timestamp -> ServiceCounts
        client_data = dict()
        min_timestamp = None
        for service_dir in container_data_dir.iterdir():
            service = service_dir.name

            for time_dir in service_dir.iterdir():
                status_file = time_dir / 'app_metrics_data/request_status.csv'
                if status_file.exists():
                    with open(status_file) as f:
                        reader = csv.DictReader(map_utils.skip_null_lines(f))
                        for row in reader:
                            if 'timestamp' not in row or 'success' not in row:
                                get_logger().debug("Misformed file %s, could not find timestamp or success", status_file)
                                continue

                            time = int(row['timestamp'])
                            if min_timestamp is None or time < min_timestamp:
                                min_timestamp = time


                            service_data = client_data.get(service, dict())
                            count = service_data.get(time, ServiceCounts(0, 0))
                            success = row['success'].lower() == 'true'
                            if success:
                                count.success_count = count.success_count + 1
                            else:
                                count.failure_count = count.failure_count + 1
                            service_data[time] = count
                            client_data[service] = service_data

        client_counts = compute_success_sums(client_data)

        return client, client_counts, client_data, min_timestamp
    except:
        get_logger().exception("Unexpected error")


def output_node_graph(output, node_name, data, label):
    """
    Arguments:
        output (Path): base directory to write to
        node_name (str): node name, None to state this is all data across nodes
        data (dict): service -> relative minutes -> ServiceCounts
        label (str): Client or Server
    """

    fig, ax = plt.subplots()
    if node_name is not None:
        ax.set_title(f"{label} Request Status for {node_name}")
    else:
        ax.set_title(f"{label} Request Status")

    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Cumulative Count")
    ax.grid(alpha=0.5, axis='y')
    for service, service_data in data.items():
        pairs = sorted(service_data.items())
        times, values = zip(*pairs)
        success_counts = [c.success_count for c in values]
        failure_counts = [c.failure_count for c in values]

        ax.plot(times, success_counts, label=f"{service} success")
        ax.plot(times, failure_counts, label=f"{service} failure")

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    if node_name is not None:
        output_name = output / f"{label.lower()}_request_status-{node_name}.png"
    else:
        output_name = output / f"{label.lower()}_request_status.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


def output_per_node_graphs(output, data, label):
    """
    Arguments:
        output (Path): base directory to write to
        data (dict): node_name -> service -> relative minutes -> ServiceCounts
        label (str): Client or Server
    """

    for node_name, node_data in data.items():
        output_node_graph(output, node_name, node_data, label)


def gather_data(sim_output):
    """
    Arguments:
        sim_output (Path): where the simulation output is

    Returns:
        dict: sums over time per client (client -> service -> timestamp -> ServiceCounts)
        dict: raw data per client (client -> service -> timestamp -> ServiceCounts)
        dict: sums over time per server (server -> service -> timestamp -> ServiceCounts)
        dict: raw data per server (server -> service -> timestamp -> ServiceCounts)
        int: min_timestamp
    """

    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        client_results = list()
        server_results = list()

        for node_dir in sim_output.iterdir():
            if not node_dir.is_dir():
                continue

            client_container_data_dir = node_dir / 'client/container_data'
            server_container_data_dir = node_dir / 'agent/container_data'
            if client_container_data_dir.is_dir():
                client_results.append(pool.apply_async(func=process_client_node, args=[node_dir, client_container_data_dir]))
            elif server_container_data_dir.is_dir():
                server_results.append(pool.apply_async(func=process_server_node, args=[node_dir, server_container_data_dir]))

        client_data_sums = dict()
        client_data_counts = dict()
        min_timestamp = None
        for result in client_results:
            (client, client_sums, client_data, client_min_timestamp) = result.get()
            if client_min_timestamp is not None:
                if min_timestamp is None or client_min_timestamp < min_timestamp:
                    min_timestamp = client_min_timestamp
            client_data_sums[client] = client_sums
            client_data_counts[client] = client_data

        server_data_sums = dict()
        server_data_counts = dict()
        for result in server_results:
            (node_name, sums, counts, node_min_timestamp) = result.get()
            if node_min_timestamp is not None:
                if min_timestamp is None or node_min_timestamp < min_timestamp:
                    min_timestamp = node_min_timestamp
            server_data_sums[node_name] = sums
            server_data_counts[node_name] = counts
            
    return client_data_sums, client_data_counts, server_data_sums, server_data_counts, min_timestamp


def compute_per_service(data_counts):
    """
    Arguments:
        data_counts (dict): increment at time per client (client -> service -> timestamp -> ServiceCounts)
    Returns:
        dict: running total per service (service -> timestamp -> ServiceCounts)
    """
    service_data_counts = dict()

    for client, client_data in data_counts.items():
        for service, service_data in client_data.items():
            service_counts = service_data_counts.get(service, dict())
            
            for timestamp, counts in sorted(service_data.items()):
                service_time_counts = service_counts.get(timestamp, ServiceCounts(0, 0))
                service_time_counts.add(counts)
                service_counts[timestamp] = service_time_counts
            service_data_counts[service] = service_counts

    service_data_sums = dict()
    for service, service_counts in service_data_counts.items():
        svc_data_sum = dict()
        
        running_service_sum = ServiceCounts(0, 0)
        for timestamp, count in sorted(service_counts.items()):
            running_service_sum.add(count)
            svc_data_sum[timestamp] = running_service_sum.clone()
            
        service_data_sums[service] = svc_data_sum
                
    return service_data_sums


def relative_timestamps_service(data, min_timestamp):
    """
    Arguments:
        data (dict): service -> timestamp -> ServiceCounts

    Returns:
        dict: service -> relative minutes -> ServiceCounts
    """
    relative_data = dict()
    for service, service_data in data.items():
        relative_service_data = dict()

        for timestamp, counts in sorted(service_data.items()):
            minutes = map_utils.timestamp_to_minutes(timestamp - min_timestamp)
            relative_service_data[minutes] = counts

        relative_data[service] = relative_service_data
    return relative_data


def relative_timestamps_client(data, min_timestamp):
    """
    Arguments:
        data (dict): client -> service -> timestamp -> ServiceCounts

    Returns:
        dict: client -> service -> relative minutes -> ServiceCounts
    """
    relative_data = dict()
    for client, client_data in data.items():
        relative_data[client] = relative_timestamps_service(client_data, min_timestamp)
    return relative_data
    

def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    # client -> service -> timestamp -> ServiceCounts
    (client_data_sums, client_data_counts, server_data_sums, server_data_counts, min_timestamp) = gather_data(sim_output)

    # client graphs
    client_data_sums_relative = relative_timestamps_client(client_data_sums, min_timestamp)
    output_per_node_graphs(output, client_data_sums_relative, 'Client')

    # client service graphs
    client_service_data_sums = compute_per_service(client_data_counts)
    client_service_data_sums_relative = relative_timestamps_service(client_service_data_sums, min_timestamp)
    output_node_graph(output, None, client_service_data_sums_relative, 'Client')

    # server graphs
    server_data_sums_relative = relative_timestamps_client(server_data_sums, min_timestamp)
    output_per_node_graphs(output, server_data_sums_relative, 'Server')

    # server service graphs
    server_service_data_sums = compute_per_service(server_data_counts)
    server_service_data_sums_relative = relative_timestamps_service(server_service_data_sums, min_timestamp)
    output_node_graph(output, None, server_service_data_sums_relative, 'Server')

    
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
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

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
