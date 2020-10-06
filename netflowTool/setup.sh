#!/bin/sh

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

# apt-get update
# apt-get install git libtool autoconf pkg-config flex bison python3.6 tar zip -y 
# apt-get install libbz2-1.0 libbz2-dev libbz2-ocaml libbz2-ocaml-dev -y
# apt-get install python3-pip -y
# git clone https://github.com/phaag/nfdump.git

try mkdir -p "${mydir}"/build

try git submodule sync
try git submodule update
try cd "${mydir}"/nfdump
try mkdir -p m4
try ./autogen.sh
try ./configure --prefix "${mydir}"/build
#ldconfig
try make
#ldconfig
try make install
#ldconfig
