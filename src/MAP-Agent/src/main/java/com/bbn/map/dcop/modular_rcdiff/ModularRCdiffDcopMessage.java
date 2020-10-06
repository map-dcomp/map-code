package com.bbn.map.dcop.modular_rcdiff;

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
public class ModularRCdiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 8234346675672168583L;

    /**
     * @author khoihd
     *
     */
    public enum ModularRCdiffMessageType {
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
        
    private final Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> messageTypeMap = new HashMap<>();

    
    /**
     *  Default constructor.
     */
    public ModularRCdiffDcopMessage() {
        super();
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public ModularRCdiffDcopMessage(ModularRCdiffDcopMessage object) {
        for (Entry<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> entry : object.getMessageTypeMap().entrySet()) {
            messageTypeMap.put(entry.getKey(), new HashSet<>());
            
            for (ModularRCdiffLoadMap loadMap : entry.getValue()) {
                messageTypeMap.get(entry.getKey()).add(new ModularRCdiffLoadMap(loadMap));
            }
        }
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param msgType .
     * @param msg .
     */
    public ModularRCdiffDcopMessage(ModularRCdiffMessageType msgType, ModularRCdiffLoadMap msg) {
        Set<ModularRCdiffLoadMap> msgSet = new HashSet<>();
        msgSet.add(msg);
        this.messageTypeMap.put(msgType, msgSet);
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param messageTypeMap .
     */
    public ModularRCdiffDcopMessage(Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> messageTypeMap) {
        setMessageTypeMap(messageTypeMap);
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ModularRCdiffDcopMessage other = (ModularRCdiffDcopMessage) obj;
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
    public Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> getMessageTypeMap() {
        return messageTypeMap;
    }

    /**
     * @param messageTypeMap the messageTypeMap to set
     */
    public void setMessageTypeMap(Map<ModularRCdiffMessageType, Set<ModularRCdiffLoadMap>> messageTypeMap) {
        messageTypeMap.forEach((key, set) -> this.messageTypeMap.put(key, new HashSet<>(set)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ModularRCdiffDcopMessage [messageTypeMap=");
        builder.append(messageTypeMap);
        builder.append("]");
        return builder.toString();
    }
    
}