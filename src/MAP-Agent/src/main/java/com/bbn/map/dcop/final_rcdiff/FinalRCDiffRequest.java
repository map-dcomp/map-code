package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.map.dcop.AbstractDcopAlgorithm;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.ComparisonUtils;

/**
 * @author khoihd
 *
 */
public final class FinalRCDiffRequest implements FinalRCDiffMessageContent, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 7669111473889178848L;

    private final RegionIdentifier root;
    
    private final Map<ServiceIdentifier<?>, Double> loadMap = new HashMap<>();
    
    private final int hop;
    
    /**
     * @return a FinalRCDiffRequest object with root=null, loadMap=empty map, hop = Integer.MAX_VALUE
     */
    public static FinalRCDiffRequest emptyRequest() {
        return new FinalRCDiffRequest(null, new HashMap<>(), Integer.MAX_VALUE);
    }
    
    /**
     * Constructor.
     * @param root is the root region
     * @param loadMap is the load map: service -> load
     * @param hop is the number of hop away from the root
     * @return a new FinalRCDiffRequest object
     */
    public static FinalRCDiffRequest of(RegionIdentifier root, Map<ServiceIdentifier<?>, Double> loadMap, int hop) {
        return new FinalRCDiffRequest(root, loadMap, hop);
    }
    
    /**
     * Deep copy from the given object.
     * @param object is the object to be copied
     * @return a deep copy from the object
     */
    public static FinalRCDiffRequest deepCopy(FinalRCDiffRequest object) {
        return new FinalRCDiffRequest(object.getRoot(), object.getLoadMap(), object.getHop());
    }
    
    private FinalRCDiffRequest(RegionIdentifier root, Map<ServiceIdentifier<?>, Double> loadMap, int hop) {
        this.root = root;
        this.loadMap.putAll(loadMap);
        this.hop = hop;
    }
    
    /**
     * @return root
     */
    public RegionIdentifier getRoot() {
        return root;
    }

    /**
     * @return loadMap
     */
    public Map<ServiceIdentifier<?>, Double> getLoadMap() {
        return loadMap;
    }

    /**
     * @return hop
     */
    public int getHop() {
        return hop;
    }
    
    /**
     * @param region .
     * @return true if this request comes from self region with root = self region ID
     */
    public boolean isRoot(RegionIdentifier region) {
        return region.equals(root) && hop == Integer.MAX_VALUE;
    }
    
    /**
     * @return true if the reques is empty
     */
    public boolean isEmptyRequest() {
        return this.equals(FinalRCDiffRequest.emptyRequest());
    }
    
    
    @Override
    public int hashCode() {
        // don't include anything that does a fuzzy comparison in equals
        return Objects.hash(hop, root);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            FinalRCDiffRequest other = (FinalRCDiffRequest) obj;
            return Objects.equals(root, other.getRoot())
                    && hop == other.getHop()
                    && ComparisonUtils.doubleMapEquals(loadMap, other.getLoadMap(), AbstractDcopAlgorithm.DOUBLE_TOLERANCE)
                    ;
        }
    }

    @Override
    public String toString() {
        return "FinalRCDiffRequest [root=" + root + ", loadMap=" + loadMap + ", hop=" + hop + "]";
    }

}
