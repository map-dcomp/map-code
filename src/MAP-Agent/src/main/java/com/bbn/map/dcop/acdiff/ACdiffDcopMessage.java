package com.bbn.map.dcop.acdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.bbn.map.dcop.GeneralDcopMessage;

/**
 * @author khoihd
 *
 */
public class ACdiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 3266796749479978906L;

    /**
     * @author khoihd
     *
     */
    public enum ACdiffMessageType {
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
        REFUSE,
        /**
         * Propose a plan to parent.
         */
        PROPOSE,
        /**
         * Parents send the final plan to children.
         */
        PLAN
    }
        
    private final Map<ACdiffMessageType, Set<ACdiffLoadMap>> messageTypeMap = new HashMap<>();

    
    /**
     *  Default constructor.
     */
    public ACdiffDcopMessage() {
        super();
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public ACdiffDcopMessage(ACdiffDcopMessage object) {
        object.getMessageTypeMap().forEach((msgType, set) -> {
            final Set<ACdiffLoadMap> setCopy = new HashSet<>();
            set.forEach(loadMap -> {
                setCopy.add(new ACdiffLoadMap(loadMap));
            });
            this.messageTypeMap.put(msgType, setCopy);
        });
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param msgType .
     * @param msg .
     */
    public ACdiffDcopMessage(ACdiffMessageType msgType, ACdiffLoadMap msg) {
        Set<ACdiffLoadMap> msgSet = new HashSet<>();
        msgSet.add(msg);
        this.messageTypeMap.put(msgType, msgSet);
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param messageTypeMap .
     */
    public ACdiffDcopMessage(Map<ACdiffMessageType, Set<ACdiffLoadMap>> messageTypeMap) {
        setMessageTypeMap(messageTypeMap);
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ACdiffDcopMessage other = (ACdiffDcopMessage) obj;
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
    public Map<ACdiffMessageType, Set<ACdiffLoadMap>> getMessageTypeMap() {
        return messageTypeMap;
    }

    /**
     * @param messageTypeMap the messageTypeMap to set
     */
    public void setMessageTypeMap(Map<ACdiffMessageType, Set<ACdiffLoadMap>> messageTypeMap) {
        messageTypeMap.forEach((key, set) -> this.messageTypeMap.put(key, new HashSet<>(set)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ACdiffDcopMessage [messageTypeMap=");
        builder.append(messageTypeMap);
        builder.append("]");
        return builder.toString();
    }
    
}