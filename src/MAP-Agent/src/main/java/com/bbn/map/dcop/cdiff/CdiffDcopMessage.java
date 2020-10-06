package com.bbn.map.dcop.cdiff;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.bbn.map.dcop.GeneralDcopMessage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * @author khoihd
 *
 */
public class CdiffDcopMessage implements GeneralDcopMessage, Serializable {    
    /**
     * 
     */
    private static final long serialVersionUID = -4453686907422133108L;

    /**
     * @author khoihd
     *
     */
    public enum CdiffMessageType {
        /**
         * Free message containing stage and parent.
         */
        EMPTY_MESSAGE,
        /**
         * Asking for help with the loadMap.
         */
        ASK_FOR_HELP,
        /**
         *  When a region agree to help.
         */
        AGREE_TO_HELP,
        /**
         *  When a region refuses to help.
         */
        REFUSE_TO_HELP,
        /**
         * Sending the aggregated plan to parent.
         */
        REPLY_WITH_PROPOSED_PLAN,
        /**
         * Parents send the plan to children.
         */
        FINAL_PLAN_TO_CHILDREN
    }
        
    /**
     * This could be <Hop -> Load> when children propose a plan to parent
     * Or <Service -> Load> when parent gives a plan to the children
     */
    private final Map<Object, Map<RegionIdentifier, Double>> cdiffLoadMap = new HashMap<>();
    
    private CdiffMessageType messageType;
    
    private int hop;
    
    /**
     *  Default constructor.
     */
    public CdiffDcopMessage() {
        super();
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public CdiffDcopMessage(CdiffDcopMessage object) {
        this.cdiffLoadMap.putAll(object.getCdiffLoadMap());
        this.messageType = object.getMessageType();
        this.hop = object.getHop();
    }
    
    /**
     * Create new CdiffDcopMessage with loadMap.
     * @param <T> is ServiceIdentifier or Integer.
     * @param loadMap .
     * @param messageType .
     * @param hop .
     */
    public <T> CdiffDcopMessage(Map<T, Map<RegionIdentifier, Double>> loadMap, CdiffMessageType messageType, int hop) {
        loadMap.forEach((key, map) -> this.cdiffLoadMap.put(key, new HashMap<>(map)));
        this.messageType = messageType;
        this.hop = hop;
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final CdiffDcopMessage other = (CdiffDcopMessage) obj;
            return Objects.equals(getCdiffLoadMap(), other.getCdiffLoadMap()) &&
                    Objects.equals(getMessageType(), other.getMessageType()) &&
                    Objects.equals(getHop(), other.getHop())
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(cdiffLoadMap, messageType, hop);
    }

    /**
     * @return the loadMap
     */
    public Map<Object, Map<RegionIdentifier, Double>> getCdiffLoadMap() {
        return cdiffLoadMap;
    }

    /**
     * @return the hop
     */
    public int getHop() {
        return hop;
    }

    /**
     * @param hop the hop to set
     */
    public void setHop(int hop) {
        this.hop = hop;
    }

    /**
     * @return the messageType
     */
    public CdiffMessageType getMessageType() {
        return messageType;
    }

    /**
     * @param messageType the messageType to set
     */
    public void setMessageType(CdiffMessageType messageType) {
        this.messageType = messageType;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CdiffDcopMessage [cdiffLoadMap=");
        builder.append(cdiffLoadMap);
        builder.append(", messageType=");
        builder.append(messageType);
        builder.append(", hop=");
        builder.append(hop);
        builder.append("]");
        return builder.toString();
    }
    
}