package com.bbn.map.dcop.cdiff;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * @author khoihd
 *
 */
public class CdiffLoadMap implements Serializable {       
    /**
     * 
     */
    private static final long serialVersionUID = 2859234679819980554L;
    // CDIFF attributes
    private RegionIdentifier originalSender;
    private int hop;
    private Map<ServiceIdentifier<?>, Double> loadMap = new HashMap<>();
    private double efficiency;
    private double latency;
    private double availableLoad;
    
    /** Default constructor.
     * 
     */
    public CdiffLoadMap() {
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public CdiffLoadMap(CdiffLoadMap object) {
        this();

        this.originalSender = object.getOriginalSender();
        this.hop = object.getHop();
        this.loadMap.putAll(object.getLoadMap());
        this.efficiency = object.getEfficiency();
        this.latency = object.getLatency();
    }
    
    /**
     * @param hop
     *            hop
     * @param loadMap
     *            load Message type 1 when asking for help
     *            For prev algorithm
     */
    public CdiffLoadMap(int hop, Map<ServiceIdentifier<?>, Double> loadMap) {
        this();
        originalSender = null;
        this.hop = hop;
        this.loadMap.putAll(loadMap);
        this.efficiency = -1.0;
        this.latency = -1.0;
    }

    /**
     * @param sender
     *            original sender of the message
     * @param hop
     *            number of hopes from original sender
     * @param loadMap
     *            loadMap that is being asked for
     * @param efficiency
     *            send my efficiency
     * @param latency
     *            time to send messages to my parent Message type 2 when
     *            answering with help
     */
    public CdiffLoadMap(RegionIdentifier sender, int hop, Map<ServiceIdentifier<?>, Double> loadMap, double efficiency, double latency) {
        originalSender = sender;
        this.hop = hop;
        this.loadMap.putAll(loadMap);
        this.efficiency = efficiency;
        this.latency = latency;
    }
    
    /**
     * @param selfRegionID
     *              selfRegionID
     * @param hop2
     *              hop2
     * @param efficiency2
     *              efficiency2
     * @param latency
     *              latency
     */
    public CdiffLoadMap(RegionIdentifier selfRegionID, int hop2, double efficiency2, Double latency) {
        this.originalSender = selfRegionID;
        this.hop = hop2;
        this.efficiency = efficiency2;
        this.latency = latency;
    }

    /**
     * @param originalSender .
     * @param hop .
     * @param loadMap .
     */
    public CdiffLoadMap(RegionIdentifier originalSender, int hop, Map<ServiceIdentifier<?>, Double> loadMap) {
        this.originalSender = originalSender;
        this.hop = hop;
        this.loadMap.putAll(loadMap);
    }
    
    /**
     * @param originalSender .
     * @param hop .
     */
    public CdiffLoadMap(RegionIdentifier originalSender, int hop) {
        this.originalSender = originalSender;
        this.hop = hop;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final CdiffLoadMap other = (CdiffLoadMap) obj;
            return Objects.equals(getOriginalSender(), other.getOriginalSender()) //
                    && Objects.equals(getHop(), other.getHop()) //
                    && Objects.equals(getLoadMap(), other.getLoadMap()) //
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(originalSender, hop, loadMap, efficiency, latency, availableLoad);
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
    public Map<ServiceIdentifier<?>, Double> getLoadMap() {
        return loadMap;
    }

    /**
     * @param loadMap
     *            load
     */
    public void setLoadMap(Map<ServiceIdentifier<?>, Double> loadMap) {
        this.loadMap.putAll(loadMap);
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
    public static Comparator<CdiffLoadMap> getSortByHop() {
        return sortByHop;
    }

    /**
     * @param sortByHop1
     *              setter function of Comparator sortByHop
     */
    public static void setSortByHop(Comparator<CdiffLoadMap> sortByHop1) {
        sortByHop = sortByHop1;
    }

    /**
     * @return the availableLoad
     */
    public double getAvailableLoad() {
        return availableLoad;
    }

    /**
     * @param availableLoad the availableLoad to set
     */
    public void setAvailableLoad(double availableLoad) {
        this.availableLoad = availableLoad;
    }

    /**
     * @author cwayllace For the aggregation: lower number of hops is better
     */
    private static Comparator<CdiffLoadMap> sortByHop = new Comparator<CdiffLoadMap>() {
        public int compare(CdiffLoadMap o1, CdiffLoadMap o2) {
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



