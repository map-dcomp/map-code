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
#!/bin/bash

########################
# source default config
########################
. ./config.sh


if [ $# -ne 1 ]; then
	echo "$0 <demand-folder no dot no />"
	exit 1
fi

sfolder=./
sinterval=10000
templatefolder=./template-results-v4

dfolder=$1

if [[ $1 =~ \..* ]]; then
        echo "output file: $1 name with dot"
        exit 1
fi

if [[ $1 =~ .*/ ]]; then
        echo "input file: $1 name with /"
        exit 1
fi

dfolder="./${dfolder}"
ifolder="${dfolder}-test"
ofolder="${dfolder}-analysis"
rfolder="${dfolder}-results"

#echo "Input: $sfolder $dfolder $ifolder $ofolder. Results: $rfolder"
#exit 0

if [ ! -d "${dfolder}" -o ! -d "${ifolder}" ]; then
	echo "$dfolder or $ifolder does not exist"
	exit 1
fi

java -jar ${chartgen} all "${sfolder}"  "${dfolder}" "${ifolder}" "${ofolder}" "${sinterval}"

pret=$?

if [ $pret -eq 0 ]; then
	cp -r "${templatefolder}" "${rfolder}"
	echo "Success"
else
	echo "Failed with code: ${pret}"
fi

exit 0
