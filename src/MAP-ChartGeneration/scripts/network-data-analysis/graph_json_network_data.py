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
Create graphs of the network data based on the JSON files directly.

- network-{node}-Summary-{demand,load}.png - network data from the resource summary using the long estimation window
- network-{node}-Report-{load}.png - network data from the resource report using the short estimation window

"""

import warnings

with warnings.catch_warnings():
    # use a non-GUI backend for matplotlib - must be first
    import matplotlib
    matplotlib.use('Agg')
    
    import re
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    import matplotlib.pyplot as plt
    import pandas as pd
    from pathlib import Path
    import numpy as np
    from numpy.polynomial.polynomial import Polynomial
    sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
    import map_utils


script_dir = os.path.abspath(os.path.dirname(__file__))

rolling_average_window_size = 10

class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"


class NetworkData(Base):
    def __init__(self):
        # time -> value
        self.rx = dict()
        self.tx = dict()
        self.all_traffic = dict()
        

    def add_data(self, time, network_data):
        """
        Arguments:
            time(int): timestamp of the data
            network_data(dict): link attribute -> value
        """
        rx = float(network_data.get("DATARATE_RX", 0))
        tx = float(network_data.get("DATARATE_TX", 0))
        
        self.rx[time] = self.rx.get(time, 0) + rx
        self.tx[time] = self.tx.get(time, 0) + tx
        self.all_traffic[time] = self.all_traffic.get(time, 0) + rx + tx
        

    def init_time(self, time):
        """
        If the specified time doesn't have a value, give it the value 0.
        """
        if time not in self.rx:
            self.rx[time] = 0
        if time not in self.tx:
            self.tx[time] = 0
        if time not in self.all_traffic:
            self.all_traffic[time] = 0
        

class NodeNetworkData(Base):
    def __init__(self, all_services):
      self.all_services = all_services
      # service -> NetworkData
      self.service = dict()
      for service in all_services:
          self.service[service] = NetworkData()

          
    def add_data(self, time, data):
        """
        Arguments:
            time(int): timestamp of the data
            data(dict): JSON version of network load or demand from a resource report or summary
        """
        for service in self.all_services:
            self.service[service].init_time(time)

        for neighbor, n_data in data.items():
            for flow, f_data in n_data.items():
                for service, s_data in f_data.items():
                    service_name = map_utils.get_service_artifact(service)
                    if service_name:
                        if service_name in self.service:
                            # exclude unmanaged services
                            self.service[service_name].add_data(time, s_data)
                    else:
                        get_logger().warn("Could not parse service name from %s", service)

                        
def get_logger():
    return logging.getLogger(__name__)


def process_node(all_services, node_dir):
    """
    Arguments:
        all_services(set): names of all services
        node_dir(Path): directory to the node
    Returns:
        str: node name or None if not found
        NodeNetworkData: network load data from resource summaries or None on an error
        NodeNetworkData: network demand data from resource summaries or None on an error
        NodeNetworkData: network load data from resource report or None on an error
        int: first timestamp
    """
    
    node_name_dir = map_utils.find_ncp_folder(node_dir)
    if node_name_dir is None:
        get_logger().debug("No NCP folder found in %s", node_dir)
        return None, None, None, None, None

    node_name = map_utils.node_name_from_dir(node_name_dir)
    network_summary_load = NodeNetworkData(all_services)
    network_summary_demand = NodeNetworkData(all_services)
    network_report_load = NodeNetworkData(all_services)

    first_timestamp = None
    get_logger().debug("Processing ncp folder %s from %s. NCP name: %s", node_name_dir, node_dir, node_name)
    for time_dir in node_name_dir.iterdir():
        if not time_dir.is_dir():
            continue

        time = int(time_dir.stem)
        if first_timestamp is None or time < first_timestamp:
            first_timestamp = time

        resource_summary_file = time_dir / 'resourceSummary-LONG.json'
        if resource_summary_file.exists():
            try:
                with open(resource_summary_file, 'r') as f:
                    resource_summary = json.load(f)

                if 'networkLoad' in resource_summary:
                    network_load = resource_summary['networkLoad']
                    network_summary_load.add_data(time, network_load)

                if 'networkDemand' in resource_summary:
                    network_demand = resource_summary['networkDemand']
                    network_summary_demand.add_data(time, network_demand)
            except json.decoder.JSONDecodeError:
                get_logger().warning("Problem reading %s, skipping", resource_summary_file)

        resource_report_file = time_dir / 'resourceReport-SHORT.json'
        if resource_report_file.exists():
            try:
                with open(resource_report_file, 'r') as f:
                    resource_report = json.load(f)

                if 'networkLoad' in resource_report:
                    network_load = resource_report['networkLoad']
                    network_report_load.add_data(time, network_load)

            except json.decoder.JSONDecodeError:
                get_logger().warning("Problem reading %s, skipping", resource_summary_file)
                
    return node_name, network_summary_load, network_summary_demand, network_report_load, first_timestamp


def load_node_data(sim_output, all_services):
    """
    Arguments:
        sim_output(Path): path to the simulation output
        all_services(set): the services known to the simulation
    Returns:
        dict: node_name -> NodeNetworkData for network load from summary
        dict: node_name -> NodeNetworkData for network demand from summary
        dict: node_name -> NodeNetworkData for network load from report
        int: first timestamp
    """
    
    # node_name -> NodeNetworkData
    summary_data_load = dict()
    summary_data_demand = dict()
    report_data_load = dict()
    first_timestamp = None
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        (node_name, network_summary_load, network_summary_demand, network_report_load, node_first_timestamp) = process_node(all_services, node_dir)
        if node_name:
            summary_data_load[node_name] = network_summary_load
            summary_data_demand[node_name] = network_summary_demand
            report_data_load[node_name] = network_report_load
            if node_first_timestamp is not None:
                if first_timestamp is None or node_first_timestamp < first_timestamp:
                    first_timestamp = node_first_timestamp
            
    return summary_data_load, summary_data_demand, report_data_load, first_timestamp


def load_container_data(network_dir):
    frames = list()
    for node_file in network_dir.glob('*_container-data.csv'):
        df = pd.read_csv(node_file)
        frames.append(df)

    data = pd.concat(frames, ignore_index=True)
    data['time_minutes'] = (data['time'] - data['time'].min()) / 1000 / 60

    return data


def graph(output, first_timestamp, label, data_type, node_data):
    """
    Graph network data.
    
    Arguments:
        output(Path): output directory
        first_timestamp(int): first timestamp seen in the data
        label(str): "Summary" or "Report"
        label(str): "demand" or "load"
        node_data(dict): service -> NetworkData
    """
    
    for node_name, network_data in node_data.items():
        rx_frames = list()
        tx_frames = list()
        all_frames = list()
        
        rx_fig, rx_ax = map_utils.subplots()
        rx_ax.set_title(f"{node_name} {label} {data_type} RX data")
        rx_ax.set_xlabel("Time (minutes)")

        tx_fig, tx_ax = map_utils.subplots()
        tx_ax.set_title(f"{node_name} {label} {data_type} TX data")
        tx_ax.set_xlabel("Time (minutes)")

        all_fig, all_ax = map_utils.subplots()
        all_ax.set_title(f"{node_name} {label} {data_type} ALL data")
        all_ax.set_xlabel("Time (minutes)")

        max_minutes = 0
        rx_empty_plot = True
        tx_empty_plot = True
        all_empty_plot = True
        for service, service_data in network_data.service.items():
            if len(service_data.rx) > 0:
                rx_empty_plot = False
                rx_pairs = sorted(service_data.rx.items())
                rx_timestamps, rx_values = zip(*rx_pairs)
                rx_times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in rx_timestamps]
                rx_series_label = f"{service} RX"
                rx_ax.plot(rx_times, rx_values, label=rx_series_label)
                rx_frames.append(pd.DataFrame(list(rx_values), index=rx_times, columns=[rx_series_label]))
                max_minutes = max(max_minutes, max(rx_times))
                
            if len(service_data.tx) > 0:
                tx_empty_plot = False
                tx_pairs = sorted(service_data.tx.items())
                tx_timestamps, tx_values = zip(*tx_pairs)
                tx_times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in tx_timestamps]
                tx_series_label = f"{service} TX"
                tx_ax.plot(tx_times, tx_values, label=tx_series_label)
                tx_frames.append(pd.DataFrame(list(tx_values), index=tx_times, columns=[tx_series_label]))
                max_minutes = max(max_minutes, max(tx_times))
                                                                                     
            if len(service_data.all_traffic) > 0:
                all_empty_plot = False
                all_pairs = sorted(service_data.all_traffic.items())
                all_timestamps, all_values = zip(*all_pairs)
                all_times = [map_utils.timestamp_to_minutes(float(t) - first_timestamp) for t in all_timestamps]
                all_series_label = f"{service} ALL"
                all_ax.plot(all_times, all_values, label=all_series_label)
                all_frames.append(pd.DataFrame(list(all_values), index=all_times, columns=[all_series_label]))
                max_minutes = max(max_minutes, max(all_times))
                                                                                       
        if not rx_empty_plot:
            rx_ax.set_xlim(left=0, right=max_minutes)
            rx_handles, rx_labels = rx_ax.get_legend_handles_labels()
            rx_lgd = rx_ax.legend(rx_handles, rx_labels, bbox_to_anchor=(1.04, 1), loc="upper left")
            rx_output_name = output / f"network-{node_name}-{label}-RX-{data_type}.png"
            rx_fig.savefig(rx_output_name.as_posix(), format='png', bbox_extra_artists=(rx_lgd,), bbox_inches='tight')
            rx_df = pd.concat(rx_frames, axis=1)
            rx_df.to_csv(output / f"network-{node_name}-{label}-RX-{data_type}.csv", index_label="relative minutes")

        if not tx_empty_plot:
            tx_ax.set_xlim(left=0, right=max_minutes)
            tx_handles, tx_labels = tx_ax.get_legend_handles_labels()
            tx_lgd = tx_ax.legend(tx_handles, tx_labels, bbox_to_anchor=(1.04, 1), loc="upper left")
            tx_output_name = output / f"network-{node_name}-{label}-TX-{data_type}.png"
            tx_fig.savefig(tx_output_name.as_posix(), format='png', bbox_extra_artists=(tx_lgd,), bbox_inches='tight')
            tx_df = pd.concat(tx_frames, axis=1)
            tx_df.to_csv(output / f"network-{node_name}-{label}-TX-{data_type}.csv", index_label="relative minutes")

        if not all_empty_plot:
            all_ax.set_xlim(left=0, right=max_minutes)
            all_handles, all_labels = all_ax.get_legend_handles_labels()
            all_lgd = all_ax.legend(all_handles, all_labels, bbox_to_anchor=(1.04, 1), loc="upper left")
            all_output_name = output / f"network-{node_name}-{label}-ALL-{data_type}.png"
            all_fig.savefig(all_output_name.as_posix(), format='png', bbox_extra_artists=(all_lgd,), bbox_inches='tight')
            all_df = pd.concat(all_frames, axis=1)
            all_df.to_csv(output / f"network-{node_name}-{label}-ALL-{data_type}.csv", index_label="relative minutes")
            
        plt.close(rx_fig)
        plt.close(tx_fig)
        plt.close(all_fig)

        
def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    scenario_dir = Path(args.scenario)
    if not scenario_dir.exists():
        get_logger().error("%s does not exist", scenario_dir)
        return 1
    
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    all_services = map_utils.gather_all_services(scenario_dir)

    # node_name -> NodeNetworkData
    summary_data_load, summary_data_demand, report_data_load, first_timestamp = load_node_data(sim_output, all_services)

    if summary_data_load is not None and first_timestamp is not None:
        graph(output_dir, first_timestamp, "Summary", "Load", summary_data_load)
        
    if summary_data_demand is not None and first_timestamp is not None:
        graph(output_dir, first_timestamp, "Summary", "demand", summary_data_demand)

    if report_data_load is not None and first_timestamp is not None:
        graph(output_dir, first_timestamp, "Report", "Load", report_data_load)
        

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
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)
    parser.add_argument("--scenario", dest="scenario", help="Scenario directory (Required)", required=True)

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

