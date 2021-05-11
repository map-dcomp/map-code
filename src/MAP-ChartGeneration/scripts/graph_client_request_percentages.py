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
Graph the percentage of client requests that goto each region binned
by the specified time window.

Creates percentage-client-requests-to-region-{service}.png
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


class Region(map_utils.Base):
    def __init__(self, name):
        self.name = name
        self.subnets = set()


class ClientRequest(map_utils.Base):
    def __init__(self, timestamp, dest_region):
        self.timestamp = timestamp
        self.dest_region = dest_region
                        

def compute_percentages(first_timestamp, window_start, window_duration, regions, total_count, region_counts, all_timestamps, all_region_percentages):
    """
    Arguments:
        first_timestamp(datetime.datetime): when the simulation started
        window_start(datetime.datetime): start of this window
        window_duration(datetime.timedelta): size of the window
        regions(dict): region name to Region
        total_count(int): number of requests for the window
        region_counts(dict): Region to count of requests for this window
        all_timestamps(list): all timestamps for the client requests (modified)
        all_region_percentages(dict): Region to list of percentages for the region, matches all_timestamps (modified)
    """
    start_diff = window_start - first_timestamp
    end_diff = start_diff + window_duration
    start_rel_minutes = start_diff / datetime.timedelta(minutes=1)
    end_rel_minutes = end_diff / datetime.timedelta(minutes=1)
    get_logger().info(f"Percentages for window from {start_rel_minutes:.1f} minutes to {end_rel_minutes:.1f} minutes")
    for region_name, region in regions.items():
        if region in region_counts:
            count = region_counts[region]
            percent = count / total_count * 100
            get_logger().info(f"\tRegion {region.name} percent: {percent:.2f}%")
        else:
            percent = 0
        region_percentages = all_region_percentages.get(region, list())
        region_percentages.append(percent)
        all_region_percentages[region] = region_percentages
    all_timestamps.append(start_rel_minutes)


def parse_region_subnets(basedir, regions):
    filename = basedir / 'inputs/region_subnet.txt'
    if filename.exists():
        with open(filename) as f:
            for line in f:
                tokens = line.split()
                region_name = tokens[0]
                region = regions.get(region_name, Region(region_name))
                subnet_str = tokens[1]
                subnet = ipaddress.ip_network(subnet_str)
                region.subnets.add(subnet)
                regions[region_name] = region
                

def process_service(output, first_timestamp, regions, window_duration, service, all_requests):
    """
    Arguments:
        output(Path): base output directory
        first_timestamp(datetime.datetime): timestamp of start of the scenario
        regions(dict): region name -> Region
        window_duration(datetime.timedelta): window duration
        service(str): service name being processed
        all_requests(dict): ClientRequest objects for the specified service 
    """
    
    window_start = None
    region_counts = dict()
    total_count = 0

    all_timestamps = list()
    all_region_percentages = dict() # region -> [percentages matching all_timestamps]

    for req in sorted(all_requests, key=lambda x: x.timestamp):
        timestamp = req.timestamp
        dest_region = req.dest_region

        if window_start is None:
            window_start = timestamp

        if timestamp - window_start > window_duration:
            # output current percentages
            compute_percentages(first_timestamp, window_start, window_duration, regions, total_count, region_counts, all_timestamps, all_region_percentages)

            # start a new window
            window_start = timestamp           
            total_count = 1
            region_counts = dict()
            region_counts[dest_region] = 1
        else:
            total_count = total_count + 1
            region_counts[dest_region] = region_counts.get(dest_region, 0) + 1


    if window_start is not None:
        compute_percentages(first_timestamp, window_start, window_duration, regions, total_count, region_counts, all_timestamps, all_region_percentages)

    if len(all_timestamps) < 1:
        get_logger().error("No data to processing, exiting without creating graphs")
        return 0

    fig, ax = map_utils.subplots()
    window_duration_minutes = window_duration.seconds / 60
    ax.set_title(f"Client request percentages for {service} to region over {window_duration_minutes} minute window")
    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Percentage of requests")
    for region_name, region in sorted(regions.items()):
        ax.step(all_timestamps, all_region_percentages[region], label=region_name, where='post')

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"percentage-client-requests-to-region-{service}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    
def main_method(args):
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
    
    parse_region_subnets(sim_output, regions)

    all_requests = dict() # service -> ClientRequest
    with open(all_requests_path) as f:
        reader = csv.DictReader(map_utils.skip_null_lines(f))
        for row in reader:
            ts_str = row['time_sent']
            service = row['service']
            timestamp = datetime.datetime.fromtimestamp(int(ts_str) / 1000, tz=pytz.UTC)
        
            dest_ip = row['server']
            dest_region = summarize_hifi.region_for_ip(regions, dest_ip)

            service_requests = all_requests.get(service, list())
            req = ClientRequest(timestamp, dest_region)
            service_requests.append(req)
            all_requests[service] = service_requests
    

    window_duration = datetime.timedelta(minutes=args.window_duration)

    for service, service_requests in all_requests.items():
        process_service(output, first_timestamp, regions, window_duration, service, service_requests)


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
