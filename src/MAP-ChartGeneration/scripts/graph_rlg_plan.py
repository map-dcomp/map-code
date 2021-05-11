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
Create graphs showing the RLG plan vs. actual container allocation.
There is also a feature to optionally graph the container load and demand as it is available on the RLG node.
These RLG load and demand values can differ from load and demand on NCPs across the topology in non-ideal situations with large resource report propagation delays for example. 

Outputs:
    - rlg-plan_{ncp}_total.png
    - rlg-plan_{ncp}_{service}.png
"""

import warnings
with warnings.catch_warnings():
    import re
    import sys
    import argparse
    import os
    import logging
    import logging.config
    import json
    from pathlib import Path
    import map_utils
    # use a non-GUI backend for matplotlib
    import matplotlib
    matplotlib.use('Agg')
    
    import matplotlib.pyplot as plt
    import multiprocessing

script_dir=Path(__file__).parent.absolute()

def get_logger():
    return logging.getLogger(__name__)


def process_rlg_plan(rlg_plan):
    total = 0
    per_service = dict()

    service_plan = rlg_plan['servicePlan']
    for node, container_info_list in service_plan.items():
        for container_info in container_info_list:
            service = container_info['service']['artifact']
            if not container_info['stopTrafficTo'] and not container_info['stop']:
                total = total + 1
                per_service[service] = per_service.get(service, 0) + 1
    return total, per_service


def process_reports(resource_reports, show_load_demand_attr):
    total_allocation = 0
    total_load = 0
    total_demand = 0

    per_service_allocation = dict()
    per_service_load = dict()
    per_service_demand = dict()

    for report in resource_reports:
        for container, creport in report['containerReports'].items():
            #get_logger().info(f"creport: {creport}")
            full_service = creport['service']
            service = full_service['artifact']

            compute_capacity = 1
            compute_load = 0
            compute_demand = 0

            if show_load_demand_attr:
                if show_load_demand_attr in creport['computeCapacity']:
                    compute_capacity = creport['computeCapacity'][show_load_demand_attr]
                else:
                    get_logger().warn(f"No {show_load_demand_attr} in computeCapacity ({creport['computeCapacity']}) in report for container {container}")

                get_logger().debug(f"creport['computeLoad']: {creport['computeLoad']}")
                for source_client, load in creport['computeLoad'].items():
                    if show_load_demand_attr in load:
                        compute_load += load[show_load_demand_attr]

                get_logger().debug(f"creport['computeDemand']: {creport['computeDemand']}")
                for source_client, demand in creport['computeDemand'].items():
                    if show_load_demand_attr in demand:
                        compute_demand += demand[show_load_demand_attr]

            total_allocation += compute_capacity
            total_load += compute_load
            total_demand += compute_demand

            per_service_allocation[service] = per_service_allocation.get(service, 0) + compute_capacity
            per_service_load[service] = per_service_load.get(service, 0) + compute_load
            per_service_demand[service] = per_service_demand.get(service, 0) + compute_demand

    return total_allocation, per_service_allocation, total_load, per_service_load, total_demand, per_service_demand


def process_node(output, first_timestamp_ms, all_services, node_dir, show_load_demand_attr):
    minutes = list()
    planned_total = list() # number of planned containers total
    planned_services = dict() # service -> list(value)
    actual_total_allocation = list() # number of containers
    actual_services_allocation = dict() # service -> list(allocation value)
    actual_total_load = list() # load
    actual_services_load = dict() # service -> list(load value)
    actual_total_demand = list() # demand
    actual_services_demand = dict() # service -> list(demand value)

    ncp = node_dir.stem
    get_logger().info("Processing directory for node %s: %s", ncp, node_dir)

    for time_dir in sorted(node_dir.iterdir()):
        if not time_dir.is_dir():
            continue

        get_logger().debug("Working on %s", time_dir)

        directory_time = int(time_dir.stem)
        directory_time_min = map_utils.timestamp_to_minutes(directory_time - first_timestamp_ms)

        rlg_plan_file = time_dir / 'loadBalancerPlan.json'
        if not rlg_plan_file.exists():
            continue
        resource_reports_file = time_dir / 'regionResourceReports-SHORT.json'
        if not resource_reports_file.exists():
            continue

        try:
            with open(rlg_plan_file, 'r') as f:
                rlg_plan = json.load(f)
        except json.decoder.JSONDecodeError:
            get_logger().warning("Problem reading %s, skipping", rlg_plan_file)
            continue

        try:
            with open(resource_reports_file, 'r') as f:
                resource_reports = json.load(f)
        except json.decoder.JSONDecodeError:
            get_logger().warning("Problem reading %s, skipping", resource_reports_file)
            continue

        minutes.append(directory_time_min)

        (p_total, p_per_service) = process_rlg_plan(rlg_plan)
        planned_total.append(p_total)
        for service in all_services:
            service_list = planned_services.get(service, list())
            if service in p_per_service:
                service_list.append(p_per_service[service])
            else:
                service_list.append(0)
            planned_services[service] = service_list

        (a_total_allocation, a_per_service_allocation, a_total_load, a_per_service_load, a_total_demand, a_per_service_demand) = process_reports(resource_reports, show_load_demand_attr)
        actual_total_allocation.append(a_total_allocation)
        actual_total_load.append(a_total_load)
        actual_total_demand.append(a_total_demand)
        for service in all_services:
            service_list_allocation = actual_services_allocation.get(service, list())
            if service in a_per_service_allocation:
                service_list_allocation.append(a_per_service_allocation[service])
            else:
                service_list_allocation.append(0)
            actual_services_allocation[service] = service_list_allocation

            service_list_load = actual_services_load.get(service, list())
            if service in a_per_service_load:
                service_list_load.append(a_per_service_load[service])
            else:
                service_list_load.append(0)
            actual_services_load[service] = service_list_load

            service_list_demand = actual_services_demand.get(service, list())
            if service in a_per_service_demand:
                service_list_demand.append(a_per_service_demand[service])
            else:
                service_list_demand.append(0)
            actual_services_demand[service] = service_list_demand


        graph(output, ncp, 'total', minutes, planned_total, actual_total_allocation, actual_total_load, actual_total_demand, show_load_demand_attr)
        for service in all_services:
            graph(output, ncp, service, minutes, planned_services[service], actual_services_allocation[service], actual_services_load[service], actual_services_demand[service], show_load_demand_attr)

            
def graph(output, ncp, service_or_total, minutes, planned, actual_allocation, actual_load, actual_demand, show_load_demand_attr):
    if len(minutes) < 1:
        get_logger().debug("Nothing to output for %s %s", ncp, service_or_total)
        return

    max_minutes = max(minutes)

    fig, ax = map_utils.subplots()
    ax.set_title(f"RLG region plan for {ncp} {service_or_total}")
    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Containers")
    ax.set_xlim(left=0, right=max_minutes)

    ax.plot(minutes, planned, label="Planned Allocation")
    ax.plot(minutes, actual_allocation, label="Actual Allocation")

    if show_load_demand_attr:
        ax.plot(minutes, actual_load, label="RLG Load")
        ax.plot(minutes, actual_demand, label="RLG Demand")

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    output_name = output / f"rlg-plan_{ncp}_{service_or_total}.png"
    fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    

def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    scenario_dir = Path(args.scenario)
    if not scenario_dir.exists():
        get_logger().error("%s does not exist", scenario_dir)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
        first_timestamp_ms = first_timestamp.timestamp() * 1000
        get_logger().info("Simulation started at %s -> %d", first_timestamp, first_timestamp_ms)
    
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    show_load_demand_attr = args.show_load_demand_attr if args.show_load_demand_attr != '' else None

    all_services = map_utils.gather_all_services(scenario_dir)
    
    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()
        for node_dir in sim_output.iterdir():
            if not node_dir.is_dir():
                continue
            node_name_dir = map_utils.find_ncp_folder(node_dir)
            if node_name_dir is None:
                get_logger().debug("No NCP folder found in %s", node_dir)
                continue
            get_logger().debug(f"node_dir: {node_dir}")
            results.append(pool.apply_async(func=process_node, args=[output, first_timestamp_ms, all_services, node_name_dir, show_load_demand_attr]))

        for result in results:
            result.wait()


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
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--scenario", dest="scenario", help="Scenario directory (Required)", required=True)
    parser.add_argument("--show-load-demand-attr", dest="show_load_demand_attr", help="Show compute load and demand available to RLG for the given load attribute.")
    
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
