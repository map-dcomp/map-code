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
Generate graphs to aid in debugging DNS region and container resolution.

* dns-region-resolution-counts-{region}-{service}.png
* dns-container-resolution-counts-{region}-{service}.png

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
    import matplotlib.pyplot as plt
    import numpy as np
    import pandas as pd
    from pathlib import Path
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)




def plot_dns_region_resolution_counts(output_dir, dns_resolution_counts, all_regions, from_region, service):
    """
    Graph individual overflow plans on a graph.
    
    Arguments:
        output_dir(Path): directory to write the results in
        dns_resolution_counts(DataFrame): dns resolution data for from_region and service
        all_regions(set): all regions
        from_region(str): region to generate graph for
        service(str): service to generate graph for
    """
    plot_data = dns_resolution_counts

    fig, ax = map_utils.subplots()
    ax.set_title(f'DNS region resolution counts for service {service} in region {from_region}')
    ax.set_ylabel('Resolutions')
    ax.set_xlabel('time (minutes)')
    

    for overflow_region in sorted(list(all_regions)):
        ax.plot(plot_data['time_minutes'], plot_data[overflow_region], label="{0}".format(overflow_region))
        
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / f"dns-region-resolution-counts-{from_region}-{service}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


def plot_dns_container_resolution_counts(output_dir, dns_resolution_counts, region, service):
    """
    Graph individual overflow plans on a graph.
    
    Arguments:
        output_dir(Path): directory to write the results in
        dns_resolution_counts(DataFrame): dns resolution data for from_region and service
        region(str): region to generate graph for
        service(str): service to generate graph for
    """
    
    containers = list(dns_resolution_counts.loc[:, (dns_resolution_counts != 0).any(axis=0)].filter(regex='.+_c.+', axis='columns').columns)
    plot_data = dns_resolution_counts

    fig, ax = map_utils.subplots()
    ax.set_title(f'DNS resolution counts for containers of service {service} in region {region}')
    ax.set_ylabel('Resolutions')
    ax.set_xlabel('time (minutes)')

    for container in sorted(list(containers)):
        ax.plot(plot_data['time_minutes'], plot_data[container], label="{0}".format(container))
        
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / f"dns-container-resolution-counts-{region}-{service}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    

def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-c", "--chart_output", dest="chart_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    
    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    chart_output_dns = chart_output / 'dns'
    get_logger().debug("chart_output_dns: %s", chart_output_dns)

    all_regions = set()
    all_services = set()
    service_region_dns_resolution_counts = dict()

    earliest_time = None
    
    for dns_req_count_file in chart_output_dns.glob('dns_req_count_*.csv'):
        match = re.match("dns_req_count_(.+)--(.+)\.csv", os.path.basename(dns_req_count_file))

        if match:
            region = match.group(1)
            service = match.group(2)
            get_logger().info("Found file '%s' for region '%s' and service '%s'", dns_req_count_file, region, service)
            all_regions.add(region)
            all_services.add(service)

            dns_resolution_counts = pd.read_csv(dns_req_count_file, na_values="?")
            service_region_dns_resolution_counts.setdefault(service, dict())[region] = dns_resolution_counts
            
            if earliest_time is None:
                earliest_time = dns_resolution_counts['time'].min()
            else:
                earliest_time = min(earliest_time, dns_resolution_counts['time'].min())


    for service in all_services:
        for region in all_regions:
            service_region_dns_resolution_counts[service][region]['time_minutes'] = (service_region_dns_resolution_counts[service][region]['time'] - earliest_time) / 1000 / 60
            
            get_logger().info("Output plots for service '%s' and region '%s'", service, region)
            plot_dns_region_resolution_counts(output_dir, service_region_dns_resolution_counts[service][region], all_regions, region, service)
            plot_dns_container_resolution_counts(output_dir, service_region_dns_resolution_counts[service][region], region, service)


if __name__ == "__main__":
    sys.exit(main())
    
