package com.bbn.map.dcop.acdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.utils.ComparisonUtils;

/**
 * @author khoihd
 *
 */
public class ACdiffLoadMap implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 3155793596042967122L;

    private RegionIdentifier rootID;
    
    private int hop;
    
    private final Map<Object, Map<RegionIdentifier, Double>> loadMap = new HashMap<>();

    /** Default constructor.
     * 
     */
    public ACdiffLoadMap() {
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public ACdiffLoadMap(ACdiffLoadMap object) {
        this.rootID = object.getRootID();
        this.hop = object.getHop();
        setLoadMap(object.getLoadMap());
    }
    
    
    /**
     * @param rootID ID of the root .
     * @param hop .
     * @param loadMap .
     * @param <A> .
     */
    public <A> ACdiffLoadMap(RegionIdentifier rootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap) {
        this.rootID = rootID;
        this.hop = hop;
        loadMap.forEach((key, map) -> this.loadMap.put(key, new HashMap<>(map)));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ACdiffLoadMap other = (ACdiffLoadMap) obj;
            return Objects.equals(getRootID(), other.getRootID()) //
                    && Objects.equals(getHop(), other.getHop()) //
                    && ComparisonUtils.doubleMapEquals2(getLoadMap(), other.getLoadMap(), AbstractDcopAlgorithm.DOUBLE_TOLERANCE) //
            ;
        }
    }
    
    @Override
    public int hashCode() {
        // don't include anything that does a fuzzy comparison in equals
        return Objects.hash(rootID, hop);
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
     * @return the rootID
     */
    public RegionIdentifier getRootID() {
        return rootID;
    }

    /**
     * @param rootID the rootID to set
     */
    public void setRootID(RegionIdentifier rootID) {
        this.rootID = rootID;
    }

    /**
     * @return the loadMap
     */
    public Map<Object, Map<RegionIdentifier, Double>> getLoadMap() {
        return loadMap;
    }

    /**
     * @param loadMap the loadMap to set
     */
    public void setLoadMap(Map<?, Map<RegionIdentifier, Double>> loadMap) {
        loadMap.forEach((key, map) -> this.loadMap.put(key, new HashMap<>(map)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ACdiffLoadMap [rootID=");
        builder.append(rootID);
        builder.append(", hop=");
        builder.append(hop);
        builder.append(", loadMap=");
        builder.append(loadMap);
        builder.append("]");
        return builder.toString();
    }
}



