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

"""
Counts the number of local DNS requests and client requests for each region to determine percentage of DNS lookups to the local region DNS server
(as opposed to local client pool DNS cache).

Outputs a CSV file per region, each with counts and percentage for each service.
Outputs a graph per region, each showing DNS lookup percentage as a line for each service.
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
    import summarize_hifi
    import matplotlib.pyplot as plt
    import ipaddress
    import csv
    import datetime
    import pytz

script_dir=(Path(__file__).parent).resolve()


def get_logger():
    return logging.getLogger(__name__)



class ClientDnsRequestCounts():
    def __init__(self):
        self.client_requests = 0;
        self.dns_requests = 0;

    def get_client_requests(self):
        return self.client_requests

    def get_dns_requests(self):
        return self.dns_requests

    def get_dns_requests_percentage(self):
        if self.client_requests == 0:
            return None
        
        return float(self.dns_requests) / float(self.client_requests)

    def increment_client_requests(self):
        self.client_requests = self.client_requests + 1

    def increment_dns_requests(self):
        self.dns_requests = self.dns_requests + 1


def timestamp_to_window(first_window, window_duration, timestamp):
    """
    Arguments:
        first_window(timedelta): time of the first window
        window_duration(timedelta): duration of each window
        timestamp(datetime): time stamp for which to determine a window
    Returns:
        int: time of the window for timestamp
    """

    return round((timestamp - first_window) / window_duration) * window_duration



def read_all_requests_file(all_requests_path, first_timestamp, window_duration, node_to_region_dict, requests_region_service_counts):
    """
    Arguments:
        all_requests_path(Path): location of all_client_requests.csv
        first_timestamp(datetime): timestamp for the beginning of the run
        window_duration(timedelta): duration of each window
        node_to_region_dict(dict): dictionary from node name to Region
        requests_region_service_counts(dict): region --> service --> window --> ClientDnsRequestCounts
    """

    get_logger().info(f"Reading client requests file: {all_requests_path}")

    with open(all_requests_path) as f:
        reader = csv.DictReader(f) 
        for row in reader:
            ts_str = row['time_sent'] 
            timestamp = datetime.datetime.fromtimestamp(int(ts_str) / 1000, tz=pytz.UTC)
            window = timestamp_to_window(datetime.timedelta(minutes=0), window_duration, timestamp - first_timestamp) + first_timestamp

            service = row['service']
            
            source_client_pool = row['client']
            client_region = node_to_region_dict[source_client_pool]

            get_logger().debug(f"Found client request at time {timestamp} for window {window} with service {service} from client {source_client_pool} in region {client_region}")
            
            client_dns_request_counts = requests_region_service_counts \
                .setdefault(client_region, dict()) \
                .setdefault(service, dict()) \
                .setdefault(window, ClientDnsRequestCounts())
            
            client_dns_request_counts.increment_client_requests()


def read_weighted_dns_files(sim_output, first_timestamp, window_duration, ip_to_node_dict, requests_region_service_counts):
    """
    Arguments:
        sim_output(Path): location of run output
        first_timestamp(datetime): timestamp for the beginning of the run
        window_duration(timedelta): duration of each window
        ip_to_node_dict(dict): dictionary from IP to Node
        requests_region_service_counts(dict): region --> service --> window --> ClientDnsRequestCounts
    """

    for node_dir in sim_output.iterdir():
        weighted_dns_file = node_dir / 'dns' / 'weighted-dns.csv'

        if weighted_dns_file.exists():
                get_logger().info(f"Reading DNS requests file: {weighted_dns_file}")
            
                with open(weighted_dns_file) as f:
                    reader = csv.DictReader(f)

                    for row in reader:
                        ts_str = row['timestamp']
                        timestamp = datetime.datetime.fromtimestamp(int(ts_str) / 1000, tz=pytz.UTC)
                        window = timestamp_to_window(datetime.timedelta(minutes=0), window_duration, timestamp - first_timestamp) + first_timestamp

                        client_ip = row['clientAddress']

                        node = ip_to_node_dict[client_ip]
                        source_client_pool = node.name
                        client_region = node.region.name

                        service_domain = row['name_to_resolve']

                        if (map_utils.is_general_service_domain_name(service_domain)):
                            service = map_utils.get_service_artifact_from_domain_name(service_domain)
                        
                            get_logger().debug(f"Found DNS request at time {timestamp} for window {window} with service {service} from client {source_client_pool} in region {client_region}")
                
                            client_dns_request_counts = requests_region_service_counts \
                                .setdefault(client_region, dict()) \
                                .setdefault(service, dict()) \
                                .setdefault(window, ClientDnsRequestCounts())

                            client_dns_request_counts.increment_dns_requests()


def output_requests_csv_per_region(requests_region_service_counts, first_timestamp, output):
    """
    Arguments:
        requests_region_service_counts(dict): region --> service --> window --> ClientDnsRequestCounts
        first_timestamp(datetime): timestamp for the beginning of the run
        output(Path): output folder for csv files
    """

    windows = set()

    for region, region_data in requests_region_service_counts.items():
        for service, service_data in region_data.items():
            for window, window_data in service_data.items():
                windows.add(window)

    window_list = list(windows)
    window_list.sort()
    

    for region, region_data in requests_region_service_counts.items():
        services = list(region_data.keys())
        services.sort()
        get_logger().debug(f"services = {services}")

        csvfile = output / ('client_dns_lookup_percent-' + region + '.csv')

        header_list = list()
        header_list.append("minutes")
        header_list.append("time")
        for service in services:
            header_list.append(service + "-client_requests")
            header_list.append(service + "-dns_requests")
            header_list.append(service + "-dns_query_percent")

        with open(csvfile, 'w', newline='') as csvfile:
            get_logger().info(f"Outputting CSV file: {csvfile.name}")
            writer = csv.writer(csvfile)
            writer.writerow(header_list)

            for window in window_list:
                window_timestamp = window.timestamp() * 1000
                window_minutes = (window - first_timestamp) / datetime.timedelta(minutes=1)

                record = list()
                record.append(window_minutes)
                record.append(window_timestamp)
                
                for service in services:
                    service_data = region_data[service]
                    window_data = service_data.get(window)

                    if window_data:
                        record.append(window_data.get_client_requests())
                        record.append(window_data.get_dns_requests())

                        percent = window_data.get_dns_requests_percentage()
                        if percent == None:
                            percent = "?"
                            
                        record.append(percent)
                    else:
                        record.append("?")
                        record.append("?")
                        record.append("?")

                writer.writerow(record)
                    

def output_requests_graph_per_region(requests_region_service_counts, first_timestamp, output):
    """
    Arguments:
        requests_region_service_counts(dict): region --> service --> window --> ClientDnsRequestCounts
        first_timestamp(datetime): timestamp for the beginning of the run
        output(Path): output folder for csv files
    """

    for region, region_data in requests_region_service_counts.items():
        services = list(region_data.keys())
        services.sort()
        get_logger().debug(f"services = {services}")

        graph_file = output / ('client_dns_lookup_percent-' + region + '.png')
        get_logger().info(f"Outputting graph file: {graph_file.name}")
        
        fig, ax = map_utils.subplots()
        ax.set_title(f"DNS cache percentages for services in region {region}")
        ax.set_xlabel("Time (minutes)")
        ax.set_ylim([0,100])
        

        for service, service_data in region_data.items():
            times = list()
            cache_percentages = list()

            windows = set()
            for window, window_data in service_data.items():
                windows.add(window)

            windows_list = list(windows)
            windows_list.sort()

            for window in windows_list:
                #get_logger().info(f"window_data[window] = {window_data[window]}")
                time = (window - first_timestamp) / datetime.timedelta(minutes=1)
                cache_percentage = (1 - service_data[window].get_dns_requests_percentage()) * 100
                
                times.append(time)
                cache_percentages.append(cache_percentage)

            series_label=f"{service}"
            ax.plot(times, cache_percentages, label=series_label)

        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
        fig.savefig(graph_file.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
        plt.close(fig)


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
    parser.add_argument("--input", dest="input", help="Path to all_client_requests.csv", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory", required=True)
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)
    parser.add_argument("--window-duration", dest="window_duration", help="Number of minutes to use for the window to compute percentages over", default=1, type=float)

    args = parser.parse_args(argv)
    map_utils.setup_logging(default_path=args.logconfig)



    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)

    get_logger().info("Simulation started at %s", first_timestamp)
    
    all_requests_path = Path(args.input)
    if not all_requests_path.exists():
        get_logger().error("Cannot read all requests file at %s", all_requests_path)
        return -1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    sim_output = Path(args.sim_output)
    regions = dict()

    window_duration = datetime.timedelta(minutes=args.window_duration)

    scenario_dir = sim_output / 'inputs' / 'scenario'
    node_to_region_dict = map_utils.gather_region_info(scenario_dir)
    _, ip_to_node_dict = summarize_hifi.parse_node_info(sim_output)

    get_logger().debug(f"node_to_region_dict = {node_to_region_dict}")
    get_logger().debug(f"ip_to_node_dict = {ip_to_node_dict}")


    # region --> service --> window --> ClientDnsRequestCounts
    requests_region_service_counts = dict()

    read_all_requests_file(all_requests_path, first_timestamp, window_duration, node_to_region_dict, requests_region_service_counts)
    read_weighted_dns_files(sim_output, first_timestamp, window_duration, ip_to_node_dict, requests_region_service_counts)

    output_requests_csv_per_region(requests_region_service_counts, first_timestamp, output)
    output_requests_graph_per_region(requests_region_service_counts, first_timestamp, output)


if __name__ == "__main__":
    sys.exit(main())
