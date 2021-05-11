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
#!/bin/bash 

# batch run a set of experiments
# a run is an individual test with some configuration.
# a time out can be provided for each run. It is based upon timeout(1).
# this script will execute many runs and store results in an output folder.
# this script is dependent on _one_run.sh for executing the sim and chart gen tools.

# set your config here.
source ./config.sh

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

scenarios_folder="${dir_scenario}"
rlg_algorithms_to_run_file="${test_config}"
output_folder="${dir_output}"
has_errors=0
error_list=""

log "======== test inputs to validate  ========"
log "Scenarios input folder: ${scenarios_folder}"
log "Output folder: ${output_folder}"
log "RLG Algorithms to run file: ${rlg_algorithms_to_run_file}"
log "Runs per scenario: ${num_runs}"
log "Time limit per run: ${time_limit_per_run}"


# XXX add more robust testing to see if the are things like trailing /s on dirs. ect.

if [ -f ${output_folder} -o -d ${output_folder} ]; then
	error "fs object '${output_folder}' exists. will not create output folder. halt."
	fatal 254
fi

if [ ! -d ${scenarios_folder} -o ! -f ${rlg_algorithms_to_run_file} ]; then
	error "scenario folder or algorithms file missing. halt."
	fatal 253
fi 

try mkdir "${output_folder}"

while read algo_line ; do

  # read the config info
  rlg_algorithm=${algo_line% *}
  if [[ "${algo_line}" = *" "* ]] ; then
    stub_algo=${algo_line#* }
  else
    stub_algo=""
  fi

  # using the config info run each scenario a number of times
  for scenario in $(ls "${scenarios_folder}"); do
    for i in $(seq 1 ${num_runs}); do

      run_dir="${output_folder}/${rlg_algorithm}${stub_algo}/${scenario}/run-${i}"

      log "======== Scenario ${scenario}, iteration $i ========"
      log "RLG Algorithm: ${rlg_algorithm}"
      if [[ ${rlg_algorithm} =~ STUB ]]; then
        log "RLG Stub Choose Algorithm: ${stub_algo}"
      fi
      log "Run directory: ${run_dir}"

      try mkdir -p ${run_dir}

      log_file=${run_dir}/harness.log
      
      touch ${log_file}

      start_time=$(date +%s)

      #timeout -k "${time_limit_kill}" "${time_limit_per_run}" ./_one_run.sh "${scenarios_folder}/${scenario}" "${rlg_algorithm}" "${stub_algo}" "${run_dir}" > "${log_file}" 2>&1
      ./_one_run.sh "${scenarios_folder}/${scenario}" "${rlg_algorithm}" "${stub_algo}" "${run_dir}" > "${log_file}" 2>&1		
      
    done
  done
done < ${rlg_algorithms_to_run_file}

log "Batch run completed. Has errors: ${has_errors}"
