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
alg=$1

./gradlew :MAP-Agent:build -x :MAP-Agent:test -x :MAP-Agent:compileTestJava -x :MAP-Agent:spotbugsMain -x :MAP-Agent:spotbugsTest -x :MAP-Agent:checkstyleMain -x :MAP-Agent:checkstyleT
killall -9 java

for agent in 10
do
  for graph in "random-network"
  do
    for instances in {0..19}
    do
    killall -9 java
    cmd="java -jar MAP-Agent/build/libs/map-sim-runner-4.9.8-executable.jar -s "$alg"/scenario/"$graph"/d"$agent"/"$instances" -d "$alg"/scenario/"$graph"/d"$agent"/"$instances"/Demand -o "$alg"/scenario/"$graph"/d"$agent"/"$instances"/output"
    move_log="mv map.log "$alg"/scenario/"$graph"/d"$agent"/"$instances"/output"
    echo $cmd
    $cmd
    echo $move_log
    $move_log
    sleep 5s
    done
  done
done
