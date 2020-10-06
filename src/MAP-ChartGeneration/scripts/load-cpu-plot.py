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

"""
Graph each attribute for all NCPs running a service.
Creates load-per-server-{app}-{attr}.png.
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
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-c", "--chart_output", dest="chart_output", help="Chart output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--interactive", dest="interactive", action="store_true", help="If specified, display the plots")

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    
    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    output_dir = Path(args.output)
    
    load_dir = chart_output / 'load'
    apps = set()
    attributes = set()
    for f in load_dir.glob('ncp_load-*.csv'):
        match = re.match(r'ncp_load-(?P<app>.*)-(?P<attr>\S+)\.csv', f.name)
        if match:
            apps.add(match.group('app'))
            attributes.add(match.group('attr'))
        else:
            get_logger().debug("No match %s", f.name)

    get_logger().info("apps %s", apps)
    get_logger().info("attributes %s", attributes)

    for app in apps:
        for attr in attributes:
            load_file = chart_output / 'load' / 'ncp_load-{0}-{1}.csv'.format(app, attr)

            load_data = pd.read_csv(load_file, na_values="?")
            load_data.set_index('time')
            load_data['time_minutes'] = (load_data['time'] - load_data['time'].min()) / 1000 / 60

            fig, ax = plt.subplots()
            ax.set_title('service: {0} attribute: {1}'.format(app, attr))
            ax.set_xlabel('time (minutes)')
            ax.grid(alpha=0.5, axis='y')

            for col in load_data.columns.values:
                if 'time' != col and 'time_minutes' != col:
                    plt.plot(load_data['time_minutes'], load_data[col])

            handles, labels = ax.get_legend_handles_labels()
            lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

            output_name = output_dir / 'load-per-server-{0}-{1}.png'.format(app, attr)
            if args.interactive:
                plt.show()
            else:
                plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
                
            plt.close(fig)



if __name__ == "__main__":
    sys.exit(main())
    
