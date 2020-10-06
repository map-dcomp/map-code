package com.bbn.map.dcop.rdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.map.dcop.ServerClientService;
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
    
    private Map<RdiffMessageType, Map<ServerClientService, Double>> msgTypeClientServiceMap = new HashMap<>();
    
    private Map<ServiceIdentifier<?>, RdiffTree> dataCenterTreeMap = new HashMap<>();
    
    /** Default constructor.
     * 
     */
    public RdiffDcopMessage() {
       super();
    }
    
    /**
     * @param dataCenterTreeMap .
     */
    public RdiffDcopMessage(Map<ServiceIdentifier<?>, RdiffTree> dataCenterTreeMap) {
        for (Entry<ServiceIdentifier<?>, RdiffTree> entry : dataCenterTreeMap.entrySet()) {
            this.dataCenterTreeMap.put(entry.getKey(), new RdiffTree(entry.getValue()));
        }
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public RdiffDcopMessage(RdiffDcopMessage object) {
        this();
        
        for (Entry<RdiffMessageType, Map<ServerClientService, Double>> msgTypeEntry : object.getMsgTypeClientServiceMap().entrySet()) {
            Map<ServerClientService, Double> clientServiceMap = msgTypeEntry.getValue();
            
            Map<ServerClientService, Double> cloneClientServiceMap = new HashMap<>();
            for (Entry<ServerClientService, Double> entry : clientServiceMap.entrySet()) {
                cloneClientServiceMap.put(new ServerClientService(entry.getKey()), entry.getValue());
            }
            
            msgTypeClientServiceMap.put(msgTypeEntry.getKey(), cloneClientServiceMap);
        }
        
        for (Entry<ServiceIdentifier<?>, RdiffTree> entry : object.getDataCenterTreeMap().entrySet()) {
            dataCenterTreeMap.put(entry.getKey(), new RdiffTree(entry.getValue()));
        }
    }
    
    /**
     * @return the msgTypeClientServiceMap
     */
    Map<RdiffMessageType, Map<ServerClientService, Double>> getMsgTypeClientServiceMap() {
        return msgTypeClientServiceMap;
    }

    /**
     * @param msgTypeClientServiceMap the msgTypeClientServiceMap to set
     */
    void setMsgTypeClientServiceMap(Map<RdiffMessageType, Map<ServerClientService, Double>> msgTypeClientServiceMap) {
        this.msgTypeClientServiceMap = msgTypeClientServiceMap;
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
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(msgTypeClientServiceMap, dataCenterTreeMap);
    }

    /**
     * @return the dataCenterTree
     */
    Map<ServiceIdentifier<?>, RdiffTree> getDataCenterTreeMap() {
        return dataCenterTreeMap;
    }

    /**
     * @param dataCenterTreeMap .
     */
    void setDataCenterTreeMap(Map<ServiceIdentifier<?>, RdiffTree> dataCenterTreeMap) {
        this.dataCenterTreeMap = dataCenterTreeMap;
    }

    /**
     * @param msgType .
     * @param messageToSend .
     */
    void addMessage(RdiffMessageType msgType, Map<ServerClientService, Double> messageToSend) {
        Map<ServerClientService, Double>  rootClientServiceMap = msgTypeClientServiceMap.getOrDefault(msgType, new HashMap<>());
        rootClientServiceMap.putAll(messageToSend);
        msgTypeClientServiceMap.put(msgType, messageToSend);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RdiffDcopMessage [msgTypeClientServiceMap=");
        builder.append(msgTypeClientServiceMap);
        builder.append(", dataCenterTreeMap=");
        builder.append(dataCenterTreeMap);
        builder.append("]");
        return builder.toString();
    }
}



