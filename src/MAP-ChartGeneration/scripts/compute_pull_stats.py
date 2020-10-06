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
    from pathlib import Path
    import csv
    import datetime
    import map_utils

script_dir = os.path.abspath(os.path.dirname(__file__))


def get_logger():
    return logging.getLogger(__name__)


logfile_dateformat = '%Y-%m-%d %H:%M:%S'
pull_re = re.compile(r'(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*Pull image: (?P<registry>[^/]+)(?P<image>\S+)')
pull_fail_re = re.compile(r'(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*Failed to pull image.*: (?P<registry>[^/]+)(?P<image>\S+)')
pull_result_re = re.compile(r'(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*Pulling docker container result: (?P<result>true|false)')


def process_logfile(writer, node, agent_logfile):
    initial_pull_timestamp = None
    pull_attempts = 0
    current_image = None

    with open(agent_logfile, 'r') as f:
        for line in f:
            pull_match = pull_re.match(line)
            pull_fail_match = pull_fail_re.match(line)
            pull_result_match = pull_result_re.match(line)

            if pull_match is not None:
                initial_pull_timestamp = datetime.datetime.strptime(pull_match.group('timestamp'), logfile_dateformat)
                current_image = pull_match.group('image')
                pull_attempts = 1
            elif pull_fail_match is not None:
                pull_attempts = pull_attempts + 1
            elif pull_result_match is not None:
                final_timestamp = datetime.datetime.strptime(pull_result_match.group('timestamp'), logfile_dateformat)
                duration = final_timestamp - initial_pull_timestamp
                writer.writerow([node, initial_pull_timestamp, final_timestamp, duration.total_seconds(), pull_attempts, pull_result_match.group('result'), current_image])

                initial_pull_timestamp = None
                pull_attempts = 0
                current_image = None


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)",
                        default='logging.json')
    parser.add_argument("-s", "--sim-output", dest="sim_output", help="Hifi sim output directory (Required)",
                        required=True)
    parser.add_argument("-o", "--output", dest="output", help="Output file (defaults to pull_stats.csv)", default='pull_stats.csv')

    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)

    sim_dir = Path(args.sim_output)
    with open(args.output, 'w') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(['node', 'initial_pull_timestamp', 'finished_timestamp', 'time_to_pull_seconds', 'attempts', 'result', 'image'])

        for server_dir in sim_dir.iterdir():
            if server_dir.is_dir():
                node = server_dir.name
                agent_dir = server_dir / 'agent'
                for agent_logfile in agent_dir.glob('map-agent*.log'):
                    get_logger().debug("Processing %s", agent_logfile)
                    process_logfile(writer, node, agent_logfile)


if __name__ == "__main__":
    sys.exit(main())

