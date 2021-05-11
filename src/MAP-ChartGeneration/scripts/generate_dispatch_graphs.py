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
Generates graphs per client and per service of percentage dispatch to each server region.
Only works with hi-fi data.

Creates:
  * dispatch/{client region}-{service}-successes.png
  * dispatch/{client region}-{service}-failures.png
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
    import csv
    import ipaddress
    import map_utils
    import multiprocessing
    import pandas as pd
    import numpy as np
    import math

    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt

    import generate_hopcount_graphs


script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)


def process_data(first_timestamp_ms, host_ip, subnet_to_region, window_size, df):
    """
    Arguments:
        first_timestamp_ms(int): time that the simulation started in milliseconds since the Unix epoch
        host_ip(DataFrame): host and IP address information
        subnet_to_region(DataFrame): mapping of subnets to regions
        window_size(int): size of the window in minutes
        df(DataFrame): the data to process
    Returns:
        DataFrame: processed data
    """

    df['client lower'] = df['client'].str.lower()
    df['server lower'] = df['server'].str.lower()
    df = df.drop(columns=['event', 'time_sent', 'time_ack_received', 'latency', 'hop_count'], axis=1)

    # convert to NA after doing lower call so that we don't end up trying to lowercase a NaN server (unknown host)
    df.replace('?',np.NaN)

    # remove unknown hosts
    df = df[~df['server'].isna()]

    # get client IP addresses
    df = df.merge(host_ip, how='left', left_on=['client lower'], right_on=['host lower']).drop(['host', 'host lower'], axis=1)
    df = df.rename(columns={'ip': 'client ip'})

    # get server IP addresses
    df = df.merge(host_ip, how='left', left_on=['server lower'], right_on=['host lower']).drop(['host', 'host lower'], axis=1)
    df = df.rename(columns={'ip': 'server ip'})

    # get region information
    df['client region'] = df['client ip'].apply(lambda x: generate_hopcount_graphs.ip_to_region(x, subnet_to_region))
    df['server region'] = df['server ip'].apply(lambda x: generate_hopcount_graphs.ip_to_region(x, subnet_to_region))

    # relative time
    df['relative timestamp'] = df['timestamp'] - first_timestamp_ms
    df['relative minutes'] = df['relative timestamp'] / 1000 / 60

    min_minutes = math.floor(df['relative minutes'].min())
    max_minutes = math.ceil(df['relative minutes'].max())

    num_bins = math.ceil((max_minutes - min_minutes) / window_size)
    df['bin'] = pd.cut(df['relative minutes'], bins=num_bins, precision=0)

    return df


def process_service(output, server_regions, client_region, service, df, filename_label, graph_label):
    """
    Arguments:
        output(Path): directory for graphs
        server_regions(list): all server regions
        client_region(str): the client region to generate the graph for
        service(str): the service to generate the graph for
        df(DataFrame): All of the request data
        filename_label(str): what to append to the filname, should be 'successes' or 'failures'
        graph_label(str): what to put in the graph title, 'successful' or 'failed'
    """
    results = df.groupby(['bin', 'server region'])['relative minutes'].agg('count').reset_index()


    # server_region -> values to plot
    server_region_series = dict()
    bin_labels = list()
    bin_positions = list()
    for bin in sorted(results['bin'].unique()):
        bin_labels.append(f'{bin.left} - {bin.right})')
        bin_positions.append(bin.left)

        bin_results = results[results['bin'] == bin]

        # compute total value
        total = bin_results['relative minutes'].sum()

        get_logger().debug("Client region: %s service: %s bin: %s Total: %s label: %s", client_region, service, bin, total, filename_label)

        # compute percentages
        for server_region in sorted(server_regions):
            server_results = bin_results[bin_results['server region'] == server_region]['relative minutes']
            if len(server_results) > 0 and total > 0:
                value = float(server_results.iloc[0]) / total * 100
            else:
                value = 0
            l = server_region_series.get(server_region, list())
            l.append(value)
            server_region_series[server_region] = l

    # track the bottom values for the bar charts
    bottom = [0] * len(bin_labels)

    fig, ax = map_utils.subplots()
    ax.set_title(f'Dispatch of {graph_label} requests for {service} from {client_region}')
    ax.set_xlabel('Minute Window')
    ax.set_ylabel('% requests')
    ax.set_xticks(bin_positions, bin_labels)

    for server_region in sorted(server_regions):
        data = server_region_series.get(server_region, [0] * len(bin_labels))
        ax.bar(bin_positions, data, bottom=bottom, label=server_region, width=1)

        # move the bottom up for the next series
        bottom = np.add(bottom, data)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"{client_region}-{service}-{filename_label}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)



def main_method(args):
    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    window_size = int(args.window_size)
    get_logger().debug("Window size: %d", window_size)
    charts_dir = Path(args.chart_output)
    sim_output = Path(args.sim_output)

    output = Path(args.output)
    dispatch_output = output / 'dispatch'
    dispatch_output.mkdir(parents=True, exist_ok=True)

    host_ip = pd.read_csv(sim_output / 'inputs/scenario/host-ip.csv')
    host_ip['host lower'] = host_ip['host'].str.lower()

    subnet_to_region = generate_hopcount_graphs.load_subnet_information(sim_output)

    frames = list()
    latency_dns = charts_dir / 'latency_dns'
    for filename in latency_dns.glob('client_processing_latency-*.csv'):
        get_logger().debug("Reading %s", filename)

        match = re.match(r'client_processing_latency-(.*).csv', filename.name)
        if not match:
            raise RuntimeError("Could not find service name in " + filename)
        service = match.group(1)
        df = pd.read_csv(filename)
        if len(df.index) < 1:
            get_logger().warning("No data found in %s", filename)
        else:
            df['service'] = service
        frames.append(df)

    if len(frames) < 1:
        get_logger().error("No data to process")
        return 0
    df = pd.concat(frames)

    df = process_data(first_timestamp_ms, host_ip, subnet_to_region, window_size, df)

    server_regions = list(filter(None, df['server region'].unique()))

    for client_region in df['client region'].unique():
        client_data = df[df['client region'] == client_region]

        services = df['service'].unique()
        for service in services:
            service_data = client_data[client_data['service'] == service]
            successes = service_data[service_data.success]
            process_service(dispatch_output, server_regions, client_region, service, successes, 'successes', 'successful')

            failures = service_data[~service_data.success]
            process_service(dispatch_output, server_regions, client_region, service, failures, 'failures', 'failed')


    return 0


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
    parser.add_argument("-c", "--chart_output", dest="chart_output", help="Chart output directory", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory", required=True)
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory", required=True)
    parser.add_argument("-w", "--window-size", dest="window_size", help="Minutes over which to collect data", default=3, type=int)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)

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
