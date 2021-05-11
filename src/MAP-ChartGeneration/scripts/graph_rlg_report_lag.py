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
Graph the difference in time between the name of the output folder
and the timestamp inside the resource reports found in
regionResourceReports-SHORT.json.  Ideally this value should be very
close to zero. Large values suggest problems in transmitting resource
reports to the RLG.

Creates rlg-node-resource-report-time-lag_{ncp}.png

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
    import matplotlib.pyplot as plt
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)

def output_graph(output, ncp, xs, ys, first_timestamp_ms):
    """
    Arguments:
        output(Path): output directory
        ncp(str): name of the NCP the graph is for
        xs: list of x-values
        ys(dict): neighbor to y-values
        first_timestamp_ms(int): initial timestamp to subtract from values
    """
    fig, ax = map_utils.subplots()
    ax.set_title(f"RLG Resource report lag for node {ncp}")
    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Difference (seconds)")


    minutes = [ map_utils.timestamp_to_minutes(x - first_timestamp_ms) for x in xs ]
    max_minutes = max(minutes)
    for node_name, plot_data in ys.items():
        get_logger().debug("Graphing NCP %s with neighbor %s. x.len: %d y.len: %d", ncp, node_name, len(minutes), len(plot_data))
        ax.scatter(minutes, plot_data, label=node_name, s=1)

    ax.set_xlim(left=0, right=max_minutes)
    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
        
    output_name = output / f'rlg-node-resource-report-time-lag_{ncp}.png'
    plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    
    
def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    first_timestamp_ms = first_timestamp.timestamp() * 1000
    get_logger().info("Simulation started at %s -> %d", first_timestamp, first_timestamp_ms)
    
    output = Path(args.output) / 'rlg-resource-report-lag'
    output.mkdir(parents=True, exist_ok=True)

    for node_dir in sim_output.iterdir():
        max_diff = None
        if not node_dir.is_dir():
            continue
        
        get_logger().debug("Processing node %s", node_dir)
        node_name_dir = map_utils.find_ncp_folder(node_dir)
        if node_name_dir is None:
            get_logger().debug("No NCP folder found")
            continue
        ncp = map_utils.node_name_from_dir(node_name_dir)

        all_known_nodes = set()
        xs = list()
        ys = dict() # report node -> series of diffs
        for time_dir in sorted(node_name_dir.iterdir()):
            if not time_dir.is_dir():
                continue

            directory_time = int(time_dir.stem)
            
            get_logger().debug("\t\tProcessing time %s", time_dir)
            resource_report_file = time_dir / 'regionResourceReports-SHORT.json'
            if resource_report_file.exists():
                try:
                    with open(resource_report_file, 'r') as f:
                        resource_reports = json.load(f)
                except json.decoder.JSONDecodeError:
                    get_logger().warning("Problem reading %s, skipping", resource_report_file)
                    continue

                xs.append(directory_time)

                seen_nodes = set()
                for resource_report in resource_reports:
                    report_time = int(resource_report['timestamp'])
                    if report_time < 1:
                        # skip reports that we don't have a rational time for
                        diff = None
                    else :
                        diff = map_utils.timestamp_to_seconds(directory_time - report_time)
                    node = resource_report['nodeName']['name']

                    if node in seen_nodes:
                        get_logger().warning("Saw multiple reports from %s in %s, skipping the second one", node, time_dir)
                        continue
                    
                    seen_nodes.add(node)
                    all_known_nodes.add(node)

                    # default value is setup to ensure that newly discovered
                    # nodes have a list of values the same length as the other lists
                    node_series = ys.get(node, [None] * (len(xs) - 1))
                    node_series.append(diff)
                    ys[node] = node_series
                    get_logger().debug("Added %s to %s xs: %d node_series: %d", diff, node, len(xs), len(node_series))

                    if max_diff is None or diff > max_diff:
                        max_diff = diff

                # make sure we skip values for any nodes that we should have seen
                missing_nodes = all_known_nodes - seen_nodes
                if len(missing_nodes) > 0:
                    get_logger().debug("Missing nodes: %s Seen: %s", missing_nodes, seen_nodes)
                    for missing_node in missing_nodes:
                        # the key must exist by now
                        node_series = ys[missing_node]
                        node_series.append(None)
                        ys[missing_node] = node_series
                        get_logger().debug("Added None to %s", missing_node)

                for node, node_series in ys.items():
                    if len(xs) != len(node_series):
                        raise RuntimeError(f"List sizes not correct for {node} {len(xs)} != {len(node_series)}")

        if len(xs) > 0:
            get_logger().info("Maximum diff for %s is %s ms", ncp, max_diff)
            output_graph(output, ncp, xs, ys, first_timestamp_ms)


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
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--first-timestamp-file", dest="first_timestamp_file", help="Path to file containing the log timestamp that the simulation started", required=True)
    
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
