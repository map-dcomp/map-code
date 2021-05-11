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
Calculates the area under a load curve as a measure of work done.

Accepts CSV input files of format:
"relative minutes", [value 1], [value 2], ...

Outputs change in time from start to finish, area under the curve (total work done) for each value, and area/time (average work done per minute) for each value.
"""

import warnings
with warnings.catch_warnings():
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    from pathlib import Path
    import csv
    import pytz
    import map_utils

script_dir=(Path(__file__).parent).resolve()


def get_logger():
    return logging.getLogger(__name__)




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
    parser.add_argument("-i", "--input-file", dest="load_capacity_csv_file", help="Path to the input load-[attribute]-[region].csv file", required=True)


    args = parser.parse_args(argv)
    map_utils.setup_logging(default_path=args.logconfig)



    load_capacity_csv_file = Path(args.load_capacity_csv_file)


    with open(load_capacity_csv_file) as f:
        reader = csv.DictReader(f)

        areas = dict()

        start_time = None
        end_time = None

        prev_row = None

        prev_row_values = dict()
        
        for row in reader:
            get_logger().debug(f"row: {row}")
            current_time = float(row['relative minutes'])
            if not start_time:
                start_time = current_time
            end_time = current_time

            if prev_row:
                prev_time = float(prev_row['relative minutes'])
                delta_time = current_time - prev_time
                average_values_for_delta = dict()
                
                for label in row.keys():
                    if label != 'relative minutes':
                        if label in prev_row_values:
                            prev_value = float(prev_row_values[label])
                            current_value = float(row[label]) if row[label] != '' else prev_value
                            average_values_for_delta[label] = (current_value + prev_value) / 2

                for label in average_values_for_delta.keys():
                    value = average_values_for_delta[label]

                    if label not in areas:
                        areas[label] = 0
                    areas[label] += value * delta_time



            for label in row.keys():
                if label != 'relative minutes':
                    if row[label] != '':
                        prev_row_values[label] = row[label]
            get_logger().debug(f"prev_row_values: {prev_row_values}")
            prev_row = row




        total_delta_time = end_time - start_time
        get_logger().info(f"time: {total_delta_time}")

        labels = list(areas.keys())
        labels.sort()

        capacity_sum_label = ""
        capacity_sum = 0

        load_sum_label = ""
        load_sum = 0

        for label in labels:
            value = areas[label]
            value_per_time = value / total_delta_time

            get_logger().info(f"{label}")
            get_logger().info(f"   area: {value}")
            get_logger().info(f"   area / time: {value_per_time} = {value} / {total_delta_time}")

            if "capacity" in label:
                if not capacity_sum_label == "":
                    capacity_sum_label += " + "
                capacity_sum_label += label
                capacity_sum += value
            else:
                if not load_sum_label == "":
                    load_sum_label += " + "
                load_sum_label += label
                load_sum += value


        get_logger().info(f"{capacity_sum_label}")
        get_logger().info(f"   area: {capacity_sum}")
        get_logger().info(f"   area / time: {capacity_sum / total_delta_time} = {capacity_sum} / {total_delta_time}")

        get_logger().info(f"{load_sum_label}")
        get_logger().info(f"   area: {load_sum}")
        get_logger().info(f"   area / time: {load_sum / total_delta_time} = {load_sum} / {total_delta_time}")




if __name__ == "__main__":
    sys.exit(main())
