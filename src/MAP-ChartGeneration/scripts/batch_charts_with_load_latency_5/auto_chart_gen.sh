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


chart_script_path="${mydir}/generate_all_charts.sh"

input_folder="/home/${USER}/Documents/bud/chart_generation/__input__"
output_folder="/home/${USER}/Documents/bud/chart_generation/__output__"


log "--- Auto chart generation ---"
if [ -f ${chart_script_path} ] ; then
	log "Using chart generation script at '${chart_script_path}'"
else
	fatal "Could not find chart generation script at '${chart_script_path}'"
fi

log "Waiting for input at '${input_folder}'"
log "Outputting to '${output_folder}'"
printf '\n\n\n'


try cd $(dirname ${chart_script_path})

while true ; do

	for input in $(ls ${input_folder}) ; do
		log "Found '${input}'. Running chart generation..."
		sleep 8

		charts_date_folder=charts-$(date +%m%d%Y_%H%M%S)
		#try rm -f -r "${input_folder}/${input}/${charts_date_folder}"

		if [ -f ${chart_script_path} ] ; then
			try ${chart_script_path} "${input_folder}/${input}/tables" "${input_folder}/${input}/${charts_date_folder}"
			try mv ${input_folder}/${input} ${output_folder}
			log "Finished chart generation of '${input}'."
		else
			error "Chart generation script '${chart_script_path}' does not exist."
		fi


		printf '\n\n\n'
		sleep 2
	done

	sleep 5
done
