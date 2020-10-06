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
    import numpy as np
    import pandas as pd
    from pathlib import Path
    import map_utils

script_dir=os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


def plot_region_service(output_dir, interactive, dcop_plans, rlg_plans, all_regions, region, service):
    dcop_plot_data = dcop_plans.loc[(dcop_plans['plan_region'] == region) & (dcop_plans['service'] == service)]
    rlg_plot_data = rlg_plans.loc[(rlg_plans['plan_region'] == region) & (rlg_plans['service'] == service)]

    fig, ax = plt.subplots()
    ax.set_title('Overflow plans for service {} in region {}'.format(service, region))
    ax.set_ylabel('overflow percentage')
    ax.set_xlabel('time (minutes)')
    ax.grid(alpha=0.5, axis='y')

    for overflow_region in all_regions:
        plt.plot(dcop_plot_data['time_minutes'], dcop_plot_data[overflow_region],
                 label="{0} DCOP".format(overflow_region))
        plt.plot(rlg_plot_data['time_minutes'], rlg_plot_data[overflow_region], label="{0} RLG".format(overflow_region))

    handles, labels = ax.get_legend_handles_labels()
    lgd = ax.legend(handles, labels, bbox_to_anchor=(1.04, 1), loc="upper left")

    if interactive:
        plt.show()
    else:
        output_name = output_dir / 'overflow_analysis-{0}-{1}.png'.format(region, service)
        plt.savefig(output_name.as_posix(), format='png', bbox_extra_artists=(lgd,), bbox_inches='tight')
    plt.close(fig)


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
    output_dir.mkdir(parents=True, exist_ok=True)

    dcop_file = chart_output / 'dcop_plan_updates/all_dcop_plans.csv'
    dcop_plans = pd.read_csv(dcop_file, na_values="?")
    dcop_plans = dcop_plans.sort_values(by=['time'])

    rlg_file = chart_output / 'rlg_plan_updates/all_rlg_overflow_plans.csv'
    rlg_plans = pd.read_csv(rlg_file, na_values="?")
    rlg_plans = rlg_plans.sort_values(by=['time'])

    earliest_time = None
    if not dcop_plans.empty:
        earliest_time = dcop_plans['time'].min()

    if not rlg_plans.empty:
        if earliest_time is None:
            earliest_time = rlg_plans['time'].min()
        else:
            earliest_time = min(earliest_time, rlg_plans['time'].min())

    dcop_plans['time_minutes'] = (dcop_plans['time'] - earliest_time) / 1000 / 60
    rlg_plans['time_minutes'] = (rlg_plans['time'] - earliest_time) / 1000 / 60

    all_regions = np.union1d(dcop_plans['plan_region'].unique(), rlg_plans['plan_region'].unique())
    all_services = np.union1d(dcop_plans['service'].unique(), rlg_plans['service'].unique())

    for region in all_regions:
        for service in all_services:
            plot_region_service(output_dir, args.interactive, dcop_plans, rlg_plans, all_regions, region, service)


if __name__ == "__main__":
    sys.exit(main())
    
