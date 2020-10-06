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

# app count
count=100

# spread initial starting locations
ncpct=24
ncp=0


echo -e "["


for i in `seq 1 ${count}`
do


echo -e "  {"
echo -e "    \"service\": {"
echo -e "      \"group\": \"com.bbn.map\","
echo -e "      \"artifact\": \"app${i}\","
echo -e "      \"version\": \"1.0\""
echo -e "    },"
echo -e "    \"hostname\": \"app${i}\","
echo -e "    \"defaultNode\": \"nodeA${ncp}\","
echo -e "    \"defaultNodeRegion\": \"A\","
echo -e "    \"initialInstances\": \"1\","
echo -e "    \"computeCapacity\": {"
echo -e "      \"TASK_CONTAINERS\": \"1\","
echo -e "      \"CPU\": \"1\""
echo -e "    },"
echo -e "    \"networkCapacity\": {"
echo -e "      \"DATARATE_TX\": \"100\","
echo -e "      \"DATARATE_RX\": \"100\""
echo -e "    }"
echo -en "  }"

ncp=$(($ncp+1))
if [ $ncp -gt $ncpct ]; then
	ncp=0
fi

if [ $i -lt $count ]; 
then
echo ","
fi

done

echo -e "\n]"
