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

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

help() {
    log "Usage $0 --batch <batch name> [ --experiment <name> --params <parameters.json> ]"
    exit
}

batch_name=""
experiment=ChainFault
scenario=acsos
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
        --experiment)
            if [ -z "$2" ]; then
                fatal "--experiment is missing argument"
            fi
            experiment=$2
            shift
            ;;
        --scenario)
            if [ -z "$2" ]; then
                fatal "--scenario is missing argument"
            fi
            scenario=$2
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

# copy so that we can add files to it and not need to specify an alternate service configuration file 
stage_scenario_dir="${mydir}"/stage_scenario
try rm -fr "${stage_scenario_dir}"
try cp -r "${scenario_dir}" "${stage_scenario_dir}"

for iftop in "filtered" "high-priority" "low-priority"; do
for num in 1; do
    for priority in 1 2; do

        try "${mydir}"/../../generate_service_configs.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${stage_scenario_dir}"/service-configurations.json

        demand_dir="${batch_name}"/demand.p${priority}.${num}
        try "${mydir}"/../../generate_demand.py \
            --scenario_dir "${scenario_dir}" \
            --num-priorities ${priority} \
            --num-apps-per-priority ${num} \
            --params "${params}" \
            --output "${demand_dir}"
        
        for algorithm in greedy-group; do
            for container_algorithm in round-robin; do

                output_name=generated_${priority}_${num}_${algorithm}_${container_algorithm}_iftop-${iftop}

				try "${mydir}"/stage.sh \
					--experiment ${experiment} \
                    --agent-configuration "${mydir}"/../agent-config_${algorithm}_${container_algorithm}_iftop-${iftop}.json \
					--scenario-name ${scenario} \
					--client-service-config "${mydir}"/../client-service-configuration.json \
					--demand ${demand_dir} \
					--scenario-dir "${stage_scenario_dir}" \
                    --output "${batch_name}"/${output_name}
            done # container weight
        done # algorithm

        # no map even spread
        algorithm=no-map
        for container_algorithm in round-robin; do

            output_name=generated_${priority}_${num}_${algorithm}_${container_algorithm}_even_iftop-${iftop}
            
            try "${mydir}"/../../generate_service_configs-no-map.py \
                --scenario_dir "${scenario_dir}" \
                --num-priorities ${priority} \
                --num-apps-per-priority ${num} \
                --params "${params}" \
                --output "${stage_scenario_dir}"/service-configurations.json

            try "${mydir}"/stage.sh \
                --experiment ${experiment} \
                --agent-configuration "${mydir}"/../agent-config_${algorithm}_${container_algorithm}_iftop-${iftop}.json \
                --scenario-name ${scenario} \
                --client-service-config "${mydir}"/../client-service-configuration.json \
                --demand ${demand_dir} \
                --scenario-dir "${stage_scenario_dir}" \
                --output "${batch_name}"/${output_name}

        done

        try rm -r ${demand_dir}

    done # priority
done # num
done # iftop

try rm -r "${stage_scenario_dir}"

try sed -e s/EXPERIMENT_NAME/${experiment}/ -e s/SCENARIO_NAME/${scenario}/ "${mydir}/execute-all_iftop.sh.template" > "${batch_name}/execute-all_iftop.sh"
try chmod +x "${batch_name}/execute-all_iftop.sh"

log "Files are in ${batch_name}"
log "Copy them to the testbed run execute-all_iftop.sh. It may be good to compress the directory before copying."
