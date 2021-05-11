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

cleanup() {
    debug "In cleanup"
}
trap 'cleanup' INT TERM EXIT

help() {
    log "Usage: $0 --sim <sim output dir> --output <output dir>"
    exit
}

scenario_dir=""
demand_dir=""
analysis_output=""
sim_output=""

while [ $# -gt 0 ]; do
    debug "Checking arg '${1}' with second '${2}'"
    case ${1} in
        --help|-h)
            help
            ;;
        --sim)
            if [ -z "${2}" ]; then
                fatal "--sim is missing an argument"
            fi
            sim_output=${2}
            shift
            ;;
        --output)
            if [ -z "${2}" ]; then
                fatal "--output is missing an argument"
            fi
            analysis_output=${2}
            shift
            ;;
        --num-cpus)
            num_cpus=${2}
            shift
            ;;
        *)
            error "Unknown argument ${1}"
            help
            ;;
    esac
    shift
done

if [ -z "${sim_output}" ]; then
   help
fi
if [ -z "${analysis_output}" ]; then
   help
fi


if [ -z "${num_cpus}" ]; then
    num_cpus=$(cat /proc/cpuinfo  | grep "physical id" | wc -l) || warn "Unable to get number of CPUs, assuming 1"
	
	if [ -z "${num_cpus}" ]; then
		num_cpus=1
	fi
fi


mkdir -p "${analysis_output}"

find "${sim_output}" -maxdepth 1 -mindepth 1 -type d -print \
    | xargs -P ${num_cpus} -n 1 "${mydir}"/map_log_iftop_missing_data_scan.sh "${analysis_output}"  || fatal "Unable to analyze data"

rm -f ${analysis_output}/iftop_missing_data.log
for node in $(ls ${sim_output}) ; do
	if [ -d "${sim_output}/${node}/agent" ] ; then
		log "${node}: " >> "${analysis_output}/iftop_missing_data.log"
		tail -n 2 "${analysis_output}/${node}-iftop_missing_data.log" >> "${analysis_output}/iftop_missing_data.log"
		log >> "${analysis_output}/iftop_missing_data.log"
	fi
done








