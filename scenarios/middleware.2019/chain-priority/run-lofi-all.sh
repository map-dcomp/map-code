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

experiment=chain-priority
sim_results_folder="results"
output_charts_date_folder=$(date +%m%d_%H%M)



for i in $(seq 1 1) ; do

	date_folder=${experiment}_$(date +%m%d%y_%H%M)-${i}
	try mkdir -p ${sim_results_folder}/${date_folder}

	for demand in \
		demand.flat2 \
		; do
	   
		for rlg_algorithm in \
			NO-DCOP-STUB-PrW \
			ACDIFF-BIN-PACKING \
			RDIFF_BIN-PACKING \
			ACDIFF-STUB \
			RDIFF-STUB \
			; do

			scenario_name=$(echo "${experiment}_${demand}_${rlg_algorithm}" | tr '/' '_')

			log "Executing ${demand} using ${rlg_algorithm}"
			try "${mydir}"/run-lofi.sh \
				--agent-configuration "../agent-config_${rlg_algorithm}.json" \
				--demand "${demand}" \
				--output "${sim_results_folder}/${date_folder}/${scenario_name}" \
				2>&1 | tee ${sim_results_folder}/${date_folder}/${scenario_name}.execute.log

			try mkdir -p ${output_charts_date_folder}/tables/${rlg_algorithm}/${demand}/${i}
			try cp -r "${sim_results_folder}/${date_folder}/${scenario_name}/chart-output" ${output_charts_date_folder}/tables/${rlg_algorithm}/${demand}/${i}
			try cp -r "${sim_results_folder}/${date_folder}/${scenario_name}/sim-output/inputs"/* ${output_charts_date_folder}/tables/${rlg_algorithm}/${demand}/${i}

	    done

	done

done
