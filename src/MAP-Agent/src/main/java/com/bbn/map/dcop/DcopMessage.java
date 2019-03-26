package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.Comparator;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * @author cwayllace Message to be sent for Aggregating Computing Approach to
 *         DCOP Algorithm
 */
public class DcopMessage implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private RegionIdentifier originalSender;
    private int hop;
    private int load;
    private double efficiency;
    private double latency;

    /**
     * @param hop
     *            hop
     * @param load
     *            load Message type 1 when asking for help
     */
    public DcopMessage(int hop, int load) {
        originalSender = null;
        this.hop = hop;
        this.load = load;
        this.efficiency = -1.0;
        this.latency = -1.0;
    }

    /**
     * @param sender
     *            original sender of the message
     * @param hop
     *            number of hopes from original sender
     * @param load
     *            load that is being asked for
     * @param efficiency
     *            send my efficiency
     * @param latency
     *            time to send messages to my parent Message type 2 when
     *            answering with help
     */
    public DcopMessage(RegionIdentifier sender, int hop, int load, double efficiency, double latency) {
        originalSender = sender;
        this.hop = hop;
        this.load = load;
        this.efficiency = efficiency;
        this.latency = latency;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "message: " + "originalSender=" + originalSender + ", hop=" + hop + ", load=" + load + ", efficiency="
                + efficiency + ", latency=" + latency;
    }

    /**
     * @return original sender
     */
    public RegionIdentifier getOriginalSender() {
        return originalSender;
    }

    /**
     * @param originalSender
     *            original sender
     */
    public void setOriginalSender(RegionIdentifier originalSender) {
        this.originalSender = originalSender;
    }

    /**
     * @return number of hops
     */
    public int getHop() {
        return hop;
    }

    /**
     * @param hop
     *            number of hops
     */
    public void setHop(int hop) {
        this.hop = hop;
    }

    /**
     * @return load
     */
    public int getLoad() {
        return load;
    }

    /**
     * @param load
     *            load
     */
    public void setLoad(int load) {
        this.load = load;
    }

    /**
     * @return efficiency
     */
    public double getEfficiency() {
        return efficiency;
    }

    /**
     * @param efficiency
     *            efficiency
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    /**
     * @return latency
     */
    public double getLatency() {
        return latency;
    }

    /**
     * @param latency
     *            latency
     */
    public void setLatency(double latency) {
        this.latency = latency;
    }
    

    /**
     * @return Comparator 
     *              sortByHop
     */
    public static Comparator<DcopMessage> getSortByHop() {
        return sortByHop;
    }

    /**
     * @param sortByHop1
     *              setter function of Comparator sortByHop
     */
    public static void setSortByHop(Comparator<DcopMessage> sortByHop1) {
        sortByHop = sortByHop1;
    }


    /**
     * @author cwayllace For the aggregation: lower number of hops is better
     */
    private static Comparator<DcopMessage> sortByHop = new Comparator<DcopMessage>() {
        public int compare(DcopMessage o1, DcopMessage o2) {
            final int before = -1;
            final int equal = 0;
            final int after = 1;
            if (o1.getHop() == o2.getHop())
                return equal;
            else if (o1.getHop() < o2.getHop())
                return before;
            else
                return after;
        }
    };
}



