package com.bbn.map.dcop.rcdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.utils.ComparisonUtils;
import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.map.dcop.AugmentedRoot;
import com.bbn.map.dcop.ServerClientService;

/**
 * @author khoihd
 *
 */
public class RCdiffLoadMap implements Serializable {    
    /**
     * 
     */
    private static final long serialVersionUID = -7246933283703478907L;

    private AugmentedRoot augmentedRootID;
    
    private int hop;
    
    private final Map<Object, Map<RegionIdentifier, Double>> loadMap = new HashMap<>();
    
    private final Map<ServerClientService, Double> serverClientServiceMap = new HashMap<>();

    /** Default constructor.
     * 
     */
    public RCdiffLoadMap() {
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public RCdiffLoadMap(RCdiffLoadMap object) {
        this.augmentedRootID = object.getAugmentedRootID();
        this.hop = object.getHop();
        object.getLoadMap().forEach((key, map) -> {
            this.loadMap.put(key, new HashMap<>(map));
        });
        object.getServerClientServiceMap().forEach((key, value) -> {
            final ServerClientService keyCopy = new ServerClientService(key);
            this.serverClientServiceMap.put(keyCopy, value);
        });
    }
    
    
    /**
     * @param augmentedRootID ID of the root .
     * @param hop .
     * @param loadMap .
     * @param serverClientServiceSet .
     * @param <A> .
     */
    public <A> RCdiffLoadMap(AugmentedRoot augmentedRootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap, Map<ServerClientService, Double> serverClientServiceSet) {
        this.augmentedRootID = augmentedRootID;
        this.hop = hop;
        loadMap.forEach((key, map) -> this.loadMap.put(key, new HashMap<>(map)));
        serverClientServiceSet.forEach((key, value) -> this.serverClientServiceMap.put(key, value));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final RCdiffLoadMap other = (RCdiffLoadMap) obj;
            return Objects.equals(getAugmentedRootID(), other.getAugmentedRootID()) //
                && getHop() == other.getHop() //
                && ComparisonUtils.doubleMapEquals2(getLoadMap(), other.getLoadMap(), AbstractDcopAlgorithm.DOUBLE_TOLERANCE) //
                && ComparisonUtils.doubleMapEquals(getServerClientServiceMap(), other.getServerClientServiceMap(), AbstractDcopAlgorithm.DOUBLE_TOLERANCE) //
            ;
        }
    }
    
    @Override
    public int hashCode() {
        // don't include anything that does a fuzzy comparison in equals
        return Objects.hash(augmentedRootID, hop);
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
    public AugmentedRoot getAugmentedRootID() {
        return augmentedRootID;
    }

    /**
     * @param augmentedRootID the augmentedRootID to set
     */
    public void setAugmentedRootID(AugmentedRoot augmentedRootID) {
        this.augmentedRootID = augmentedRootID;
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
        builder.append("RCdiffLoadMap [augmentedRootID=");
        builder.append(augmentedRootID);
        builder.append(", hop=");
        builder.append(hop);
        builder.append(", loadMap=");
        builder.append(loadMap);
        builder.append(", serverClientServiceMap=");
        builder.append(serverClientServiceMap);
        builder.append("]");
        return builder.toString();
    }

    /**
     * @return 
     * @return the serverClientServiceSet
     */
    public Map<ServerClientService, Double> getServerClientServiceMap() {
        return serverClientServiceMap;
    }
}



