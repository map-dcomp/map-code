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
    log "Usage: $0 --first_epoch_timestamp <reference epoch time for run> --service <service> [--all_client_requests_file <all_client_request.csv file path>] [--region_subnet_file <region_subnet.txt file path>] [--start_minutes <analysis start minutes>] [--end_minutes <analysis end minutes>] [--window_size <window size>]"
    exit
}

first_epoch_timestamp=""
all_client_requests_file="all_client_requests.csv"
region_subnet_file="region_subnet.txt"
service=""
start_minutes=0
end_minutes=120
window_size=5

while [ $# -gt 0 ]; do
    debug "Checking arg '${1}' with second '${2}'"
    case ${1} in
        --help|-h)
            help
            ;;
        --first_epoch_timestamp)
            if [ -z "${2}" ]; then
                fatal "--first_epoch_timestamp is missing an argument"
            fi
            first_epoch_timestamp=${2}
            shift
            ;;
        --service)
            if [ -z "${2}" ]; then
                fatal "--service is missing an argument"
            fi
            service=${2}
            shift
            ;;
        --all_client_requests_file)
            if [ -z "${2}" ]; then
                fatal "--all_client_requests_file is missing an argument"
            fi
            all_client_requests_file=${2}
            shift
            ;;
        --region_subnet_file)
            if [ -z "${2}" ]; then
                fatal "--region_subnet_file is missing an argument"
            fi
            region_subnet_file=${2}
            shift
            ;;
        --start_minutes)
            if [ -z "${2}" ]; then
                fatal "--start_minutes is missing an argument"
            fi
            start_minutes=${2}
            shift
            ;;
        --end_minutes)
            if [ -z "${2}" ]; then
                fatal "--end_minutes is missing an argument"
            fi
            end_minutes=${2}
            shift
            ;;
        --window_size)
            if [ -z "${2}" ]; then
                fatal "--window_size is missing an argument"
            fi
            window_size=${2}
            shift
            ;;
        *)
            error "Unknown argument ${1}"
            help
            ;;
    esac
    shift
done

log Service: ${service}
log First epoch timestamp: ${first_epoch_timestamp}


windows=$(((${end_minutes} - ${start_minutes}) / ${window_size}))


grep ${service} ${all_client_requests_file} > all_client_requests-${service}.csv



for w in $(seq 0 $((${windows} - 1))) ; do

	# set these (range in minutes)
	MINSTART=$((${start_minutes} + ${window_size} * w))
	MINEND=$((${MINSTART} + ${window_size}))
	
	log "Window ${MINSTART} - ${MINEND} minutes:"
	
	
	# adjust to ms
	STM=$(($MINSTART*60*1000))
	ETM=$(($MINEND*60*1000))

	MINTS=$(($first_epoch_timestamp + $STM))
	MAXTS=$(($first_epoch_timestamp + $ETM))

	#debug
	#log "test range: $MINTS - $MAXTS ($(($MAXTS-$MINTS)))"

	# dump rows for the range to a temporary file (for debugging)
	rowfile=.tmp.dat
	awk -F, -v st=$MINTS -v et=$MAXTS '{ if ($1 >= st && $1 <= et) print $2 }' all_client_requests-${service}.csv > $rowfile

	# build count stats from temp file
	statsfile=.tmp.stats
	cat $rowfile | sort | uniq -c | tr -s ' ' > $statsfile

	# assign region identifiers to blocks. store in file
	statsfilewithregion=.tmp.stats.region
	rm -f $statsfilewithregion
	while read e; do
	 ip=$(echo $e | awk '{ print $2 }')
	 subnet=$(echo ${ip} | sed s/\\.[^.]*$//g).0
	 block=$(grep "${subnet}" ${region_subnet_file} | sed s/' .*'//g)
	 echo "$e $block" >> $statsfilewithregion
	done <$statsfile

	# sort for printing
	cat $statsfilewithregion | sort -k 1 -n -r > .tmp
	mv .tmp $statsfilewithregion

	# sum region counts
	uniqueregions=$(awk '{ print $3 }' $statsfilewithregion | sort | uniq)
	regionstats=.tmp.stats.region.sum
	globalcount=0
	rm -f $regionstats
	for region in $uniqueregions
	do
	 grep $region $statsfilewithregion > .tmp
	 totalct=$(awk '{ sum += $1 } END {print sum}' .tmp)
	 #log "total ct: $totalct"
	 echo "$totalct $region" >> $regionstats
	 globalcount=$(($globalcount+$totalct))
	done

	# sort for printing
	cat $regionstats | sort -k 1 -n -r > .tmp
	mv .tmp $regionstats

	log "NCP view"
	cat $statsfilewithregion

	log ""
	log "Region view"
	#cat $regionstats
	while read e; do
	 
	 lct=$(echo "$e" | awk '{print $1}')
	 val=$(echo "scale=2; $lct/$globalcount" | bc)
	 log "$e $val"

	done <$regionstats
	
	log
	log

done






