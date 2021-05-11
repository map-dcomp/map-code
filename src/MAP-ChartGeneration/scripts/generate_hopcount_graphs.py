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
Generates graphs of hopcounts per client and per service.
Only works with hi-fi data.

Creates:
  * hopcount-{client}-{service}-successes.png
  * hopcount-{client}-{service}-failures.png
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

    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    

script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)


def process_service(filename, service, host_ip, subnet_to_region, hopcount, output):
    """
    Arguments:
        filename(Path): the file to read the client request information from
        service(str): service name
        host_ip(DataFrame): host and IP address information
        subnet_to_region(DataFrame): mapping of subnets to regions
        hopcount(DataFrame): regional hop count iformation
        output(Path): directory to output to
    """
    
    df = pd.read_csv(filename)
    if len(df.index) < 1:
        get_logger().warning("No data found in %s", filename)
        return
    
    df['client lower'] = df['client'].str.lower()
    df['server lower'] = df['server'].str.lower()
    df = df.drop(columns=['event', 'time_sent', 'time_ack_received', 'latency', 'hop_count'], axis=1)
    
    # convert to NA after doing lower call so that we don't end up trying to lowercase a NaN server (unknown host)
    df = df.replace('?', np.NaN)
    
    # remove unknown hosts
    df = df[~df['server'].isna()]

    # get client IP addresses
    df = df.merge(host_ip, how='left', left_on=['client lower'], right_on=['host lower']).drop(['host', 'host lower'], axis=1)
    df = df.rename(columns={'ip': 'client ip'})

    # get server IP addresses
    df = df.merge(host_ip, how='left', left_on=['server lower'], right_on=['host lower']).drop(['host', 'host lower'], axis=1)
    df = df.rename(columns={'ip': 'server ip'})    

    # get region information
    df['client region'] = df['client ip'].apply(lambda x: ip_to_region(x, subnet_to_region))
    df['server region'] = df['server ip'].apply(lambda x: ip_to_region(x, subnet_to_region))

    # get hopcounts
    df = df.merge(hopcount, how='left', left_on=['client region', 'server region'], right_on=['from', 'to'])

    for client in df['client'].unique():
        # plot successes
        successes = df[df.success]
        counts = successes[successes['client'] == client].groupby(by='hop count').agg('count')['timestamp']

        fig, ax = map_utils.subplots()
        ax.set_title(f'hop counts for {client} {service} - {counts.sum()} successful requests')

        color_palette = map_utils.get_plot_colors()
        colors = list()
        labels=list()
        for idx in counts.index:
            colors.append(color_palette[int(idx)])
            labels.append(int(idx))
            
        ax.pie(counts, labels=labels, colors=colors, autopct='%1.1f%%')
        ax.axis('equal')  # Equal aspect ratio ensures that pie is drawn as a circle.

        output_name = output / f'hopcount-{client}-{service}-successes.png'
        fig.savefig(output_name.as_posix(), format='png', bbox_inches='tight')
        plt.close(fig)
        
        # plot failures
        failures = df[~df.success]
        counts = failures[failures['client'] == client].groupby(by='hop count').agg('count')['timestamp']

        fig, ax = map_utils.subplots()
        ax.set_title(f'hop counts for {client} {service} - {counts.sum()} failed requests')

        ax.pie(counts, labels=counts.index, autopct='%1.1f%%')
        ax.axis('equal')  # Equal aspect ratio ensures that pie is drawn as a circle.

        output_name = output / f'hopcount-{client}-{service}-failures.png'
        fig.savefig(output_name.as_posix(), format='png', bbox_inches='tight')
        plt.close(fig)

    for client_region in df['client region'].unique():
        # plot successes
        successes = df[df.success]
        counts = successes[successes['client region'] == client_region].groupby(by='hop count').agg('count')['timestamp']

        fig, ax = map_utils.subplots()
        ax.set_title(f'hop counts for client region {client_region} {service} - {counts.sum()} successful requests')

        color_palette = map_utils.get_plot_colors()
        colors = list()
        labels=list()
        for idx in counts.index:
            colors.append(color_palette[int(idx)])
            labels.append(int(idx))
            
        ax.pie(counts, labels=labels, colors=colors, autopct='%1.1f%%')
        ax.axis('equal')  # Equal aspect ratio ensures that pie is drawn as a circle.

        output_name = output / f'hopcount-region_{client_region}-{service}-successes.png'
        fig.savefig(output_name.as_posix(), format='png', bbox_inches='tight')
        plt.close(fig)
        
        # plot failures
        failures = df[~df.success]
        counts = failures[failures['client region'] == client_region].groupby(by='hop count').agg('count')['timestamp']

        fig, ax = map_utils.subplots()
        ax.set_title(f'hop counts for client region {client_region} {service} - {counts.sum()} failed requests')

        ax.pie(counts, labels=counts.index, autopct='%1.1f%%')
        ax.axis('equal')  # Equal aspect ratio ensures that pie is drawn as a circle.

        output_name = output / f'hopcount-region_{client_region}-{service}-failures.png'
        fig.savefig(output_name.as_posix(), format='png', bbox_inches='tight')
        plt.close(fig)
        
    
def load_subnet_information(sim_output):
    """
    Arguments:
        sim_output(Path): sime output directory
    Returns:
        dict: subnet to region name
    """
    
    subnet_to_region = dict()
    with open(sim_output / 'inputs/region_subnet.txt') as f:
        for line in f:
            tokens = line.split()
            region_name = tokens[0]
            subnet_str = tokens[1]
            subnet = ipaddress.ip_network(subnet_str)
            subnet_to_region[subnet] = region_name
            
    return subnet_to_region


def load_hopcount(charts_dir):
    """
    Arguments:
        charts_dir(Path): directory where the charts are output
    Returns:
        DataFrame: hopcount information
    """
    hopcount = pd.read_csv(charts_dir / 'hopcount.csv')
    for source in hopcount['from'].unique():
        hopcount = hopcount.append({'from': source, 'to': source, 'hop count': 0}, ignore_index=True)    

    return hopcount


def ip_to_region(ip_str, subnet_to_region):
    if isinstance(ip_str, float) and np.isnan(ip_str):
        return None
    
    ip = ipaddress.ip_address(ip_str)
    for subnet, region in subnet_to_region.items():
        if ip in subnet:
            return region
    return None


def main_method(args):
    charts_dir = Path(args.chart_output)
    sim_output = Path(args.sim_output)

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    host_ip = pd.read_csv(sim_output / 'inputs/scenario/host-ip.csv')
    host_ip['host lower'] = host_ip['host'].str.lower()

    subnet_to_region = load_subnet_information(sim_output)
    hopcount = load_hopcount(charts_dir)

    latency_dns = charts_dir / 'latency_dns'
    for filename in latency_dns.glob('client_processing_latency-*.csv'):
        match = re.match(r'client_processing_latency-(.*).csv', filename.name)
        if not match:
            raise RuntimeError("Could not find service name in " + filename)
        service = match.group(1)
        process_service(filename, service, host_ip, subnet_to_region, hopcount, output)

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
    parser.add_argument("-c", "--chart_output", dest="chart_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)

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
    
