package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.bbn.map.dcop.ServerClientService;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * @author khoihd
 *
 */
public final class FinalRCDiffTree implements FinalRCDiffMessageContent, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 6972468303461334860L;
    
    private final Map<ServerClientService, RegionIdentifier> pathToClient = new HashMap<>();
    
    private final Set<ServiceIdentifier<?>> selfRegionServices = new HashSet<>();
    
    private static int counter = 0;
    
    private final int objectCounter;
    
    private final LocalDateTime timeStamp;
    
    
    /**
     * @param pathToClient .
     * @param selfRegionServices .
     * @return a FinalRCDiffTree object
     */
    public static FinalRCDiffTree of(Map<ServerClientService, RegionIdentifier> pathToClient, Set<ServiceIdentifier<?>> selfRegionServices) {
        return new FinalRCDiffTree(pathToClient, selfRegionServices);
    }
    
    /**
     * @return an empty tree
     */
    public static FinalRCDiffTree emptyTree() {
        return new FinalRCDiffTree(new HashMap<>(), new HashSet<>());
    }
    
    /**
     * @param object .
     * @return a deep copy
     */
    public static FinalRCDiffTree deepCopy(FinalRCDiffTree object) {
        return new FinalRCDiffTree(object.getPathToClient(), object.getSelfRegionServices());
    }
    
    
    @Override
    public int hashCode() {
        return Objects.hash(pathToClient, selfRegionServices);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FinalRCDiffTree other = (FinalRCDiffTree) obj;
        return Objects.equals(pathToClient, other.pathToClient)
                && Objects.equals(selfRegionServices, other.selfRegionServices);
    }

    

    @Override
    public String toString() {
        return "FinalRCDiffTree [pathToClient=" + pathToClient + ", selfRegionServices=" + selfRegionServices
                + ", objectCounter=" + objectCounter + ", timeStamp=" + timeStamp + "]";
    }

    private FinalRCDiffTree(Map<ServerClientService, RegionIdentifier> pathToClient, Set<ServiceIdentifier<?>> selfRegionServices) {
        this.pathToClient.putAll(pathToClient);
        this.selfRegionServices.addAll(selfRegionServices);
        this.timeStamp = LocalDateTime.now();
        this.objectCounter = counter++;
    }
    
    /**
     * @return pathToClient
     */
    public Map<ServerClientService, RegionIdentifier> getPathToClient() {
        return pathToClient;
    }
    
    /**
     * @return self region services
     */
    public Set<ServiceIdentifier<?>> getSelfRegionServices() {
        return selfRegionServices;
    }

    /**
     * @param tuple .s
     * @return true if the tree has this tuples
     */
    public boolean hasTree(ServerClientService tuple) {        
        return pathToClient.containsKey(tuple);
    }
    
    /**
     * @param tuple .
     * @return tree for this tuple
     */
    public RegionIdentifier getTree(ServerClientService tuple) {        
        return pathToClient.get(tuple);
    }
    
    /**
     * @param tuple .
     * @param child .
     */
    public void addTree(ServerClientService tuple, RegionIdentifier child) {
        if (!hasTree(tuple)) {
            pathToClient.put(tuple, child);
        }
    }
    
    /**
     * @param service .
     */
    public void addService(ServiceIdentifier<?> service) {
        selfRegionServices.add(service);
    }
    
    /**
     * @param services .
     */
    public void addServices(Set<ServiceIdentifier<?>> services) {
        selfRegionServices.addAll(services);
    }

    /**
     * @return timeStamp
     */
    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }
}