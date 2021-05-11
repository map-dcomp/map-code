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
Creates:
  * [node or container]-network.png
  * [node or container]-network-[service].png
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
    import pandas as pd
    from pathlib import Path
    import numpy as np
    from numpy.polynomial.polynomial import Polynomial
    sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
    import map_utils
    import multiprocessing
    import math

script_dir = os.path.abspath(os.path.dirname(__file__))

rolling_average_window_size = 10


def get_logger():
    return logging.getLogger(__name__)


def process_node(output_dir, data, node):
    """
    Arguments:
        output_dir(Path): base directory for output
        data(DataFrame): the data to process
        node(str): name of the node to process
    """

    node_name = node.replace(".map.dcomp", "")

    directions = ['RX', 'TX']
    
    max_minutes = math.ceil(data['time_minutes'].max())
    
    try:
        fig, ax = map_utils.subplots()
        ax.set_title('network traffic for {0}'.format(node_name))
        ax.set_ylabel('bandwidth (Mbps)')
        ax.set_xlabel('time (minutes)')

        ydata = dict()
        node_data = data.loc[data['node'] == node]
        services = node_data['service'].unique()
        for service in services:
            plot_data = node_data.loc[node_data['service'] == service]

            for direction in directions:
                label = '{0}-{1}'.format(service, direction)
                ax.scatter(plot_data['time_minutes'], plot_data[direction], label=label, s=2)

                service_data = plot_data.drop(columns='time').set_index('time_minutes')[direction].rename(f"{service} {direction}")
                yd = ydata.get(direction, list())
                yd.append(service_data)
                ydata[direction] = yd

        ax.set_xlim(left=0, right=max_minutes)
        
        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))
        
        output_name = output_dir / '{0}-network.png'.format(node_name)
        plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
        plt.close(fig)

        for direction in directions:
            yd = ydata[direction]
            ydata_combined = pd.concat(yd, axis=1).fillna(0)
            fig_stacked, ax_stacked = map_utils.subplots()
            ax_stacked.set_title(f'{direction} network traffic for {node_name}')
            ax_stacked.set_ylabel('bandwidth (mbps)')
            ax_stacked.set_xlabel('time (minutes)')
            ax_stacked.stackplot(ydata_combined.index.values, ydata_combined.T, labels=ydata_combined.columns.values)

            ax_stacked.set_xlim(left=0, right=max_minutes)
            
            handles_stacked, labels_stacked = ax_stacked.get_legend_handles_labels()
            lgd_stacked = ax_stacked.legend(handles_stacked, labels_stacked, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name_stacked = output_dir / f"{node_name}-network_stacked_{direction}.png"
            fig_stacked.savefig(output_name_stacked.as_posix(), format='png', bbox_extra_artists=(lgd_stacked,), bbox_inches='tight')
            plt.close(fig_stacked)
    except:
        get_logger().exception("Unexpected error")


def process_node_per_service(output_dir, data, node):
    """
    Arguments:
        output_dir(Path): base directory for output
        data(DataFrame): data to process
        node(str): node to process
    """

    max_minutes = math.ceil(data['time_minutes'].max())
    
    try:
        node_name = node.replace(".map.dcomp", "")

        node_data = data.loc[data['node'] == node]
        services = node_data['service'].unique()
        for service in services:
            fig, ax = map_utils.subplots()
            ax.set_title('network traffic for {0} - {1}'.format(node_name, service))
            ax.set_ylabel('bandwidth (Mbps)')
            ax.set_xlabel('time (minutes)')

            plot_data = node_data.loc[node_data['service'] == service].copy()
            for direction in ('RX', 'TX'):
                label = '{0}-{1}'.format(service, direction)
                ax.scatter(plot_data['time_minutes'], plot_data[direction], label=label, s=2)

                plot_data['RX_rolling_average'] = plot_data['RX'].rolling(window=rolling_average_window_size).mean()
                plot_data['TX_rolling_average'] = plot_data['TX'].rolling(window=rolling_average_window_size).mean()

                direction_avg = '{0}_rolling_average'.format(direction)
                x = plot_data['time_minutes']
                y = plot_data[direction_avg]
                ax.plot(x, y, label='{0} trend'.format(direction))
                
            ax.set_xlim(left=0, right=max_minutes)

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

            output_name = output_dir / '{0}-network-{1}.png'.format(node_name, service)
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
    except:
        get_logger().exception("Unexpected error")


def load_node_data(network_dir, min_time):
    node_frames = list()
    for node_file in network_dir.glob('*_node-data.csv'):
        node_name = re.match(r'(.*)_node-data.csv', node_file.name).group(1)

        df = pd.read_csv(node_file)
        df['node'] = node_name
        node_frames.append(df)

    if len(node_frames) < 1:
        get_logger().warning("No node data found")
        return None
    
    node_data = pd.concat(node_frames, ignore_index=True)
    if node_data.size < 1:
        get_logger().warning("No node data found")
        return None
        
    node_data['time_minutes'] = (node_data['time'] - min_time) / 1000 / 60
    return node_data


def process_container_per_service(output_dir, data, container):
    """
    Arguments:
        output_dir(Path): base directory for output
        data(DataFrame): data to process
        container(str): container to process
    """

    max_minutes = math.ceil(data['time_minutes'].max())
    
    try:
        name = container.replace(".map.dcomp", "")

        container_data = data.loc[data['container'] == container]
        services = container_data['service'].unique()
        for service in services:
            fig, ax = map_utils.subplots()
            ax.set_title('network traffic for {0} - {1}'.format(name, service))
            ax.set_ylabel('bandwidth (Mbps)')
            ax.set_xlabel('time (minutes)')

            plot_data = container_data.loc[data['service'] == service].copy()

            for direction in ('RX', 'TX'):
                label = '{0}-{1}'.format(service, direction)
                ax.scatter(plot_data['time_minutes'], plot_data[direction], label=label, s=2)

                plot_data['RX_rolling_average'] = plot_data['RX'].rolling(window=rolling_average_window_size).mean()
                plot_data['TX_rolling_average'] = plot_data['TX'].rolling(window=rolling_average_window_size).mean()

                direction_avg = '{0}_rolling_average'.format(direction)
                x = plot_data['time_minutes']
                y = plot_data[direction_avg]
                ax.plot(x, y, label='{0} trend'.format(direction))

            ax.set_xlim(left=0, right=max_minutes)
                
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

            output_name = output_dir / '{0}-network-{1}.png'.format(name, service)
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
    except:
        get_logger().exception("Unexpected error")


def process_container(output_dir, data, container):
    """
    Arguments:
        output_dir(Path): base directory for output
        data(DataFrame): data to process
        container(str): container to process
    """

    max_minutes = math.ceil(data['time_minutes'].max())
    
    try:
        name = container.replace(".map.dcomp", "")

        fig, ax = map_utils.subplots()
        ax.set_title('network traffic for {0}'.format(name))
        ax.set_ylabel('bandwidth (Mbps)')
        ax.set_xlabel('time (minutes)')

        container_data = data.loc[data['container'] == container]
        services = container_data['service'].unique()
        for service in services:
            plot_data = container_data.loc[data['service'] == service]
            for direction in ('RX', 'TX'):
                label = '{0}-{1}'.format(service, direction)
                ax.scatter(plot_data['time_minutes'], plot_data[direction], label=label, s=2)

        ax.set_xlim(left=0, right=max_minutes)
                
        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

        output_name = output_dir / '{0}-network.png'.format(name)
        plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
        plt.close(fig)
    except:
        get_logger().exception("Unexpected error")


def load_container_data(network_dir, min_time):
    frames = list()
    for node_file in network_dir.glob('*_container-data.csv'):
        df = pd.read_csv(node_file)
        frames.append(df)

    data = pd.concat(frames, ignore_index=True)
    data['time_minutes'] = (data['time'] - min_time) / 1000 / 60

    return data


def main_method(args):
    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    min_time = first_timestamp.timestamp() * 1000
    
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    network_dir = chart_output / 'network'

    node_data = load_node_data(network_dir, min_time)
    if node_data is None:
        get_logger().error("No node data, cannot continue")
        return 1

    container_data = load_container_data(network_dir, min_time)
    
    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()

        for node in node_data['node'].unique():
            results.append(pool.apply_async(func=process_node, args=[output_dir, node_data, node]))
            results.append(pool.apply_async(func=process_node_per_service, args=[output_dir, node_data, node]))
        for container in container_data['container'].unique():
            results.append(pool.apply_async(func=process_container, args=[output_dir, container_data, container]))
            results.append(pool.apply_async(func=process_container_per_service, args=[output_dir, container_data, container]))

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
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("--debug", dest="debug", help="Enable interactive debugger on error", action='store_true')
    parser.add_argument("-c", "--chart-output", dest="chart_output", help="Chart output directory (Required)",
                        required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
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

