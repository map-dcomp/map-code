/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
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
package com.bbn.map.ChartGeneration;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * Class for outputting GNU plot rectangle files for indicating plan change
 * times on the generated charts.
 * 
 * @author awald
 *
 */
public class GnuPlotPrinter implements Flushable, Closeable
{
    private static final Logger LOGGER = LogManager.getLogger(GnuPlotPrinter.class);
    private static final long[] ALTERNATING_LABEL_Y_COORDINATES =
    { 10, 12 };
    private static final String[] ALTERNATING_RECTANGLE_COLORS =
    { "red", "blue" };

    private Appendable out;

    private long endTime;
    private long prevTime = 0;
    private int n = 0;
    private String labelPrefix;

    private Map<?, ?> prevMap = null;

    /**
     * Constructs a new printer for outputting GNU plot files.
     * 
     * @param out
     *            The destination for the outputted GNU plot text.
     * @param labelPrefix
     *            The prefix for each label to be output.
     * @param endTime
     *            The last time that will be used in output.
     */
    public GnuPlotPrinter(Appendable out, String labelPrefix, long endTime)
    {
        this.out = out;
        this.endTime = endTime;
        this.labelPrefix = labelPrefix;

        LOGGER.info("Start file '{}' with end time {} and label prefix '{}'", out, endTime, labelPrefix);
    }

    @Override
    public void close() throws IOException
    {
        if (out instanceof Closeable)
            ((Closeable) out).close();
    }

    @Override
    public void flush() throws IOException
    {
        if (out instanceof Flushable)
            ((Flushable) out).flush();
    }

    /**
     * Outputs an entry into the GNU plot file for a single service.
     * 
     * @param <K>
     *            The type of key in the plan Map.
     * @param <V>
     *            The type of value in the plan Map.
     * @param time
     *            The time of the current event.
     * @param nextTime
     *            The time of the next event.
     * @param map
     *            A Map with plan information to be placed in the label.
     * @throws IOException
     *             if there was an issue outputting GNU plot text
     */
    public <K, V extends Number> void printEntry(long time, long nextTime, Map<K, V> map) throws IOException
    {
        printEntry(time, nextTime, labelPrefix, map);
    }

    private <K, V extends Number> void printEntry(long time, long nextTime, String prefix, Map<K, V> map)
            throws IOException
    {
        LOGGER.debug("Print entry for time {}: {}", time, map);

        out.append(formatEntry(time, nextTime, prefix, map));
        prevMap = map;
        prevTime = time;
        n++;
    }

    /**
     * Outputs an entry into the GNU plot file for multiple services.
     * 
     * @param <K>
     *            The type of key in a service's plan Map.
     * @param <V>
     *            The type of value in a service's plan Map.
     * @param time
     *            The time of the current event.
     * @param nextTime
     *            The time of the next event.
     * @param map
     *            A Map with plan information to be placed in the label for a set of services.
     * @throws IOException
     *             if there was an issue outputting GNU plot text
     */
    public <K, V extends Number> void printServiceEntry(long time, long nextTime,
            Map<ServiceIdentifier<?>, Map<K, V>> map) throws IOException
    {
        printServiceEntry(time, nextTime, labelPrefix, map);
    }

    private <K, V extends Number> void printServiceEntry(long time, long nextTime, String prefix,
            Map<ServiceIdentifier<?>, Map<K, V>> map) throws IOException
    {
        LOGGER.debug("Print entry for time {}: {}", time, map);

        out.append(formatServiceEntry(time, nextTime, prefix, map));
        prevMap = map;
        prevTime = time;
        n++;
    }

    private <K, V extends Number> String formatServiceEntry(long time, long nextTime, String prefix,
            Map<ServiceIdentifier<?>, Map<K, V>> map)
    {
        StringBuilder label = new StringBuilder();
        label.append(prefix);

        map.forEach((service, servicePlan) ->
        {
            label.append("\\n");

            label.append(service.toString()
                    .replaceAll(Pattern.quote("AppCoordinates {com.bbn, ") + "|" + Pattern.quote("}"), "")
                    .replaceAll(", ", "_"));

            label.append(" \\\\{");

            List<K> keys = new ArrayList<>();
            keys.addAll(servicePlan.keySet());
            Collections.sort(keys, new Comparator<K>()
            {
                @Override
                public int compare(K o1, K o2)
                {
                    return (o1.toString().compareTo(o2.toString()));
                }
            });

            for (int n = 0; n < keys.size(); n++)
            {
                K key = keys.get(n);

                if (n > 0)
                    label.append(",");

                label.append(String.format("%s:%.3f", key.toString(), servicePlan.get(key).floatValue()));
            }

            label.append("\\\\}");
        });

        return formatEntry(time, nextTime, label.toString());
    }

    private <K, V extends Number> String formatEntry(long time, long nextTime, String prefix, Map<K, V> map)
    {
        StringBuilder label = new StringBuilder();
        label.append(prefix);

        List<K> keys = new ArrayList<>();
        keys.addAll(map.keySet());
        Collections.sort(keys, new Comparator<K>()
        {
            @Override
            public int compare(K o1, K o2)
            {
                return (o1.toString().compareTo(o2.toString()));
            }
        });

        label.append("\\\\{");

        for (int n = 0; n < keys.size(); n++)
        {
            K key = keys.get(n);

            if (n > 0)
                label.append(",");

            label.append(String.format("%s:%.3f", key.toString(), map.get(key).floatValue()));
        }

        label.append("\\\\}");

        return formatEntry(time, nextTime, label.toString());
    }

    private String formatEntry(long time, long nextTime, String label)
    {
        String color = ALTERNATING_RECTANGLE_COLORS[n % ALTERNATING_RECTANGLE_COLORS.length];
        long labelPosX = (time + nextTime) / 2;
        long labelPosY = ALTERNATING_LABEL_Y_COORDINATES[n % ALTERNATING_LABEL_Y_COORDINATES.length];

        StringBuilder entry = new StringBuilder();
        entry.append(formatRectString(color, time, endTime));
        entry.append(formatLabelString(label, labelPosX, labelPosY));

        entry.append(System.lineSeparator());

        return entry.toString();
    }

    private String formatRectString(String color, long fromX, long toX)
    {
        return String.format(
                "set obj rect fc rgb '%s' fs solid 0.1 from %d, graph 0 to %d, graph 1" + System.lineSeparator(), color,
                fromX, toX);
    }

    private String formatLabelString(String label, long labelPosX, long labelPosY)
    {
        return String.format("set label \"%s\" at %d,%d center font 'Verdana,7'" + System.lineSeparator(), label,
                labelPosX, labelPosY);
    }
}
