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

########################
# source default config
########################
. ./config.sh

#####################
# check cmd line for
# user specified
# demand sceanrio
#####################
if [ $# -eq 1 ]; then
	dfile=$1
fi

if [ ! -d $dfile ]; then
 echo "Could not locate demand dir: $dfile!"
 exit 1
fi

if [ ! -d $sfile ]; then
	echo "Could not locate sceanrio dir: $sfile!"
	echo "Change sfile in config.sh"
	exit 1
fi 

###############################
# build a test name and dir
# based upon the config
###############################
testname=${dfile}-test
outputdir=/tmp/${testname}

echo "Executing sim runner:"
echo "${simrunner}"
echo "${sfile}"
echo "${dfile}"
echo "${outputdir}"

#exit 0

if [ -d $outputdir ]; then
 echo "Output dir already exists: $outputdir"
 echo "Moving to a backup folder"
 mv -f ${outputdir} ${outputdir}-`date +%s`
fi

# run simulation runner
java -jar ${simrunner} -o ${outputdir} -s ${sfile} -d ${dfile} --rlgAlgorithm STUB
pret=$?

logdir=${outputdir}/map-`date +%s`.log
cp map.log ${logdir}

if [ $pret == 0 ]; then
	echo "Success"
else
	echo "Failed with code: ${pret}"
	echo "See log file in: ${logdir}"
fi

exit 0
