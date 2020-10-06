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
#!/bin/sh

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

map_srcdir=${mydir}/../../../src

help() {
    log "Usage $0 --output <output directory> [ --demand <demand directory> --agent-configuration <agent config file>]"
    exit
}

# parse arguments
agent_configuration="--agentConfiguration ${mydir}/../agent-config_RDIFF-STUB-GdyGr-RR.json"
base_output=""
demand_dir="${mydir}/demand.flat_40TC"
while [ $# -gt 0 ]; do
    case $1 in
        --help|-h)
            help
            ;;
        --agent-configuration)
            if [ -z "$2" ]; then
                fatal "--agent-configuration is missing argument"
            fi
            agent_configuration="--agentConfiguration ${2}"
            shift
            ;;
        --output)
            if [ -z "$2" ]; then
                fatal "--output is missing argument"
            fi
            base_output=${2}
            shift
            ;;
        --demand)
            if [ -z "$2" ]; then
                fatal "--demand is missing argument"
            fi
            demand_dir="${2}"
            shift
            ;;
        *)
            error "Unknown argument $1"
            help
            ;;
    esac
    shift
done

if [ -z "${base_output}" ]; then
   help
fi

if [ ! -d "${demand_dir}" ]; then
    fatal "Cannot find demand directory: ${demand_dir}"
fi

try mkdir -p "${base_output}"

scenario_dir="${mydir}"/scenario
sim_output=${base_output}/sim-output

agent_jar=$(ls -rt "${map_srcdir}"/MAP-Agent/build/libs/map-sim-runner-*-executable.jar | tail -1)

rm -fr "${sim_output}"
try mkdir -p "${sim_output}"

log "Running simulation"
try java \
    -jar "${agent_jar}" ${agent_configuration} \
     --scenario "${scenario_dir}" \
     -d "${demand_dir}" \
     -o "${sim_output}"
