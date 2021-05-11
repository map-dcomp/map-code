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

def compute_base_values(input_demand):
    base_containers = None
    base_tx = None
    base_rx = None
    for request in input_demand:
        containers = None
        tx = None
        rx = None
        if 'nodeLoad' in request:
            if 'TASK_CONTAINERS' in request['nodeLoad']:
                containers = float(request['nodeLoad']['TASK_CONTAINERS'])
        if 'networkLoad' in request:
            if 'DATARATE_TX' in request['networkLoad']:
                tx = float(request['networkLoad']['DATARATE_TX'])
            if 'DATARATE_RX' in request['networkLoad']:
                rx = float(request['networkLoad']['DATARATE_RX'])
        if containers is not None and tx is not None and rx is not None:
            if base_containers is None:
                base_containers = containers
                base_tx = tx
                base_rx = rx
            elif containers < base_containers:
                base_containers = containers
                base_tx = tx
                base_rx = rx
    
    return base_containers, base_tx, base_rx


def scale_request(base_containers, base_tx, base_rx, request):
    containers = None
    if 'nodeLoad' in request:
        if 'TASK_CONTAINERS' in request['nodeLoad']:
            containers = float(request['nodeLoad']['TASK_CONTAINERS'])
            multiplier = containers / base_containers
            if 'networkLoad' in request:
                if 'DATARATE_TX' in request['networkLoad']:
                    tx = float(request['networkLoad']['DATARATE_TX'])
                    request['networkLoad']['DATARATE_TX'] = tx * multiplier
                if 'DATARATE_RX' in request['networkLoad']:
                    rx = float(request['networkLoad']['DATARATE_RX'])
                    request['networkLoad']['DATARATE_RX'] = rx * multiplier


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-i", "--input", dest="input", help="input directory (required)", required=True)
    parser.add_argument("-o", "--output", dest="output", help="output directory (required)", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    input_dir = Path(args.input)
    if not input_dir.exists():
        get_logger().error("%s does not exist", input_dir)
        return 1

    output_dir = Path(args.output)

    if not output_dir.exists():
        output_dir.mkdir(parents=True)

    if input_dir.samefile(output_dir):
        get_logger().error("Input and output cannot be the same directory")
        return 1
    
    for input_file in input_dir.glob('*.json'):
        output_file = output_dir / input_file.name
        
        with open(input_file, 'r') as f:
            input_demand = json.load(f)

        # compute minimum TASK_CONTAINERS and associated network
        (base_containers, base_tx, base_rx) = compute_base_values(input_demand)
        for request in input_demand:
            scale_request(base_containers, base_tx, base_rx, request)
            
        with open(output_file, 'w') as f:
            json.dump(input_demand, f, indent=2)
        
    
        
if __name__ == "__main__":
    sys.exit(main())
    
