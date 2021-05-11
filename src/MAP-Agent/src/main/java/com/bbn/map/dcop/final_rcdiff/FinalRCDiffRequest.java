package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.time.LocalDateTime;
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
    
    private static int counter = 0;
    
    private final int objectCounter;
    
    private final LocalDateTime timeStamp;
            
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
        this.timeStamp = LocalDateTime.now();
        this.objectCounter = counter++;
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
        return root == null
                && loadMap.isEmpty()
                && hop == Integer.MAX_VALUE;
        // Do not use equals in case they're having same value but with different construction time
//        return this.equals(FinalRCDiffRequest.emptyRequest());
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
        StringBuilder builder = new StringBuilder();
        builder.append("FinalRCDiffRequest [root=");
        builder.append(root);
        builder.append(", loadMap=");
        builder.append(loadMap);
        builder.append(", hop=");
        builder.append(hop);
        builder.append(", objectCounter=");
        builder.append(objectCounter);
        builder.append(", timeStamp=");
        builder.append(timeStamp);
        builder.append("]");
        return builder.toString();
    }

    /**
     * @return timeStamp
     */
    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }
}
