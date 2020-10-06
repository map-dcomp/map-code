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

script_dir = os.path.abspath(os.path.dirname(__file__))

rolling_average_window_size = 10


def get_logger():
    return logging.getLogger(__name__)


def setup_logging(
        default_path='logging.json',
        default_level=logging.INFO,
        env_key='LOG_CFG'
):
    """
    Setup logging configuration
    """
    path = default_path
    value = os.getenv(env_key, None)
    if value:
        path = value
    if os.path.exists(path):
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)


def process_nodes(interactive, output_dir, data):
    for node in data['node'].unique():
        node_name = node.replace(".map.dcomp", "")

        fig, ax = plt.subplots()
        ax.set_title('network traffic for {0}'.format(node_name))
        ax.set_ylabel('bandwidth (Mbps)')
        ax.set_xlabel('time (minutes)')

        node_data = data.loc[data['node'] == node]
        services = node_data['service'].unique()
        for service in services:
            plot_data = node_data.loc[node_data['service'] == service]

            for direction in ('RX', 'TX'):
                label = '{0}-{1}'.format(service, direction)
                ax.scatter(plot_data['time_minutes'], plot_data[direction], label=label, s=2)

        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

        if interactive:
            plt.show()
        else:
            output_name = output_dir / '{0}-network.png'.format(node_name)
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
        plt.close(fig)


def process_nodes_per_service(interactive, output_dir, data):
    for node in data['node'].unique():
        node_name = node.replace(".map.dcomp", "")

        node_data = data.loc[data['node'] == node]
        services = node_data['service'].unique()
        for service in services:
            fig, ax = plt.subplots()
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

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

            if interactive:
                plt.show()
            else:
                output_name = output_dir / '{0}-network-{1}.png'.format(node_name, service)
                plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)


def load_node_data(network_dir):
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
        
    node_data['time_minutes'] = (node_data['time'] - node_data['time'][0]) / 1000 / 60
    return node_data


def process_containers_per_service(interactive, output_dir, data):
    for container in data['container'].unique():
        name = container.replace(".map.dcomp", "")

        container_data = data.loc[data['container'] == container]
        services = container_data['service'].unique()
        for service in services:
            fig, ax = plt.subplots()
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

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

            if interactive:
                plt.show()
            else:
                output_name = output_dir / '{0}-network-{1}.png'.format(name, service)
                plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)


def process_containers(interactive, output_dir, data):
    for container in data['container'].unique():
        name = container.replace(".map.dcomp", "")

        fig, ax = plt.subplots()
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

        handles, labels = ax.get_legend_handles_labels()
        lgd = ax.legend(handles, labels, bbox_to_anchor=(1, 1))

        if interactive:
            plt.show()
        else:
            output_name = output_dir / '{0}-network.png'.format(name)
            plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
        plt.close(fig)


def load_container_data(network_dir):
    frames = list()
    for node_file in network_dir.glob('*_container-data.csv'):
        df = pd.read_csv(node_file)
        frames.append(df)

    data = pd.concat(frames, ignore_index=True)
    data['time_minutes'] = (data['time'] - data['time'].min()) / 1000 / 60

    return data


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-c", "--chart-output", dest="chart_output", help="Chart output directory (Required)",
                        required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--interactive", dest="interactive", action="store_true",
                        help="If specified, display the plots")

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    network_dir = chart_output / 'network'

    node_data = load_node_data(network_dir)
    if node_data is None:
        get_logger().error("No node data, cannot continue")
        return 1

    process_nodes(args.interactive, output_dir, node_data)
    process_nodes_per_service(args.interactive, output_dir, node_data)

    container_data = load_container_data(network_dir)

    process_containers(args.interactive, output_dir, container_data)
    process_containers_per_service(args.interactive, output_dir, container_data)


if __name__ == "__main__":
    sys.exit(main())

