#!/bin/bash
#
# Build MAP. Optionally run tests. 
#
#### When adding a new project, please update <root>/continuous_integration/standard_build

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"


build_appmgr() {
    NAME="MAP-ApplicationManager"
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
build_appmgr ${run_tests} ${build_offline}
build_agent ${run_tests} ${build_offline}
build_visualization ${run_tests} ${build_offline}
