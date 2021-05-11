package com.bbn.map.dcop.modular_acdiff;

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
public class ModularACdiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 2734822943748410156L;

    /**
     * @author khoihd
     *
     */
    public enum ModularACdiffMessageType {
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
        
    private final Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> messageTypeMap = new HashMap<>();

    
    /**
     *  Default constructor.
     */
    public ModularACdiffDcopMessage() {
        super();
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public ModularACdiffDcopMessage(ModularACdiffDcopMessage object) {
        object.getMessageTypeMap().forEach((msgType, set) -> {
            final Set<ModularACdiffLoadMap> setCopy = new HashSet<>();
            set.forEach(loadMap -> {
                setCopy.add(new ModularACdiffLoadMap(loadMap));
            });
            this.messageTypeMap.put(msgType, setCopy);
        });
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param msgType .
     * @param msg .
     */
    public ModularACdiffDcopMessage(ModularACdiffMessageType msgType, ModularACdiffLoadMap msg) {
        Set<ModularACdiffLoadMap> msgSet = new HashSet<>();
        msgSet.add(msg);
        this.messageTypeMap.put(msgType, msgSet);
    }
    
    /**
     * Create new AsynchronousCdiffDcopMessage with loadMap.
     * @param messageTypeMap .
     */
    public ModularACdiffDcopMessage(Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> messageTypeMap) {
        setMessageTypeMap(messageTypeMap);
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ModularACdiffDcopMessage other = (ModularACdiffDcopMessage) obj;
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
    public Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> getMessageTypeMap() {
        return messageTypeMap;
    }

    /**
     * @param messageTypeMap the messageTypeMap to set
     */
    public void setMessageTypeMap(Map<ModularACdiffMessageType, Set<ModularACdiffLoadMap>> messageTypeMap) {
        messageTypeMap.forEach((key, set) -> this.messageTypeMap.put(key, new HashSet<>(set)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ModularACdiffDcopMessage [messageTypeMap=");
        builder.append(messageTypeMap);
        builder.append("]");
        return builder.toString();
    }
    
}