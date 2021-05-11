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
#!/bin/sh

# create the lofi files to run all experiments

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

help() {
    log "Usage $0 --batch <batch name> [ --params <parameters.json> ]"
    exit
}

batch_name=""
params="${mydir}"/demand-params.json
while [ $# -gt 0 ]; do
    case $1 in
        --help|-h)
            help
            ;;
        --batch)
            if [ -z "$2" ]; then
                fatal "--batch is missing argument"
            fi
            batch_name=$2
            shift
            ;;
        --params)
            if [ -z "$2" ]; then
                fatal "--params is missing argument"
            fi
            params=$2
            shift
            ;;
        *)
            error "Unknown argument $1"
            help
            ;;
    esac
    shift
done

if [ -z "${batch_name}" ]; then
    help
fi

if [ ! -e "${params}" ]; then
    fatal "${params} doesn't exist"
fi

try mkdir -p "${batch_name}"

scenario_dir="${mydir}/scenario"

map_srcdir="${mydir}"/../../../src
if [ ! -d "${map_srcdir}" ]; then
    fatal "Cannot find ${map_srcdir}, make sure you are running from inside a checkout of lo-fi"
fi
agent_jar=$(ls -rt "${map_srcdir}"/MAP-Agent/build/libs/map-sim-runner-*-executable.jar | tail -1)

try rm -fr "${batch_name}"
try mkdir -p "${batch_name}"
try cp "${agent_jar}" "${batch_name}/map-sim-runner-executable.jar"

create_scenario_dir() {
    # create lo-fi scenario directory - sets output_directory
    output_directory="${batch_name}"/lofi_${priority}_${num}_${algorithm}_${container_algorithm}
    try rm -fr "${output_directory}"
    try mkdir -p "${output_directory}"
    try cp -r "${scenario_dir}" "${output_directory}"        

    if [ "${algorithm}" = "no-map" ]; then
        agent_config_src=agent-config_${algorithm}.json
    else
        agent_config_src=agent-config_${algorithm}_${container_algorithm}.json
    fi
    
    try cp "${mydir}"/../${agent_config_src} "${output_directory}"/agent-config.json
    try cp "${mydir}"/run-lofi.sh.template "${output_directory}"/run-lofi.sh
    try chmod +x "${output_directory}"/run-lofi.sh
}

for num in 1 2; do
    for priority in 2 5; do
        for algorithm in greedy-group reservation; do
            for container_algorithm in proportional round-robin; do
                create_scenario_dir
                
                try "${mydir}"/../generate_service_configs.py \
                    --scenario_dir "${scenario_dir}" \
                    --num-priorities ${priority} \
                    --num-apps-per-priority ${num} \
                    --params "${params}" \
                    --output "${output_directory}"/scenario/service-configurations.json

                try "${mydir}"/../generate_demand.py \
                    --scenario_dir "${scenario_dir}" \
                    --num-priorities ${priority} \
                    --num-apps-per-priority ${num} \
                    --params "${params}" \
                    --output "${output_directory}"/demand
                
            done # container weight
        done # algorithm

        # no map single container
        algorithm=no-map
        container_algorithm="single" # use this variable to handle the different spreads
        create_scenario_dir

        try "${mydir}"/../generate_service_configs.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${output_directory}"/scenario/service-configurations.json

        try "${mydir}"/../generate_demand.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${output_directory}"/demand
        
        # no map even spread
        algorithm=no-map
        container_algorithm="even" # use this variable to handle the different spreads

        create_scenario_dir

        try "${mydir}"/../generate_service_configs-no-map.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${output_directory}"/scenario/service-configurations.json

        try "${mydir}"/../generate_demand.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${output_directory}"/demand
        
    done # priority
done # num
