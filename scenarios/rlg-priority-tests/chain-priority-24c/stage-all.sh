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

cleanup() {
    debug "In cleanup"
}
trap 'cleanup' INT TERM EXIT

help() {
    log "Usage $0 --batch <batch name>"
    exit
}

batch_name=""
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

experiment=chain-priority-24c

opwd=$(pwd)

try mkdir -p "${batch_name}"
try cd  "${batch_name}"

for demand in \
    demand.flat.c1 \
    demand.flat.c3 \
    demand.flat.c5 \
    ; do

    for rlg_algorithm in \
	NO-MAP-Po40 \
	STUB-Po40-FixTarPr \
	STUB-Po40-GdyGrPr \
	STUB-Po40-NoPr \
	NO-MAP-Po75 \
	STUB-Po75-FixTarPr \
	STUB-Po75-GdyGrPr \
	STUB-Po75-NoPr \
        ; do

        scenario_name=$(echo "${experiment}_${demand}_${rlg_algorithm}" | tr '/' '_')
        
        try "${mydir}"/stage.sh \
            --experiment ${experiment} \
            --demand "${mydir}"/${demand} \
            --agent-configuration "${mydir}"/../agent-config_${rlg_algorithm}.json \
            --scenario-name ${scenario_name}
    done
    
done

try cd "${opwd}"

try cp "${mydir}/execute-all.sh.template" "${batch_name}/execute-all.sh"
try chmod +x "${batch_name}/execute-all.sh"

log "Files are in ${batch_name}"
log "Copy them to a node in the scenario and run execute-all.sh"

