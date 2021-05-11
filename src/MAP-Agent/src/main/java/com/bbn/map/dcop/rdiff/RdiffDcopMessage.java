package com.bbn.map.dcop.rdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.ServerClientService;
import com.bbn.map.dcop.ServerClientServiceLoad;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * @author khoihd
 *
 */
public class RdiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 4869224455176767568L;

    /**
     * This is all the message types sent between DCOP agents.
     */
    enum RdiffMessageType {
    /**
     * Telling a neighbor that I'm your parent and you're my children.
     */
    PARENT_TO_CHILDREN,
    /**
     * Sending load to the parent.
     */
    CHILDREN_TO_PARENT,
    /**
     * EMPTY message to non-parent and non-children.
     */
    EMPTY,
    /**
     * Write tree information of this DCOP iteration.
     */
    TREE
    }
    
    private final Map<RdiffMessageType, Set<ServerClientServiceLoad>> msgTypeClientServiceMap = new HashMap<>();
    
    private final Map<ServerClientService, RegionIdentifier> dataCenterTreeMap = new HashMap<>();
    
    private final Map<ServerClientService, RegionIdentifier> pathToClientMap = new HashMap<>();
    
    private final Set<ServiceIdentifier<?>> serviceSet = new HashSet<>();
    
    /** Default constructor.
     * 
     */
    public RdiffDcopMessage() {
       super();
    }
    
    /**
     * @param msgTypeClientServiceMap .
     * @param dataCenterTreeMap .
     * @param pathToClientMap .
     * @param serviceSet .
     */
    public RdiffDcopMessage(Map<RdiffMessageType, Set<ServerClientServiceLoad>> msgTypeClientServiceMap, Map<ServerClientService, RegionIdentifier> dataCenterTreeMap, Map<ServerClientService, RegionIdentifier> pathToClientMap, Set<ServiceIdentifier<?>> serviceSet) {
        for (Entry<RdiffMessageType, Set<ServerClientServiceLoad>> entry : msgTypeClientServiceMap.entrySet()) {
            Set<ServerClientServiceLoad> tupleSet = new HashSet<>();
            for (ServerClientServiceLoad tuple : entry.getValue()) {
                tupleSet.add(ServerClientServiceLoad.deepCopy(tuple));
            }
            this.msgTypeClientServiceMap.put(entry.getKey(), tupleSet);
        }
        
        
        for (Entry<ServerClientService, RegionIdentifier> entry : dataCenterTreeMap.entrySet()) {
            this.dataCenterTreeMap.put(new ServerClientService(entry.getKey()), entry.getValue());
        }
        
        for (Entry<ServerClientService, RegionIdentifier> entry : pathToClientMap.entrySet()) {
            this.pathToClientMap.put(new ServerClientService(entry.getKey()), entry.getValue());
        }
        
        this.serviceSet.addAll(serviceSet);
    }
    
    /**
     * @return service 
     */
    public Set<ServiceIdentifier<?>> getServiceSet() {
        return serviceSet;
    }

    /** Copy constructor.
     * @param object is the object to be copied
     */
    public RdiffDcopMessage(RdiffDcopMessage object) {
        this(object.getMsgTypeClientServiceMap(), object.getDataCenterTreeMap(), object.getPathToClientMap(), object.getServiceSet());
    }
    
    /**
     * @return the msgTypeClientServiceMap
     */
    Map<RdiffMessageType, Set<ServerClientServiceLoad>> getMsgTypeClientServiceMap() {
        return msgTypeClientServiceMap;
    }
    
    Set<ServerClientServiceLoad> getMessages(RdiffMessageType msgType) {
        return msgTypeClientServiceMap.getOrDefault(msgType, new HashSet<>());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final RdiffDcopMessage other = (RdiffDcopMessage) obj;
            return Objects.equals(getMsgTypeClientServiceMap(), other.getMsgTypeClientServiceMap()) //
                    && Objects.equals(getDataCenterTreeMap(), other.getDataCenterTreeMap())
                    && Objects.equals(getPathToClientMap(), other.getPathToClientMap())
                    && Objects.equals(getServiceSet(), other.getServiceSet())
               ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(msgTypeClientServiceMap, dataCenterTreeMap, pathToClientMap, serviceSet);
    }

    /**
     * @return the dataCenterTree
     */
    Map<ServerClientService, RegionIdentifier> getDataCenterTreeMap() {
        return dataCenterTreeMap;
    }
    
    /**
     * @return path to client 
     */
    public Map<ServerClientService, RegionIdentifier>  getPathToClientMap() {
        return pathToClientMap;
    }

    /**
     * @param msgType .
     * @param messageToSend .
     */
    void addMessage(RdiffMessageType msgType, ServerClientServiceLoad messageToSend) {
        msgTypeClientServiceMap.computeIfAbsent(msgType, k -> new HashSet<>()).add(messageToSend);
    }
    
    /**
     * @param msgType .
     * @param messageToSend .
     */
    void addMessage(RdiffMessageType msgType, Set<ServerClientServiceLoad> messageToSend) {
        msgTypeClientServiceMap.computeIfAbsent(msgType, k -> new HashSet<>()).addAll(messageToSend);
    }

    @Override
    public String toString() {
        return "RdiffDcopMessage [msgTypeClientServiceMap=" + msgTypeClientServiceMap + ", dataCenterTreeMap="
                + dataCenterTreeMap + ", pathToClientMap=" + pathToClientMap + ", serviceSet=" + serviceSet + "]";
    }
}



