package com.bbn.map.dcop.modular_acdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * @author khoihd
 *
 */
public class ModularACdiffLoadMap implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -5687126397075062649L;

    private RegionIdentifier rootID;
    
    private int hop;
    
    private final Map<Object, Map<RegionIdentifier, Double>> loadMap = new HashMap<>();
    
    /** Default constructor.
     * 
     */
    public ModularACdiffLoadMap() {
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public ModularACdiffLoadMap(ModularACdiffLoadMap object) {
        this(object.getRootID(), object.getHop(), object.getLoadMap());
    }
    
    
    /**
     * @param rootID ID of the root .
     * @param hop .
     * @param loadMap .
     * @param <A> .
     */
    public <A> ModularACdiffLoadMap(RegionIdentifier rootID, int hop, Map<A, Map<RegionIdentifier, Double>> loadMap) {
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
            final ModularACdiffLoadMap other = (ModularACdiffLoadMap) obj;
            return Objects.equals(getRootID(), other.getRootID()) //
                && Objects.equals(getHop(), other.getHop()) //
                && Objects.equals(getLoadMap(), other.getLoadMap()) //
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(rootID, hop, loadMap);
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
     * @param rootID the RootID to set
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
        builder.append("ModularACdiffLoadMap [RootID=");
        builder.append(rootID);
        builder.append(", hop=");
        builder.append(hop);
        builder.append(", loadMap=");
        builder.append(loadMap);
        builder.append("]");
        return builder.toString();
    }
}



