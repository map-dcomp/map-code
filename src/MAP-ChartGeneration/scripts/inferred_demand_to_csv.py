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
Create a CSV file for each node that has total demand information.
Output to {output_dir}/inferred-demand/{node}.csv

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
    import csv
    import multiprocessing
    
script_dir=Path(__file__).parent.absolute()

def get_logger():
    return logging.getLogger(__name__)


def process_file(writer, timestamp, total_demand_file):
    """
    Arguments:
        writer(csv.writer): where to write the information
        timestamp(int): timestamp of the output directory
        total_demand_file(Path): file to process
    Returns:
        boolean: True if something was written to writer, otherwise False
    """
    
    data_written = False
    try:
        with open(total_demand_file, 'r') as f:
            total_demand = json.load(f)
            
        for app_coordinates, demand in total_demand.items():
            service = map_utils.get_service_artifact(app_coordinates)
            if service is None:
                get_logger().warning("Problem reading %s, unable to find service artifact in '%s' skipping service data", total_demand_file, app_coordinates)
                continue

            for _, service_data in demand.items():
                for source_region, source_data in service_data.items():
                    for attr, value in source_data.items():
                        writer.writerow([timestamp, service, source_region, attr, value])
                        data_written = True
                
        return data_written         
    except json.decoder.JSONDecodeError:
        get_logger().warning("Problem reading %s, skipping", total_demand_file)
        return False


def process_node(output_dir, node_dir):
    ncp = map_utils.node_name_from_dir(node_dir)

    data_written = False
    output_file = output_dir / f"{ncp}.csv"
    with open(output_file, 'w') as f:
        writer = csv.writer(f)
        writer.writerow(['timestamp', 'service', 'source region', 'attribute', 'value'])
        
        for time_dir in node_dir.iterdir():
            if not time_dir.is_dir():
                continue

            timestamp = int(time_dir.stem)

            total_demand_file = time_dir / 'totalDemand.json'
            if total_demand_file.exists():
                if process_file(writer, timestamp, total_demand_file):
                    data_written = True

    if not data_written:
        # no data for this node
        get_logger().debug("No total demand data for %s", ncp)
        output_file.unlink()


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    output = Path(args.output) / 'inferred-demand'
    output.mkdir(parents=True, exist_ok=True)

    with multiprocessing.Pool(processes=os.cpu_count()) as pool:
        results = list()
    
        for node_dir in sim_output.iterdir():
            if not node_dir.is_dir():
                continue

            node_name_dir = map_utils.find_ncp_folder(node_dir)
            if node_name_dir is None:
                get_logger().debug("No NCP folder found in %s", node_name_dir)
                continue

            results.append(pool.apply_async(func=process_node, args=[output, node_name_dir]))

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
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("--debug", dest="debug", help="Enable interactive debugger on error", action='store_true')
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Sim output directory (Required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output directory (Required)", required=True)

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
