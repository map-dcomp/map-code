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

dir=$1
if [ -z "${dir}" ]; then
    dir=$(pwd)
fi

map_base=${mydir}/../../..
map_srcdir=${map_base}/src
chart_jar=$(ls -rt "${map_srcdir}"/MAP-ChartGeneration/build/libs/MAP-ChartGeneration-*-executable.jar | tail -1)

cd "${dir}"
if [ -n "$(find . -maxdepth 1 -name '*.tar.xz' -print -quit)" ]; then
    for i in *.tar.xz; do
        try xzcat -T0 ${i} | tar -x
    done
    for sim_dir in *; do
        if [ -d "${sim_dir}" ]; then
            cd "${sim_dir}"
            if [ -n "$(find . -maxdepth 1 -name '*.tar.xz' -print -quit)" ]; then
                log "Processing sim dir ${sim_dir}"

                for i in *.tar.xz; do
                    try xzcat -T0 ${i} | tar -x
                done
                cd ..

                chart_output=charts-pass1
                try mkdir -p "${chart_output}"

                try java -jar "${chart_jar}" \
                    load \
                    "${scenario_dir}" \
                    "${sim_output}" \
                    "${chart_output}" \
                    10000 \
                    > "${chart_output}"/chartgen-pass1.log 2>&1

                # free up disk space
                try rm -fr ${sim_dir}
            else
                cd ..
            fi # tar.xz in sim dir
        fi
    done
else
    warn "No tar.xz files found in the current directory, skipping processing"
fi # have some tar.xz files
