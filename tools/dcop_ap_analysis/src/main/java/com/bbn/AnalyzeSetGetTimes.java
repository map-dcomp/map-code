/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.ImmutablePair;

public class AnalyzeSetGetTimes {
    // stored data references
    private HashMap<String, HashMap<String, String>> getData = null;
    private HashMap<String, HashMap<String, List<String>>> setData;

    /*
     * Process log files analyze each node in graph
     */
    public AnalyzeSetGetTimes(File file) throws Exception {
        final LogParse logParse = new LogParse(file);
        getData = logParse.getServerGetData();
        setData = logParse.getServerSetData();

        graph.forEach((server, peers) -> {
            peers.forEach(peer -> {
                analyze(server, peer);
            });
        });

        final NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(2);

        successPerPair.forEach((key, success) -> {
            final AtomicInteger fail = failPerPair.getOrDefault(key, new AtomicInteger(0));

            System.out.format("%s -> %s: Unmatched count: %d%n", key.getLeft(), key.getRight(), fail.get());
            System.out.format("%s -> %s: Matched count: %d%n", key.getLeft(), key.getRight(), success.get());

            final int total = fail.get() + success.get();
            final double dropPercentage = (double) fail.get() / total * 100;
            System.out.format("Drop percentage: %s%%%n", format.format(dropPercentage));

        });

        final int unmatched = aifail.get();
        final int matched = aisuccess.get();
        final int total = unmatched + matched;
        final double dropPercentage = (double) unmatched / total * 100;
        System.out.println("Unmatched data count: " + unmatched + "\nMatched data count: " + matched);
        System.out.println("Drop percentage: " + format.format(dropPercentage) + "%");
    }

    private final AtomicInteger aifail = new AtomicInteger(0);

    private final AtomicInteger aisuccess = new AtomicInteger(0);

    private final Map<ImmutablePair<String, String>, AtomicInteger> failPerPair = new HashMap<>();
    private final Map<ImmutablePair<String, String>, AtomicInteger> successPerPair = new HashMap<>();

    private void analyze(String server, String peer) {
        final HashMap<String, String> writeData = getData.get(server);
        final List<String> readData = setData.get(peer).get(server);

        final ImmutablePair<String, String> key = ImmutablePair.of(server, peer);
        writeData.forEach((logtime, constructtime) -> {
            if (!readData.contains(constructtime)) {
                aifail.incrementAndGet();
                failPerPair.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

                System.out.printf("Receiver %s MissingConstruct %s Sender %s SenderLogTimeStamp %s\n", peer,
                        constructtime, server, logtime);
            } else {
                aisuccess.getAndIncrement();
                successPerPair.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            }
        });

    }

    /*
     * HC static peer map for chain setup
     */
    private static final Map<String, List<String>> graph;
    static {
        Map<String, List<String>> aMap = new HashMap<String, List<String>>();
        aMap.put("X", new ArrayList<>(Arrays.asList("A")));
        aMap.put("A", new ArrayList<>(Arrays.asList("X", "B")));
        aMap.put("B", new ArrayList<>(Arrays.asList("A", "C")));
        aMap.put("C", new ArrayList<>(Arrays.asList("B", "D")));
        aMap.put("D", new ArrayList<>(Arrays.asList("C")));
        graph = Collections.unmodifiableMap(aMap);
    }

    // give it a log directory
    public static void main(String[] args) {
        try {
            new AnalyzeSetGetTimes(new File(args[0]));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
