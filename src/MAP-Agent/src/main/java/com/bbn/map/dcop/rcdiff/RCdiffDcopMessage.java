package com.bbn.map.dcop.rcdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.bbn.map.dcop.GeneralDcopMessage;

/**
 * @author khoihd
 *
 */
public class RCdiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 3400136283201179255L;

    /**
     * @author khoihd
     *
     */
    public enum RCdiffMessageType {
        /**
         * Contain load information. This type of message is sent from the server to the client that initiates the server demand.
         */
        SERVER_TO_CLIENT,
        /**
         * Asking for help with the loadMap.
         */
        ASK,
        /**
         *  Signal when a root is not overloaded anymore.
         */
        DONE,
        /**
         *  When a region refuses to help.
         */
        PROPOSE,
        /**
         * Parents send the final plan to children.
         */
        PLAN
    }
        
    private final Map<RCdiffMessageType, Set<RCdiffLoadMap>> messageTypeMap = new HashMap<>();

    
    /**
     *  Default constructor.
     */
    public RCdiffDcopMessage() {
        super();
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public RCdiffDcopMessage(RCdiffDcopMessage object) {
        for (Entry<RCdiffMessageType, Set<RCdiffLoadMap>> entry : object.getMessageTypeMap().entrySet()) {
            messageTypeMap.put(entry.getKey(), new HashSet<>());
            
            for (RCdiffLoadMap loadMap : entry.getValue()) {
                messageTypeMap.get(entry.getKey()).add(new RCdiffLoadMap(loadMap));
            }
        }
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param msgType .
     * @param msg .
     */
    public RCdiffDcopMessage(RCdiffMessageType msgType, RCdiffLoadMap msg) {
        Set<RCdiffLoadMap> msgSet = new HashSet<>();
        msgSet.add(msg);
        this.messageTypeMap.put(msgType, msgSet);
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param messageTypeMap .
     */
    public RCdiffDcopMessage(Map<RCdiffMessageType, Set<RCdiffLoadMap>> messageTypeMap) {
        setMessageTypeMap(messageTypeMap);
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final RCdiffDcopMessage other = (RCdiffDcopMessage) obj;
            return Objects.equals(getMessageTypeMap(), other.getMessageTypeMap());
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageTypeMap);
    }

    /**
     * @return the messageTypeMap
     */
    public Map<RCdiffMessageType, Set<RCdiffLoadMap>> getMessageTypeMap() {
        return messageTypeMap;
    }

    /**
     * @param messageTypeMap the messageTypeMap to set
     */
    public void setMessageTypeMap(Map<RCdiffMessageType, Set<RCdiffLoadMap>> messageTypeMap) {
        messageTypeMap.forEach((key, set) -> this.messageTypeMap.put(key, new HashSet<>(set)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RCdiffDcopMessage [messageTypeMap=");
        builder.append(messageTypeMap);
        builder.append("]");
        return builder.toString();
    }
    
}