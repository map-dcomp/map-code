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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class LogParse {

    /*
     * Stored in set data structure.
     * 
     * Server name, e.g., A, B, or X (receiver Server set name, e.g., A, B, or X
     * (sender) List of construct timestamp in ms since epoch Zulu (time
     * constructed at sender)
     */
    HashMap<String, HashMap<String, List<String>>> serverSetData = null;

    /*
     * Stored in get data structure:
     * 
     * Server name, e.g., A, B, or X Log entry timestamp in ms since epoch Zulu
     * Construct timestamp in ms since epoch Zulu
     */
    HashMap<String, HashMap<String, String>> serverGetData = null;

    public HashMap<String, HashMap<String, List<String>>> getServerSetData() {
        return serverSetData;
    }

    public HashMap<String, HashMap<String, String>> getServerGetData() {
        return serverGetData;
    }

    /*
     * root directory to parse. 1. Walk it to find server directories matching
     * reg ex 2. Set up storage class 3. Walk server directories to process map
     * logs matching reg ex
     */
    public LogParse(final File rootDirectory) throws Exception {
        System.out.println("Starting log parse");
        Long s = System.currentTimeMillis();

        final HashMap<String, File> server2Directory = getServerList(rootDirectory);

        serverSetData = new HashMap<String, HashMap<String, List<String>>>();
        serverGetData = new HashMap<String, HashMap<String, String>>();

        server2Directory.forEach((k, v) -> {
            try {
                processLogs(k, v);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Long e = System.currentTimeMillis();

        System.out.printf("Log parse completed in: %dms\n", (e - s));
    }

    /*
     * locate server directories matching reg ex
     */
    private HashMap<String, File> getServerList(final File file) throws Exception {
        final File[] filterServers = file.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File file, String name) {
                return file.isDirectory() && name.matches("^[A-Z]server[0-9]*$");
            }
        });

        final HashMap<String, File> server2Directory = new HashMap<String, File>();

        for (File f : filterServers) {
            final String name = f.getName().split("s")[0];
            server2Directory.put(name, f);
        }

        return server2Directory;
    }

    /*
     * 1. init storage for server name 2. locate and process map agent logs for
     * specified server
     */
    private void processLogs(final String serverName, final File serverDir) throws Exception {

        HashMap<String, List<String>> setData = serverSetData.get(serverName);
        if (setData == null) {
            setData = new HashMap<String, List<String>>();
            serverSetData.put(serverName, setData);
        }

        HashMap<String, String> getData = serverGetData.get(serverName);
        if (getData == null) {
            getData = new HashMap<String, String>();
            serverGetData.put(serverName, getData);
        }

        final File[] filterLogs = serverDir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File file, String name) {
                final File f = new File(file, name);
                return f.isFile() && name.matches("^map-agent.*log$");
            }
        });

        for (File logFile : filterLogs) {
            processLog(setData, getData, logFile);
        }

    }

    /*
     * Walk log line by line checking for get and set messages. > it would help
     * to preprocess logs with grep.
     */
    private void processLog(final HashMap<String, List<String>> setData,
            final HashMap<String, String> getData,
            final File logFile) throws Exception {

        final BufferedReader br = new BufferedReader(new FileReader(logFile));
        String line;
        while ((line = br.readLine()) != null) {
            if (!inspectGet(line, getData)) {
                inspectSet(line, setData);
            }
        }
        br.close();
    }

    /*
     * Check and process a line for set data
     * 
     * 
     * 2020-10-15/21:07:48.176/+0000 [Node-Aserver03.map.dcomp] DEBUG
     * com.bbn.map.Controller.Aserver03.map.dcomp [] {}- Set new DCOP shared
     * information {
     * 
     * A =ImmutableDcopSharedInformation [ DcopSharedInformation
     * [iterationMessageMap={}, asynchronousMessage=MessagePerIteration
     * [sender=null, iteration=8, receiverMessageMap={A=FinalRCDiffDcopMessage
     * [messageMap={TREE=[FinalRCDiffTree [pathToClient={},
     * selfRegionServices=[]]]}], B=FinalRCDiffDcopMessage
     * [messageMap={G=[FinalRCDiffRequest [root=null, loadMap={},
     * hop=2147483647]], C=[FinalRCDiffProposal [sender=null, root=null,
     * sink=null, hop=2147483647, proposalCapacity=-1.7976931348623157E308]]}],
     * X=FinalRCDiffDcopMessage [messageMap={G=[FinalRCDiffRequest [root=null,
     * loadMap={}, hop=2147483647]], C=[FinalRCDiffProposal [sender=null,
     * root=null, sink=null, hop=2147483647,
     * proposalCapacity=-1.7976931348623157E308]]}]}], constructed
     * at=2020-10-15T21:05:58.526090] ],
     * 
     * B =ImmutableDcopSharedInformation [ DcopSharedInformation
     * [iterationMessageMap={}, asynchronousMessage=MessagePerIteration
     * [sender=null, iteration=8, receiverMessageMap={A=FinalRCDiffDcopMessage
     * [messageMap={C=[FinalRCDiffProposal [sender=null, root=null, sink=null,
     * hop=2147483647, proposalCapacity=-1.7976931348623157E308]],
     * G=[FinalRCDiffRequest [root=null, loadMap={}, hop=2147483647]]}],
     * B=FinalRCDiffDcopMessage [messageMap={TREE=[FinalRCDiffTree
     * [pathToClient={}, selfRegionServices=[]]]}], C=FinalRCDiffDcopMessage
     * [messageMap={C=[FinalRCDiffProposal [sender=null, root=null, sink=null,
     * hop=2147483647, proposalCapacity=-1.7976931348623157E308]],
     * G=[FinalRCDiffRequest [root=null, loadMap={}, hop=2147483647]]}]}],
     * constructed at=2020-10-15T21:05:56.991168] ],
     * 
     * X =ImmutableDcopSharedInformation [ DcopSharedInformation
     * [iterationMessageMap={}, asynchronousMessage=MessagePerIteration
     * [sender=null, iteration=8, receiverMessageMap={X=FinalRCDiffDcopMessage
     * [messageMap={TREE=[FinalRCDiffTree [pathToClient={},
     * selfRegionServices=[AppCoordinates {com.bbn, appP1N1, 1}, AppCoordinates
     * {com.bbn, appP2N1, 1}]]]}], A=FinalRCDiffDcopMessage
     * [messageMap={C=[FinalRCDiffProposal [sender=null, root=null, sink=null,
     * hop=2147483647, proposalCapacity=-1.7976931348623157E308]],
     * G=[FinalRCDiffRequest [root=null, loadMap={}, hop=2147483647]],
     * SERVER_TO_CLIENT=[FinalRCDiffServerToClient [server=X, client=D,
     * service=AppCoordinates {com.bbn, appP2N1, 1}, load=4.01247161300723],
     * FinalRCDiffServerToClient [server=X, client=D, service=AppCoordinates
     * {com.bbn, appP1N1, 1}, load=9.9680396077091]]}]}], constructed
     * at=2020-10-15T21:03:46.887693] ]}
     * 
     */
    final static String setMatch = "Set new DCOP shared information {";
    final static String tsMatch = "constructed at=";

    private boolean inspectSet(final String line, final HashMap<String, List<String>> setData) {

        // look for index of set message.
        int matchIdx = line.indexOf(setMatch);

        if (matchIdx > 0) { // it matches

            // convert log timestamp to ms
            // final String timeStamp = line.substring(0, line.indexOf(" "
            // )).replaceFirst("/", "T").replace("/+0000", "Z");
            // final Instant instant = Instant.parse(timeStamp);
            // final Long timeStampLong = instant.toEpochMilli();
            // System.out.println(timeStamp + " " + instant.toEpochMilli());

            // trim the line: right of set string
            final String cut = line.substring((matchIdx + setMatch.length())).trim();
            // System.out.println(cut);

            /*
             * lazy token pruning, e.g. recast into: A
             * 2020-10-15T21:05:58.526090] ], B 2020-10-15T21:05:56.991168] ], X
             * 2020-10-15T21:03:57.064517] ]}
             */
            final String[] tok = cut.replaceAll("=ImmutableDcopSharedInformation.*?constructed at=", ";").split(";");
            // for (String s : tok) {
            // System.out.println(s);
            // }

            // reassembly into region ts pairs
            String region = tok[0]; // first region

            for (int i = 1; i < tok.length; i++) {
                // convert constructed at ts to ms
                final String recvTime = tok[i].split("]")[0];

                List<String> regionData = setData.get(region);
                if (regionData == null) {
                    regionData = new LinkedList<String>();
                    setData.put(region, regionData);
                }

                regionData.add(recvTime);

                // next region. until last token.
                region = tok[i].substring(tok[i].length() - 1);
            }

            return true;
        }

        return false;
    }

    /*
     * Check and process a line for get data
     * 
     * 
     * 2020-10-15/21:03:47.178/+0000 [Node-Xserver03.map.dcomp] DEBUG
     * com.bbn.map.Controller.Xserver03.map.dcomp [] {}- Getting local DCOP
     * information ImmutableDcopSharedInformation[ DcopSharedInformation
     * [iterationMessageMap={}, asynchronousMessage=MessagePerIteration
     * [sender=null, iteration=8, receiverMessageMap={ A=FinalRCDiffDcopMessage
     * [messageMap={C=[FinalRCDiffProposal [sender=null, root=null, sink=null,
     * hop=2147483647, proposalCapacity=-1.7976931348623157E308]],
     * G=[FinalRCDiffRequest [root=null, loadMap={}, hop=2147483647]],
     * SERVER_TO_CLIENT=[FinalRCDiffServerToClient [ server=X, client=D,
     * service=AppCoordinates {com.bbn, appP1N1, 1}, load=9.9680396077091],
     * FinalRCDiffServerToClient [server=X, client=D, service=AppCoordinates
     * {com.bbn, appP2N1, 1}, load=4.01247161300723]]}],
     * 
     * X=FinalRCDiffDcopMessage [messageMap={TREE=[FinalRCDiffTree
     * [pathToClient={}, selfRegionServices=[AppCoordinates {com.bbn, appP1N1,
     * 1}, AppCoordinates {com.bbn, appP2N1, 1}]]]}]}], constructed
     * at=2020-10-15T21:03:46.887693]
     */
    private boolean inspectGet(String line, HashMap<String, String> getData) {
        if (line.matches(".*Getting local DCOP information.*")) {
            // get log line ts
            final String timeStamp = line.substring(0, line.indexOf(" "));

            // get constructed ts
            String constructTime = line.split(tsMatch)[1].split("]")[0];

            if (null != getData.put(timeStamp, constructTime)) {
                // for debugging. Jon: there are duplicates of each one.
                // System.out.printf("Warn: duplicate log timestamp: %s.
                // smashing last get entry. logline: '%s'\n", timeStamp , line);
            }

            return true;
        }

        return false;
    }
}