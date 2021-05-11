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
    import logging
    import logging.config
    import json
    from pathlib import Path

script_dir=Path(__file__).parent.absolute()

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
    path = Path(default_path)
    value = os.getenv(env_key, None)
    if value:
        path = Path(value)
    if path.exists():
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)


class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"

    
def main_method(args):
    input_file = Path(args.input)
    if not input_file.exists():
        get_logger().error("%s doesn't exist", input_file)
        return 1

    output_file = Path(args.output)
    output_file.parent.mkdir(parents=True, exist_ok=True)

    with open(output_file, "w") as output_f:
        output_f.write("graph topology {\n")
        with open(input_file) as input_f:
            set_re = re.compile('^set\s+(?P<name>\S+)\s+\[\s*\S+\s+(?P<object_type>\S+)\s*(?P<args>[^]]*)\s*\]$')
            
            for line in input_f:
                set_match = set_re.match(line)
                if set_match:
                    name = set_match.group("name")
                    object_type = set_match.group("object_type")
                    get_logger().debug("Found object type '%s'", object_type)
                    
                    if "node" == object_type:
                        output_f.write(f'{name} [shape="ellipse"];\n')
                    elif "duplex-link" == object_type:
                        args = set_match.group("args")
                        match = re.match(r'^\$(?P<source>\S+)\s+\$(?P<dest>\S+)\s+', args)
                        if match:
                            source = match.group("source")
                            dest = match.group("dest")
                            #output_f.write(f'"{source}" -- "{dest}" [label="{name}"];\n')
                            output_f.write(f'"{source}" -- "{dest}";\n')
                        else:
                            get_logger().warning("duplex-link args didn't parse '%s'", args)
                    elif "make-lan" == object_type:
                        args = set_match.group("args")
                        match = re.match(r'^"([^"]+)"', args)
                        if match:
                            nodes = match.group(1).split()

                            output_f.write(f'{name} [shape="box"];\n')
                            for node in nodes:
                                node_name = node[1:]
                                output_f.write(f'"{name}" -- "{node_name}";\n')

                            
        output_f.write("}\n")



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
    parser.add_argument("--input", dest="input", help="topology.ns file", required=True)
    parser.add_argument("--output", dest="output", help="graphviz file", required=True)    

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)
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
