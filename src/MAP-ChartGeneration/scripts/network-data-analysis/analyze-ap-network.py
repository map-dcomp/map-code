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
    import numpy as np
    import pandas as pd
    from pathlib import Path

script_dir=os.path.abspath(os.path.dirname(__file__))

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

def gather_data(input_dir):
    '''
    @return a data frame with all AP node data
    '''

    frames = list()
    for child in input_dir.iterdir():
        get_logger().debug("Processing %s", child)
        match = re.match(r'(?P<node>[^_]+)_node-data.csv', child.name)
        if match:
            node = match.group('node')

            df = pd.read_csv(child)
            df['node'] = node
            frames.append(df)

    return pd.concat(frames, ignore_index=True)
        
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-i", "--input", dest="input", help="Chart directory to read from", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    input_dir = Path(args.input) / 'network'

    all_data = gather_data(input_dir)

    average_network_usage_per_node = all_data[all_data['service'] == 'AP'].groupby(['node']).mean().drop('time', axis=1)
    get_logger().info("Average AP network usage by server\n %s", average_network_usage_per_node)

    average_network_usage = average_network_usage_per_node.mean()
    get_logger().info("Average AP network usage across all servers\n %s", average_network_usage)

    total_ms = all_data.max()['time'] - all_data.min()['time']
    total_sec = total_ms / 1000
    total_min = total_sec / 60

    get_logger().info("Total runtime %f ms, %f sec, %f min", total_ms, total_sec, total_min)
    
        
if __name__ == "__main__":
    sys.exit(main())
    
