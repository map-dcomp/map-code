package com.bbn.map.dcop.modular_rcdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.map.dcop.AugmentedRoot;
import com.bbn.map.dcop.ServerClientService;

/**
 * @author khoihd
 *
 */
public class ModularRCdiffLoadMap implements Serializable {    
    /**
     * 
     */
    private static final long serialVersionUID = -1841355548610294657L;

    private AugmentedRoot augmentedRootID;
    
    private int hop;
    
    private final Map<Object, Map<RegionIdentifier, Double>> loadMap = new HashMap<>();
    
    private final Map<ServerClientService, Double> serverClientServiceMap = new HashMap<>();

    /** Default constructor.
     * 
     */
    public ModularRCdiffLoadMap() {
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public ModularRCdiffLoadMap(ModularRCdiffLoadMap object) {
        this(object.getAugmentedRootID(), object.getHop(), object.getLoadMap(), object.getServerClientServiceMap());
    }
    
    
    /**
     * @param augmentedRootID ID of the root .
     * @param hop .
     * @param loadMap .
     * @param serverClientServiceSet .
     * @param <A> .
     */
    public <A> ModularRCdiffLoadMap(AugmentedRoot augmentedRootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap, Map<ServerClientService, Double> serverClientServiceSet) {
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
            final ModularRCdiffLoadMap other = (ModularRCdiffLoadMap) obj;
            return Objects.equals(getAugmentedRootID(), other.getAugmentedRootID()) //
                && Objects.equals(getHop(), other.getHop()) //
                && Objects.equals(getLoadMap(), other.getLoadMap()) //
                && Objects.equals(getServerClientServiceMap(), other.getServerClientServiceMap()) //
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(augmentedRootID, hop, loadMap, serverClientServiceMap);
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
        builder.append("ModularRCdiffLoadMap [augmentedRootID=");
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



