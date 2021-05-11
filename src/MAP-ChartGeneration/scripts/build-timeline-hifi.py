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
Build a timeline of important events from the output of a hifi run. 
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
    import datetime
    import map_utils
    import fileinput

script_dir=os.path.abspath(os.path.dirname(__file__))

TIMESTAMP_RE = re.compile(r'^(?P<timestamp>\d+\-\d+\-\d+/\d+:\d+:\d+\S+)\s+(?P<message>.*)$')
OOM_RE = re.compile(r'OutOfMemoryError')

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


def parse_timestamp(timestamp):
    """
    Arguments:
        timestamp (str): timestamp in MAP log format
    Returns:
        datetime: parsed datetime object or None on error
    """
    try:
        return datetime.datetime.strptime(timestamp, '%Y-%m-%d/%H:%M:%S.%f/%z')
    except ValueError:
        return None


def parse_sim_log(log_directory, node_name):
    """
    Arguments:
        log_directory (Path): directory to find the log files
        node_name (str): name of the node
    Returns:
        list<str>: lines to add to the timeline
    """
    lines = list()
    
    first_line = True
    last_timestamp = None
    apps = set()
    logfiles = sorted(log_directory.glob("map-sim*.log"), key=lambda f: f.stat().st_mtime)
    with fileinput.input(files=logfiles) as f:
        for line in f:
            match = TIMESTAMP_RE.match(line)
            if match:
                timestamp = match.group('timestamp')
                last_timestamp = timestamp

                if first_line:
                    lines.append(f"{timestamp} simulation driver started")
                    first_line = False

            if re.search(r'Starting simulation driver', line):
                lines.append(f"{last_timestamp} Starting the simulation - sending start commands to all nodes")

            failure_match = re.search(r'Simulating failure on node (\S+)', line)
            if failure_match:
                failed_node = failure_match.group(1)
                lines.append(f"{last_timestamp} simulating failure on {failed_node}")
                
            if OOM_RE.search(line):
                lines.append(f"{last_timestamp} OOM on {node_name}")

                
    if last_timestamp:
        lines.append(f"{timestamp} simulation driver finished")
        
    return lines

        
def parse_client_log(log_directory, node_name):
    """
    Arguments:
        log_directory (Path): directory to find the log files
        node_name (str): name of the node
    Returns:
        list<str>: lines to add to the timeline
    """
    lines = list()
    first_line = True
    last_timestamp = None
    apps = set()
    logfiles = sorted(log_directory.glob("map-client*.log"), key=lambda f: f.stat().st_mtime)
    with fileinput.input(files=logfiles) as f:
        for line in f:
            match = TIMESTAMP_RE.match(line)
            if match:
                timestamp = match.group('timestamp')
                last_timestamp = timestamp
                raw_message = match.group('message')
            
                if first_line:
                    lines.append(f"{last_timestamp} client on {node_name} started")
                    first_line = False

            apply_match = re.search(r'Applying client request:.*AppCoordinates {\S+, (\S+), \S+}', line)
            if apply_match:
                app = apply_match.group(1)
                if app not in apps:
                    apps.add(app)
                    lines.append(f"{last_timestamp} first request for {app} from {node_name}")

            if OOM_RE.search(line):
                lines.append(f"{last_timestamp} OOM on {node_name}")
                    
    if last_timestamp:
        lines.append(f"{last_timestamp} client on {node_name} finished")

    return lines

                
def parse_agent_log(log_directory, node_name):
    """
    Arguments:
        log_directory (Path): directory to find the agent log files
        node_name (str): name of the node
    Returns:
        list<str>: lines to put in the timeline
    """
    lines = list()

    rlg_sum = 0
    rlg_count = 0
    dcop_sum = 0
    dcop_count = 0
    rlg_prev_timestamp = None
    rlg_start_timestamp = None
    dcop_prev_timestamp = None
    dcop_start_timestamp = None
    first_line = True
    last_timestamp = None
    logfiles = sorted(log_directory.glob("map-agent*.log"), key=lambda f: f.stat().st_mtime)
    with fileinput.input(files=logfiles) as f:
        for line in f:
            match = TIMESTAMP_RE.match(line)
            if match:
                timestamp = match.group('timestamp')
                last_timestamp = timestamp
                raw_message = match.group(2)
            
                if first_line:
                    lines.append(f"{timestamp} agent on {node_name} started")
                    first_line = False

                # find any INFO log statement from DCOP
                dcop_match = re.match(r'^\[DCOP\-([^.]+).*\]\s+INFO\s+(.*)$', raw_message)
                if dcop_match:
                    message = dcop_match.group(2)
                    if re.search(r'\sUsing algorithm\s', message):
                        if dcop_prev_timestamp:
                            if not dcop_start_timestamp:
                                get_logger().error("Found end of DCOP without start of DCOP on node %s file %s line %d", node_name, f.filename(), f.filelineno())
                            else:
                                start_ts = parse_timestamp(dcop_start_timestamp)
                                end_ts = parse_timestamp(dcop_prev_timestamp)
                                delta = end_ts - start_ts
                                compute_ms = delta / datetime.timedelta(milliseconds=1)
                                compute_sec = delta / datetime.timedelta(seconds=1)
                                compute_min = delta / datetime.timedelta(minutes=1)

                                dcop_sum = dcop_sum + compute_ms
                                dcop_count = dcop_count + 1

                                lines.append(f"{dcop_prev_timestamp} DCOP on {node_name} finished ({compute_ms:.2f} ms, {compute_sec:.2f} sec, {compute_min:.2f} min)")

                        lines.append(f"{timestamp} DCOP on {node_name} started")
                        dcop_start_timestamp = timestamp

                    wait_match = re.search(r'Wait for messages took (\d+) ms', message)
                    if wait_match:
                        ms = int(wait_match.group(1))
                        sec = ms / 1000
                        min = sec / 60
                        lines.append(f"{timestamp} wait for messages on {node_name} took {ms} ms {sec:.2f} seconds {min:.2f} minutes")

                    dcop_prev_timestamp = timestamp

                # find any INFO log statement from RLG
                rlg_match = re.search(r'\[RLG\-([^.]+).*\]\s+INFO\s+', raw_message)
                if rlg_match:
                    if re.search(r'\sUsing \S+ RLG algorithm', raw_message):
                        if rlg_prev_timestamp:
                            if not rlg_start_timestamp:
                                get_logger().error("Found end of RLG without start of RLG on node %s file %s line %d", node_name, f.filename(), f.filelineno())
                            else:
                                start_ts = parse_timestamp(rlg_start_timestamp)
                                end_ts = parse_timestamp(rlg_prev_timestamp)
                                delta = end_ts - start_ts
                                compute_ms = delta / datetime.timedelta(milliseconds=1)
                                compute_sec = delta / datetime.timedelta(seconds=1)
                                compute_min = delta / datetime.timedelta(minutes=1)

                                rlg_sum = rlg_sum + compute_ms
                                rlg_count = rlg_count + 1

                        rlg_start_timestamp = timestamp

                    rlg_prev_timestamp = timestamp
                
            if OOM_RE.search(line):
                lines.append(f"{last_timestamp} OOM on {node_name}")
                
    if last_timestamp:
        lines.append(f"{timestamp} agent on {node_name} finished")

    if rlg_count > 0:
        get_logger().info("Average RLG time on %s is %.2f ms", node_name, rlg_sum / rlg_count)
        
    if dcop_count > 0:
        get_logger().info("Average DCOP time on %s is %.2f ms", node_name, dcop_sum / dcop_count)
    
    return lines


def process_node(node_dir):
    """
    Arguments:
        node_dir (Path): directory to read
    Returns:
        list<str>: lines to add to the timeline
    """
    lines = list()
    
    node_name = map_utils.node_name_from_dir(node_dir)

    get_logger().debug("Processing %s for node %s", node_dir, node_name)
    
    agent_log_dir = node_dir / 'agent'
    if agent_log_dir.exists():
        lines.extend(parse_agent_log(agent_log_dir, node_name))
        
    client_log_dir = node_dir / 'client'
    if client_log_dir.exists():
        lines.extend(parse_client_log(client_log_dir, node_name))


    sim_log_dir = node_dir / 'sim-driver'
    if sim_log_dir.exists():
        lines.extend(parse_sim_log(sim_log_dir, node_name))

    return lines


def main_method(args):
    sim_output = Path(args.sim_output)
    lines = list()
    for node_dir in sim_output.iterdir():
        if node_dir.is_dir():
            lines.extend(process_node(node_dir))

    output = Path(args.output)
    with open(output, 'w') as out:
        for line in sorted(lines):
            print(line, file=out)

    get_logger().info("See %s for the output timeline", output)


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
    parser.add_argument("--output", dest="output", help="where to write the data", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

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
