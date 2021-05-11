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
Checks for unknown host exceptions with zero duration less than 1 second from a long unknown host exception in the client logs.

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
    import datetime
    import fileinput

script_dir=Path(__file__).parent.absolute()

def get_logger():
    return logging.getLogger(__name__)


def process_client_dir(client_dir):
    unknown_host_re = re.compile(r'(?P<timestamp>\d{4}-\d{2}-\d{2}/\d{2}:\d{2}:\d{2}\.\d{3}/\S+).*Unable to find host (?P<host>\S+) after (?P<duration>\d+) ms')
    acceptable_delta = datetime.timedelta(seconds=1)

    prev_long_failure = dict() # name -> timestamp
    logfiles = sorted(client_dir.glob("map-client*.log"), key=lambda f: f.stat().st_mtime)
    with fileinput.input(files=logfiles) as f:
        for line in f:
            match = unknown_host_re.match(line)
            if match:
                timestamp = map_utils.log_timestamp_to_datetime(match.group('timestamp'))
                hostname = match.group('host')
                duration = int(match.group('duration'))
                if duration > 0:
                    prev_long_failure[hostname] = timestamp
                elif duration == 0:
                    if hostname not in prev_long_failure:
                        get_logger().warning("Lookup at %s of %s took zero time and there is no previous long lookup", timestamp, hostname)
                    else:
                        prev_timestamp = prev_long_failure[hostname]
                        diff = timestamp - prev_timestamp
                        if diff > acceptable_delta:
                            get_logger().warning("Lookup at %s of %s took zero time and the last lookup was at %s", timestamp, hostname, prev_timestamp)

    
def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return 1

    for node_dir in sim_output.iterdir():
        client_dir = node_dir / 'client'
        if client_dir.is_dir():
            process_client_dir(client_dir)

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


    args = parser.parse_args(argv)

    map_utils.setup_logging(default_path=args.logconfig)
    if 'multiprocessing' in sys.modules:
        # requires the multiprocessing-logging module - see https://github.com/jruere/multiprocessing-logging
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
