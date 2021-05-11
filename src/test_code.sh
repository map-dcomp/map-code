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
# alg=rdiff,cdiff,base
killall -9 java
./gradlew :MAP-Agent:build -x :MAP-Agent:test -x :MAP-Agent:compileTestJava -x :MAP-Agent:spotbugsMain -x :MAP-Agent:spotbugsTest -x :MAP-Agent:checkstyleMain -x :MAP-Agent:checkstyleTest
content='{"dcopAcdiffSimulateMessageDrops: true, "dcopAcdiffSimulateMessageDropRate": 0.0, "dcopAcdiffTimeOut": 40}'
head='{"dcopAcdiffSimulateMessageDrops": true, "dcopAcdiffSimulateMessageDropRate": '
mid=', "dcopAcdiffTimeOut": '
tail='}'
for topology in "random-network"
do
  # for timeout in 60 40 20
  for timeout in 140
  do
    # for rate in 0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0
    # for rate in 0.0 0.2 0.4 0.6 0.8 1.0
    for rate in 0.0
    do
      config="config/timeout="$timeout",rate="$rate".json"
      echo $head$rate$mid$timeout$tail > $config
      for agent in 10
      do
        result_folder=/Users/khoihd/Documents/workspace/CP-19/$topology/timeout=$timeout/rate=$rate/d$agent/
        # for id in {0..9}
        for id in 0
        do
          killall -9 java
          echo $result_folder
          mkdir -p $result_folder
          rm -rf $result_folder/$id
          rm -rf scenario/random-network/d$agent/$id/output
          rm -rf scenario/random-network/d$agent/$id/*.log
          instance=scenario"/"$topology"/d"$agent"/"$id
          demand=$instance"/demand/"
          outfolder=$instance"/output"
          outfile=$instance$"/"$id".log"
          echo $instance
          echo $demand
          echo $outfile
          cmd="java -jar "$java_jar" -s "$instance" -d "$demand" -o "$outfolder" --agentConfiguration "$config
          echo $cmd
          $cmd
          mv map.log $outfile
          sleep 1s
          cp -r scenario/$topology/d$agent/$id $result_folder/
        done
      done
      sleep 1s
    done
  done
done
# mkdir /Users/khoihd/Documents/workspace/CP-19/rate=$rate/random-network/d10
# cp -r scenario/random-network/d10 /Users/khoihd/Documents/workspace/CP-19/rate=$rate/random-network/
