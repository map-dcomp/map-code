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

"""Graph the difference in time between the name of the output folder and
the timestamp inside the resource report.  Ideally this value should
be very close to zero. Large values suggest problems in the system
with creating resource reports.

Creates resource-report-time-lag.png

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

def output_graph(output, data, min_time):
    fig, ax = plt.subplots()
    ax.set_title("Resource report generation lag")
    ax.set_xlabel("Time (minutes)")
    ax.set_ylabel("Difference (seconds)")
    ax.grid(alpha=0.5, axis='y')

    for node_name, plot_data in data.items():
        (xs, ys) = plot_data
        minutes = [ map_utils.timestamp_to_minutes(x - min_time) for x in xs ]
        ax.scatter(minutes, ys, label=node_name, s=1)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")
        
    output_name = output / 'resource-report-time-lag.png'
    plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)
    
    
def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    # node_name -> (xs, ys)
    data = dict()
    min_time = None
    max_diff = None
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        
        get_logger().debug("Processing node %s", node_dir)
        node_name_dir = map_utils.find_ncp_folder(node_dir)
        if node_name_dir is None:
            get_logger().debug("No NCP folder found")
            continue

        xs = list()
        ys = list()
        for time_dir in node_name_dir.iterdir():
            if not time_dir.is_dir():
                continue

            directory_time = int(time_dir.stem)
            
            get_logger().debug("\t\tProcessing time %s", time_dir)
            resource_report_file = time_dir / 'resourceReport-SHORT.json'
            if resource_report_file.exists():
                with open(resource_report_file, 'r') as f:
                    resource_report = json.load(f)
                    
                report_time = int(resource_report['timestamp'])
                if report_time < 1:
                    # skip reports that we don't have a rational time for
                    continue
                
                # directory_time should be greater than or equal to report_time
                diff = map_utils.timestamp_to_seconds(directory_time - report_time)
                xs.append(directory_time)
                ys.append(diff)
                if min_time is None or directory_time < min_time:
                    min_time = directory_time
                if max_diff is None or diff > max_diff:
                    max_diff = diff
        data[node_dir.stem] = (xs, ys)
        get_logger().info("Maximum difference for %s is %f seconds", node_dir.stem, max_diff)

    output_graph(output, data, min_time)

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
