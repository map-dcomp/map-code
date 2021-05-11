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
Graph the inferred demand information created by inferred_demand_to_csv.py.

Output to {output_dir}/inferred-demand/{ncp}-{service}={attr}.png
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
    import pandas as pd
    import matplotlib.pyplot as plt
    
script_dir=Path(__file__).parent.absolute()

def get_logger():
    return logging.getLogger(__name__)


def process_file(first_timestamp_ms, output, csv_file):
    ncp = csv_file.stem

    get_logger().debug("Processing %s ncp: %s", csv_file, ncp)
    
    df = pd.read_csv(csv_file)
    df['minutes'] = (df['timestamp'] - first_timestamp_ms) / 1000 / 60
    df = df.sort_values(by=['minutes'])
    
    max_minutes = df['minutes'].max()
    services = df['service'].unique()
    source_regions = df['source region'].unique()
    attributes = df['attribute'].unique()

    xdata = df['minutes'].unique()
    for service in sorted(services):
        get_logger().debug("Service: %s", service)
        for attr in attributes:
            get_logger().debug("attribute: %s", attr)
            
            fig, ax = map_utils.subplots()
            ax.set_title(f"Inferred demand for {service} and {attr} on {ncp}")
            ax.set_xlabel('time (minutes)')
            ax.set_xlim(left=0, right=max_minutes)

            ydata = list()
            labels = list()
            for source_region in sorted(source_regions):
                get_logger().debug("source region: %s", source_region)
                
                plot_data = df.loc[ (df['service'] == service) & (df['source region'] == source_region) & (df['attribute'] == attr)]
                label = source_region
                time_data = pd.Series(plot_data['value'].values, index=plot_data['minutes']).to_dict()
                yfilled = map_utils.fill_missing_times(xdata, time_data)
                
                get_logger().debug("yseries len: %d", len(yfilled))
                ydata.append(yfilled)
                labels.append(label)

            get_logger().debug("xdata len: %d", len(xdata))
            get_logger().debug("ydata len: %d", len(ydata))
            ax.stackplot(xdata, ydata, labels=labels)
            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output / f"{ncp}-{service}-{attr}.png"
            fig.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
            plt.close(fig)
            
            
        

def main_method(args):
    with open(args.first_timestamp_file) as f:
        ts_str = f.readline().strip()
        first_timestamp = map_utils.log_timestamp_to_datetime(ts_str)
    first_timestamp_ms = first_timestamp.timestamp() * 1000
    get_logger().info("Simulation started at %s -> %d", first_timestamp, first_timestamp_ms)
    
    output = Path(args.output) / 'inferred-demand'
    if not output.exists():
        get_logger().error("%s does not exist and is required to read the CSV files", output)


    for csv_file in output.glob('*.csv'):
        process_file(first_timestamp_ms, output, csv_file)


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
