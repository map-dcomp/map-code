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
Generate graphs to aide in debugging overflow plans.

* overflow_analysis-{region}-{service}.png
* overflow_analysis-{DCOP,RLG,DNS}-{region}-{service}.png
* overflow_analysis_expected-{DCOP,RLG,DNS}-{source_region}-{service}.png
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
    import math

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def get_plan_at_time(all_regions, plans, plan_region, service, time_minutes):
    """
    Arguments:
        all_regions(set): all regions
        plans(DataFrame): DCOP/RLG/DNS plans
        plan_region: the region that generated the plans that we are interested in
        service: the service that we are interested in the plan for
        time_minutes(int): time we are interested in the plan for
    Returns:
        dict: region_name -> percentage, may be empty
    """
    service_plan = dict()
    
    plans_filtered = plans.loc[(plans['plan_region'] == plan_region) & (plans['service'] == service)]
    row = plans_filtered.loc[plans_filtered['time_minutes'] <= time_minutes].sort_values(by=['time_minutes'], ascending=False)[:1]
    if len(row.index) < 1:
        return service_plan

    series = row.iloc[0]
    for col in series.index:
        if col in all_regions:
            service_plan[col] = series[col]
    return service_plan


def compute_expected_at_time(all_regions, plan_label, plans, source_region, service, time, seen_regions):
    """
    Compute the expected percentages over time based on traffic
    starting in source_region and following the plans.

    Arguments:
        all_regions(set): all regions    
        plan_label(str): type of plan (RLG, DCOP, DNS)
        plans(DataFrame): plan update information
        source_region(str): name of the region where the traffic is initiated
        service(str): name of the service to consider
        time(int): minutes from start
        seen_regions(list): the regions that have been seen so far
    Returns:
        dict: region to weight to the region
    """

    # value to use when a loop is found
    error_percent = 100

    # region to percent of traffic to this region, sum of the values should be 1
    region_percentages = dict()
    
    if source_region in seen_regions:
        # put a bad value instead of raising an error so that we get
        # some plots, but make the plot obvious that something is
        # wrong
        get_logger().error("%s for %s at %d minutes: Loop found %s back to %s, putting large value in graph and returning", plan_label, service, time, seen_regions, source_region)

        for r in all_regions:
            region_percentages[r] = error_percent
        return region_percentages

    seen_regions.append(source_region)

    region_plan = get_plan_at_time(all_regions, plans, source_region, service, time)
    get_logger().debug("%s: service %s source region %s at %d: %s", plan_label, service, source_region, time, region_plan)
    
    for region, weight in region_plan.items():
        if region == source_region:
            get_logger().debug("Source region %s has weight %f", region, weight)
            region_percentages[region] = weight
            
        elif weight > 0:
            recurse_seen = seen_regions.copy()
            get_logger().debug("Recursing to %s weight is %f", region, weight)
            
            recurse_percentages = compute_expected_at_time(all_regions, plan_label, plans, region, service, time, recurse_seen)
            for r, w in recurse_percentages.items():
                raw_percent = region_percentages.get(r, 0)
                weighted = raw_percent + (weight * w)
                get_logger().debug("After recursing to %s source region %s computed weight for %s as %f -> %f", region, source_region, r, raw_percent, weighted)
                
                region_percentages[r] = weighted

    total_weight = sum(region_percentages.values())
    get_logger().debug("%s for %s at %d minutes has total weight of %f when starting at %s", plan_label, service, time, total_weight, source_region)
    
    if abs(total_weight - 1) > 0.1 and abs(total_weight) > 0.1:
        get_logger().warning("%s for %s at %d minutes: Total weight of all regions when starting at %s is %f and should be 1", plan_label, service, time, source_region, total_weight)
        
    get_logger().debug("%s: service %s source region %s at %d final percentages %s", plan_label, service, source_region, time, region_percentages)
    return region_percentages


def compute_expected(sample_duration, all_regions, plan_label, plans, source_region, service):
    """
    Compute the expected percentages over time based on traffic
    starting in source_region and following the plans.

    Arguments:
        sample_duration(float): how many minutes between samples of the plans
        all_regions(set): all regions    
        plan_label(str): type of plan (RLG, DCOP, DNS)
        plans(DataFrame): plan update information
        source_region(str): name of the region where the traffic is initiated
        service(str): name of the service to consider
    Returns:
        list: list of times
        dict: region -> list of percentages in the same order as list of times
    """

    current_time = plans['time_minutes'].min()
    max_time = plans['time_minutes'].max()
    current_region = source_region
    times = list()
    region_weights = dict()

    while current_time < max_time:
        weights_at_time = compute_expected_at_time(all_regions, plan_label, plans, source_region, service, current_time, list())
        get_logger().debug("%s: service %s source region %s at %d computed %s", plan_label, service, source_region, current_time, weights_at_time)

        times.append(current_time)
        for region in all_regions:
            region_weight = weights_at_time.get(region, 0)
            weight_list = region_weights.get(region, list())
            weight_list.append(region_weight)
            region_weights[region] = weight_list
        
        current_time = current_time + sample_duration
    
    return times, region_weights


def plot_expected(output_dir, plan_label, source_region, service, times, region_weights):
    """
    Arguments:
        output_dir(Path): base output directory
        plan_label(str): type of plan (RLG, DCOP, DNS)
        source_region(str): name of region that the traffic starts at
        service(str): the service to consider
        times(list): from compute_expected
        region_weights(dict): from computed_expected
    """

    fig, ax = map_utils.subplots()
    ax.set_title(f'Expected percentages of traffic for {service} when starting at {source_region} based on {plan_label}')
    ax.set_ylabel('% dispatch to region')
    ax.set_xlabel('time (minutes)')
    ax.set_xlim(left=0, right=max(times))

    fig_stack, ax_stack = map_utils.subplots()
    ax_stack.set_title(f'Expected percentages of traffic for {service} when starting at {source_region} based on {plan_label}')
    ax_stack.set_ylabel('% dispatch to region')
    ax_stack.set_xlabel('time (minutes)')
    ax_stack.set_xlim(left=0, right=max(times))
    
    # track the bottom values for the bar charts
    bottom = [0] * len(times)
    
    ydata = list()
    labels = list()
    for region, weight_list in region_weights.items():
        ax.step(times, weight_list, label=region, where='post')

        ax_stack.bar(times, weight_list, bottom=bottom, label=region, width=1)

        # move the bottom up for the next series
        bottom = np.add(bottom, weight_list)
        

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / f'overflow_analysis_expected-{plan_label}-{source_region}-{service}.png'
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    handles_stack, labels_stack = ax_stack.get_legend_handles_labels()
    lgd_stack = ax_stack.legend(handles_stack, labels_stack, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name_stack = output_dir / f'overflow_analysis_expected-{plan_label}-{source_region}-{service}_stacked.png'
    fig_stack.savefig(output_name_stack.as_posix(), format='png', bbox_extra_artists=(lgd_stack,), bbox_inches='tight')
    plt.close(fig_stack)

    
def plot_region_service_plan_type(output_dir, plan_type, plans, all_regions, region, service):
    """
    Graph individual overflow plans on a graph.
    
    Arguments:
        output_dir(Path): directory to write the results in
        plan_type(str): RLG, DCOP, DNS
        plans(DataFrame): plan update information
        all_regions(set): all regions
        region(str): region to generate graph for
        service(str): service to generate graph for
    """
    plot_data = plans.loc[(plans['plan_region'] == region) & (plans['service'] == service)]
    max_x = plot_data['time_minutes'].max()
    if not math.isfinite(max_x):
        get_logger().warning("Non-finite max_x found %s graphing %s for %s and %s", max_x, plan_type, region, service)
        return

    get_logger().info("graphing %s for %s and %s with max_x %s", plan_type, region, service, max_x)

    fig, ax = map_utils.subplots()
    ax.set_title(f'{plan_type} overflow plans for service {service} in region {region}')
    ax.set_ylabel('% dispatch to region')
    ax.set_xlabel('time (minutes)')
    ax.set_xlim(left=0, right=max_x)

    fig_stack, ax_stack = map_utils.subplots()
    ax_stack.set_title(f'{plan_type} overflow plans for service {service} in region {region}')
    ax_stack.set_ylabel('% dispatch to region')
    ax_stack.set_xlabel('time (minutes)')
    ax_stack.set_xlim(left=0, right=max_x)

    times = plot_data['time_minutes']
    
    # track the bottom values for the bar charts
    bottom = [0] * len(times)
    
    labels = list()
    ydata = list()
    for overflow_region in all_regions:
        data = plot_data[overflow_region]
        ax.step(times, data,
                 label=overflow_region, where='post')

        ax_stack.bar(times, data, bottom=bottom, label=overflow_region, width=1)

        # move the bottom up for the next series
        bottom = np.add(bottom, data)
        
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output_dir / f"overflow_analysis-{plan_type}-{region}-{service}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)

    handles_stack, labels_stack = ax_stack.get_legend_handles_labels()
    lgd_stack = ax_stack.legend(handles_stack, labels_stack, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name_stack = output_dir / f'overflow_analysis-{plan_type}-{region}-{service}_stacked.png'
    fig_stack.savefig(output_name_stack.as_posix(), format='png', bbox_extra_artists=(lgd_stack,), bbox_inches='tight')
    plt.close(fig_stack)
    

def compute_client_regions(scenario_dir):
    regions = set()
    for fname in scenario_dir.glob('*.json'):
        with open(fname) as f:
            data = json.load(f)
            if 'client' in data and 'region' in data:
                if data['client']:
                    regions.add(data['region'])
    return regions


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-c", "--chart_output", dest="chart_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    parser.add_argument("--sample-duration", dest="sample_duration", help="Number of minutes between samples of plans for expected percentages plots", default=1, type=float)
    parser.add_argument("--scenario", dest="scenario", help="Scenario directory (Required)", required=True)
    
    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    
    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    get_logger().info("Simulation started at %s", first_timestamp)
    
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    dcop_file = chart_output / 'dcop_plan_updates/all_dcop_plans.csv'
    dcop_plans = pd.read_csv(dcop_file, na_values="?")
    dcop_plans.fillna(0, inplace=True)
    dcop_plans.sort_values(by=['time'], inplace=True)

    rlg_file = chart_output / 'rlg_plan_updates/all_rlg_overflow_plans.csv'
    rlg_plans = pd.read_csv(rlg_file, na_values="?")
    rlg_plans.fillna(0, inplace=True)
    rlg_plans.sort_values(by=['time'], inplace=True)

    dns_file = chart_output / 'dns_region_plan_updates/all_dns_region_plans.csv'
    dns_plans = pd.read_csv(dns_file, na_values="?")
    dns_plans.fillna(0, inplace=True)
    dns_plans.sort_values(by=['time'], inplace=True)

    first_timestamp_ms = first_timestamp.timestamp() * 1000
    dcop_plans['time_minutes'] = (dcop_plans['time'] - first_timestamp_ms) / 1000 / 60
    rlg_plans['time_minutes'] = (rlg_plans['time'] - first_timestamp_ms) / 1000 / 60
    dns_plans['time_minutes'] = (dns_plans['time'] - first_timestamp_ms) / 1000 / 60

    all_regions = np.union1d(dcop_plans['plan_region'].unique(), rlg_plans['plan_region'].unique())
    all_services = np.union1d(dcop_plans['service'].unique(), rlg_plans['service'].unique())

    for region in all_regions:
        for service in all_services:
            plot_region_service_plan_type(output_dir, "DCOP", dcop_plans, all_regions, region, service)
            plot_region_service_plan_type(output_dir, "RLG", rlg_plans, all_regions, region, service)
            plot_region_service_plan_type(output_dir, "DNS", dns_plans, all_regions, region, service)


    scenario_dir = Path(args.scenario)
    if not scenario_dir.exists():
        get_logger().error("%s does not exist", scenario_dir)
        return 1
    
    client_regions = compute_client_regions(scenario_dir)
    
    # plot expected percentages
    sample_duration = args.sample_duration
    for source_region in client_regions:
        for service in all_services:
            times, region_weights = compute_expected(sample_duration, all_regions, 'DCOP', dcop_plans, source_region, service)
            plot_expected(output_dir, 'DCOP', source_region, service, times, region_weights)
            times, region_weights = compute_expected(sample_duration, all_regions, 'RLG', rlg_plans, source_region, service)
            plot_expected(output_dir, 'RLG', source_region, service, times, region_weights)
            times, region_weights = compute_expected(sample_duration, all_regions, 'DNS', dns_plans, source_region, service)
            plot_expected(output_dir, 'DNS', source_region, service, times, region_weights)

    get_logger().info("Finished")

if __name__ == "__main__":
    sys.exit(main())
    
