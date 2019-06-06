#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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
#
# Build MAP. Optionally run tests. 
#
#### When adding a new project, please update <root>/Jenkinsfile

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"


build_agent() {
    NAME="MAP-Agent"
    echo building $NAME
    pushd $NAME

    run_tests=$1
    build_offline=$2
    test_arg=""
    offline_arg=""
    if [ ${run_tests} -eq 0 ]; then
        test_arg="-x test"
    else
        test_arg="test"
    fi
    if [ ${build_offline} -eq 1 ]; then
        offline_arg="--offline"
    fi

    ./gradlew ${offline_arg} clean build publish ${test_arg} | tee build_log.txt

    if grep -q '^BUILD SUCCESSFUL' build_log.txt ; then
        printf "*** SUCCESS: $NAME\n"
    else
	printf "\n\n*** BUILD FAILED on $NAME\n\n"
	cat build_log.txt
        exit 1
    fi
    popd 
}

build_visualization() {
    NAME="MAP-Visualization"
    echo building $NAME
    pushd $NAME

    run_tests=$1
    build_offline=$2
    test_arg=""
    offline_arg=""
    if [ ${run_tests} -eq 0 ]; then
        test_arg="-x test"
    else
        test_arg="test"
    fi
    if [ ${build_offline} -eq 1 ]; then
        offline_arg="--offline"
    fi

    ./gradlew ${offline_arg} clean build ${test_arg} | tee build_log.txt

    if grep -q '^BUILD SUCCESSFUL' build_log.txt ; then
        printf "*** SUCCESS: $NAME\n"
    else
	printf "\n\n*** BUILD FAILED on $NAME\n\n"
	cat build_log.txt
        exit 1
    fi
    popd 
}

build_chartgeneration() {
    NAME="MAP-ChartGeneration"
    echo building $NAME
    pushd $NAME

    run_tests=$1
    build_offline=$2
    test_arg=""
    offline_arg=""
    if [ ${run_tests} -eq 0 ]; then
        test_arg="-x test"
    else
        test_arg="test"
    fi
    if [ ${build_offline} -eq 1 ]; then
        offline_arg="--offline"
    fi

    ./gradlew ${offline_arg} clean build ${test_arg} | tee build_log.txt

    if grep -q '^BUILD SUCCESSFUL' build_log.txt ; then
        printf "*** SUCCESS: $NAME\n"
    else
	printf "\n\n*** BUILD FAILED on $NAME\n\n"
	cat build_log.txt
        exit 1
    fi
    popd 
}

build_demandgenerationgui() {
    NAME="MAP-DemandGenerationGUI"
    echo building $NAME
    pushd $NAME

    run_tests=$1
    build_offline=$2
    test_arg=""
    offline_arg=""
    if [ ${run_tests} -eq 0 ]; then
        test_arg="-x test"
    else
        test_arg="test"
    fi
    if [ ${build_offline} -eq 1 ]; then
        offline_arg="--offline"
    fi

    ./gradlew ${offline_arg} clean build ${test_arg} | tee build_log.txt

    if grep -q '^BUILD SUCCESSFUL' build_log.txt ; then
        printf "*** SUCCESS: $NAME\n"
    else
	printf "\n\n*** BUILD FAILED on $NAME\n\n"
	cat build_log.txt
        exit 1
    fi
    popd 
}


build_protelis() {
    NAME="P2Protelis"
    echo building $NAME
    pushd $NAME

    run_tests=$1
    build_offline=$2
    test_arg=""
    offline_arg=""
    if [ ${run_tests} -eq 0 ]; then
        test_arg="-x test"
    else
        test_arg="test"
    fi
    if [ ${build_offline} -eq 1 ]; then
        offline_arg="--offline"
    fi

    ./gradlew ${offline_arg} clean build publish ${test_arg} | tee build_log.txt

    if grep -q '^BUILD SUCCESSFUL' build_log.txt ; then
        printf "*** SUCCESS: $NAME\n"
    else
	printf "\n\n*** BUILD FAILED on $NAME\n\n"
	cat build_log.txt
        exit 1
    fi
    popd 
}

cd "${mydir}"
run_tests=1
build_offline=0

while [ $# -gt 0 ]; do
    case "$1" in
        "--skiptests")
            run_tests=0
            ;;
        "--offline")
            build_offline=1
            ;;
        "--help")
            log "Build MAP"
            log "Usage: $0 [--skiptests]|[--offline]|[--help|-h]"
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            ;;
    esac
    shift
done

if [ $run_tests -eq 0 ]; then
    log "Skipping tests"
fi
if [ $build_offline -eq 1 ]; then
    log "Building offline"
fi

build_protelis ${run_tests} ${build_offline}
build_agent ${run_tests} ${build_offline}
build_visualization ${run_tests} ${build_offline}
build_chartgeneration ${run_tests} ${build_offline}
build_demandgenerationgui ${run_tests} ${build_offline}

