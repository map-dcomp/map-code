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
output_folder="${dir_output}"
has_errors=0
error_list=""

log "======== test inputs to validate  ========"
log "Scenarios input folder: ${scenarios_folder}"
log "Output folder: ${output_folder}"
log "Agent configurations folder: ${test_configs_folder}"
log "Runs per scenario: ${num_runs}"
log "Time limit per run: ${time_limit_per_run}"
printf "\n\n\n\n"


# XXX add more robust testing to see if the are things like trailing /s on dirs. ect.

if [ -f ${output_folder} ] || [ -d ${output_folder} ]; then
	error "fs object '${output_folder}' exists. will not create output folder. halt."
	fatal 254
fi

if [ ! -d ${scenarios_folder} ] || [ ! -d ${test_configs_folder} ]; then
	error "scenario folder or configs to run folder missing. halt."
	fatal 253
fi 

try mkdir "${output_folder}"

for test_config_file in $(ls "${agent_configs_folder}"); do

  # using the config info run each scenario a number of times
  for scenario in $(ls "${scenarios_folder}"); do
    for i in $(seq 1 ${num_runs}); do

      sleep 3	

      time_id=$(date +%Y%m%d_%H%M%S)
      run_dir="${output_folder}/${test_config_file}/${scenario}/${time_id}"

      log "======== Config ${test_config_file}, Scenario ${scenario}, run $i ========"
      log "Agent configuration file: ${agent_configs_folder}/${test_config_file}"
      cat "${agent_configs_folder}/${test_config_file}"
      log "Run directory: ${run_dir}"

      try mkdir -p ${run_dir}

      log_file=${run_dir}/harness.log
      
      touch ${log_file}

      ./_one_run.sh "${scenarios_folder}/${scenario}" "${agent_configs_folder}/${test_config_file}" "${run_dir}" | tee "${log_file}"

      printf "\n\n\n"
      
    done
  done
done

log "Batch run completed. Has errors: ${has_errors}"
