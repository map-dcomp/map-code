#!/usr/bin/env python3.6

import warnings
with warnings.catch_warnings():
    import argparse
    import os
    import os.path
    import sys
    import topologyReader
    from pathlib import Path

    import topologyReader
    import portNameMerge
    import routingTable
    import merger

script_dir=os.path.abspath(os.path.dirname(__file__))
    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    topologyReader.main()

    portNameMerge.main()

    routingTable.main()

    merger.main()

    print("Moving outputs to webdata")
    os.rename("netconfig.json", "webdata/netconfig.json")
    os.rename("routingTable.json", "webdata/routingTable.json")
    os.rename("flowData.json", "webdata/flowData.json")

if __name__ == "__main__":
    sys.exit(main())
