package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.bbn.map.dcop.GeneralDcopMessage;

/**
 * @author khoihd
 *
 */
public final class FinalRCDiffDcopMessage implements GeneralDcopMessage, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 4869224455176767568L;

    /**
     * This is all the message types sent between DCOP agents.
     */
    enum FinalRCDiffMessageType {
        /**
         * Contains the network demand information propagated from the data center to the client pool 
         */
        SERVER_TO_CLIENT,
        /**
         * Request of overloaded region. Used in G block
         */
        G,
        /**
         * Send available capacity. Used in C block
         */
        C,
        /**
         * Shedding plan. Used in Clear block
         */
        CLR,
        /**
         * Data center tree information and Dcop run
         */
        TREE
    }
    
    private final Map<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> messageMap = new HashMap<>();
    
    /**
     * @return a message with empty messageMap
     */
    public static FinalRCDiffDcopMessage emptyMessage() {
        return new FinalRCDiffDcopMessage(new HashMap<>());
    }
    
    /**
     * @param messageMap .
     * @return a new FinalRCDiffDcopMessage object with messageMap
     */
    public static FinalRCDiffDcopMessage of(Map<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> messageMap) {
        return new FinalRCDiffDcopMessage(messageMap);
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     * @return a deep copy of the FinalRCDiffDcopMessage object
     */
    public static FinalRCDiffDcopMessage deepCopy(FinalRCDiffDcopMessage object) {        
        return new FinalRCDiffDcopMessage(object.getMessageMap());
    }
    
    private FinalRCDiffDcopMessage(Map<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> messageMap) {
        for (Entry<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> entry : messageMap.entrySet()) {
            this.messageMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
        }
    }
    
    /**
     * @param msgType .
     * @param messageContent .
     */
    public void addMessage(FinalRCDiffMessageType msgType, FinalRCDiffMessageContent messageContent) {
        messageMap.computeIfAbsent(msgType, k -> new HashSet<>()).add(messageContent);
    }

    /**
     * @return the dataCenterTree which is the only element of the set with the TREE message type
     */
    public FinalRCDiffTree getDataCenterTree() {
        if (messageMap.containsKey(FinalRCDiffMessageType.TREE)) {
            return (FinalRCDiffTree) messageMap.get(FinalRCDiffMessageType.TREE).iterator().next();
        }
        
        return FinalRCDiffTree.emptyTree();
    }
    
    /**
     * Delete current tree and add new tree for TREE message type for messageMap.
     * @param tree .
     */
    public void setDataCenterTree(FinalRCDiffTree tree) {
        messageMap.put(FinalRCDiffMessageType.TREE, new HashSet<>());
        messageMap.get(FinalRCDiffMessageType.TREE).add(tree);
    }

    /**
     * @return messageMap
     */
    public Map<FinalRCDiffMessageType, Set<FinalRCDiffMessageContent>> getMessageMap() {
        return messageMap;
    }

    /**
     * Clear all messages of this msgType in messageMap.
     * @param msgType .
     */
    public void clearMessages(FinalRCDiffMessageType msgType) {
        if (messageMap.containsKey(msgType)) {
            messageMap.get(msgType).clear();
        }
    }

    @Override
    public String toString() {
        return "FinalRCDiffDcopMessage [messageMap=" + messageMap + "]";
    }
    
    @Override 
    public int hashCode() {
        return messageMap.hashCode();
    }
    
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (null == o) {
            return false;
        } else if (this.getClass().equals(o.getClass())) {
            final FinalRCDiffDcopMessage other = (FinalRCDiffDcopMessage) o;
            return messageMap.equals(other.messageMap);
        } else {
            return false;
        }
    }
}



