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

"""Create client_demand.png showing how many clients are active at
any point during the run.
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

script_dir = os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-c", "--chart-output", dest="chart_output", help="Output of MAPChartGeneration(Required)",
                        required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)
    parser.add_argument("--interactive", dest="interactive", action="store_true",
                        help="If specified, display the plots")

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    chart_output = Path(args.chart_output)
    if not chart_output.exists():
        get_logger().error("%s does not exist", chart_output)
        return 1

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    client_demand_dir = chart_output / 'client_demand'

    frames = list()
    for file in client_demand_dir.glob('num_clients-*.csv'):
        match = re.match(r'^num_clients-(.*)\.csv$', file.name)
        if not match:
            continue
        service = match.group(1)
        df =  pd.read_csv(file)
        df['service'] = service
        frames.append(df)

    data = pd.concat(frames, ignore_index=True)
    data['time_minutes'] = (data['time'] - data['time'].min()) / 1000 / 60

    all_services = data['service'].unique()

    fig, ax = map_utils.subplots()
    ax.set_title('number of clients per service over time')
    ax.set_ylabel('number of clients')
    ax.set_xlabel('time (minutes)')

    for service in all_services:
        plot_data = data.loc[(data['service'] == service)]
        ax.step(plot_data['time_minutes'], plot_data['num clients'], where='post', label=service)

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    if args.interactive:
        plt.show()
    else:
        output_name = output_dir / 'client_demand.png'
        plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')

    plt.close(fig)

if __name__ == "__main__":
    sys.exit(main())

