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

experiment=FullScaleEmulab
output=$1
execute_all_script=${output}/execute-all.sh

if [ -z ${output} ] ; then
	fatal "You must specify an output directory"
fi


stage_scenario() {
	scenario=$1
	demand=$2
	config=$3
	scenario_output=$4

	try "${mydir}"/stage-helper.sh $* \
		--demand "${mydir}"/${demand} \
		--client-service-config "${mydir}"/client-service-configurations.json \
		--agent-configuration "${mydir}"/agent-configuration-${config}.json \
		--scenario-dir "${mydir}"/${scenario} \
		--experiment ${experiment} \
		--output ${output}/${scenario_output}

	try log "cd ${scenario_output}" >> ${execute_all_script}
	try log "./execute-scenario.sh --output \${output}/${scenario_output}" >> ${execute_all_script}
	try log "cd .." >> ${execute_all_script}
	try log "" >> ${execute_all_script}
}




try mkdir -p ${output}
try touch ${execute_all_script}

try log "output=\$1" >> ${execute_all_script}
try log "if [ -z \${output} ] ; then output=${output} ; fi" >> ${execute_all_script}
try log "" >> ${execute_all_script}


for config in SMA EMA ; do
	try stage_scenario "scenario" "demand.priority_waves" "${config}" "priority_waves-${config}"
	try stage_scenario "scenario" "demand.1min_spikes" "${config}" "1min_spikes-${config}"
	try stage_scenario "scenario" "demand.constant" "${config}" "constant-${config}"
done


try chmod +x ${execute_all_script}



