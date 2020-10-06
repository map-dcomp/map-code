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
#!/usr/bin/env python3.6

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

def parse_file(logfile):
    # 424080 [clientPoolA-simulator] INFO com.bbn.map.simulator.ClientSim [1 of 125] {}-   request for app2 goes to nodeA8_c00

    # indexed by client
    start_time = {}
    durations = {}
    with open(logfile) as f:
        for line in f:
            match = re.match(r'(?P<time>\d+)\s+\[(?P<client>[^]]+)\].*\[(?P<request>\d+) of (?P<total>\d+)\]', line)
            if match:
                request = int(match.group('request'))
                total = int(match.group('total'))
                time = int(match.group('time'))
                client = match.group('client')
                
                if request == 1:
                    start_time[client] = time
                elif request == total:
                    # found last request, compute time
                    duration = time - start_time[client]
                    if client not in durations:
                        durations[client] = []
                    durations[client].append(duration)
    return durations 

def compute_stats(durations):
    sums = {}
    counts = {}
    for (client, dur) in durations.items():
        for d in dur:
            sums[client] = sums.get(client, 0) + d
            counts[client] = counts.get(client, 0) + 1

    for (client, sum) in sums.items():
        count = counts[client]
        avg = float(sum) / float(count)
        get_logger().info("Average for {} is {}".format(client, avg))
        
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-i", "--input", dest="input", help="file to read", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    durations = parse_file(args.input)
    compute_stats(durations)
        
if __name__ == "__main__":
    sys.exit(main())
    
