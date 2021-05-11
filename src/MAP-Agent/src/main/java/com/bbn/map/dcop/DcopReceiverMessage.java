package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.dcop.acdiff.ACdiffDcopMessage;
import com.bbn.map.dcop.cdiff.CdiffDcopMessage;
import com.bbn.map.dcop.final_rcdiff.FinalRCDiffDcopMessage;
import com.bbn.map.dcop.modular_acdiff.ModularACdiffDcopMessage;
import com.bbn.map.dcop.modular_rcdiff.ModularRCdiffDcopMessage;
import com.bbn.map.dcop.rcdiff.RCdiffDcopMessage;
import com.bbn.map.dcop.rdiff.RdiffDcopMessage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * Messages of a region in an iteration
 * Containing a mapping receiver -> AbstractDcopMessage
 */
/**
 * @author khoihd
 *
 */
public class DcopReceiverMessage implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 8086099048703695161L;

    private RegionIdentifier sender = null;
    
    /**
     *  Contain the last iteration of the previous DCOP round
     */
    private int iteration = -1;

    /**
     * A mapping from receiver -> AbstractDcopMessage
     */
    private final Map<RegionIdentifier, GeneralDcopMessage> receiverMessageMap = new HashMap<>(); 
    
    
    /**
     * Default constructor.
     */
    public DcopReceiverMessage() {}
    
    /**
     * Copy constructor.
     * 
     * @param object
     *          the object to be copied
     * @param algorithm
     *          current DCOP algorithm
     */
    public DcopReceiverMessage(DcopReceiverMessage object, DcopAlgorithm algorithm) {        
        this.sender = object.getSender();
        this.iteration = object.getIteration();
        
        for (Entry<RegionIdentifier, GeneralDcopMessage> entry : object.getReceiverMessageMap().entrySet()) {
            GeneralDcopMessage msg = entry.getValue();
            
            if (DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(algorithm)) {
                RdiffDcopMessage rdiffMsg = (RdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new RdiffDcopMessage(rdiffMsg));
            } 
            else if (DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(algorithm)) {
                CdiffDcopMessage cdiffMsg = (CdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new CdiffDcopMessage(cdiffMsg));
            }
            else if (DcopAlgorithm.ASYNCHRONOUS_CDIFF.equals(algorithm)) {
                ACdiffDcopMessage acdiffMsg = (ACdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new ACdiffDcopMessage(acdiffMsg));
            }
            else if (DcopAlgorithm.RC_DIFF.equals(algorithm)) {
                RCdiffDcopMessage rcdiffMsg = (RCdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new RCdiffDcopMessage(rcdiffMsg));
            }
            else if (DcopAlgorithm.MODULAR_RCDIFF.equals(algorithm)) {
                ModularRCdiffDcopMessage rcdiffMsg = (ModularRCdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new ModularRCdiffDcopMessage(rcdiffMsg));
            }
            else if (DcopAlgorithm.MODULAR_ACDIFF.equals(algorithm)) {
                ModularACdiffDcopMessage rcdiffMsg = (ModularACdiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), new ModularACdiffDcopMessage(rcdiffMsg));
            }
            else if (DcopAlgorithm.FINAL_RCDIFF.equals(algorithm)) {
                FinalRCDiffDcopMessage finalRCdiffMsg = (FinalRCDiffDcopMessage) msg;
                receiverMessageMap.put(entry.getKey(), FinalRCDiffDcopMessage.deepCopy(finalRCdiffMsg));
            } 
            else {
                throw new RuntimeException("Unknown DCOP algorithm " + algorithm);
            }
        }
    }
    
    /**
     * @param sender
     *          sender
     * @param iteration
     *          iteration
     */
    public DcopReceiverMessage(RegionIdentifier sender, int iteration) {
        this.sender = sender;
        this.iteration = iteration;    
    }

    /**
     * @param receiver the receiver
     * @param msg the message needs to be added to the messageMap
     */
    public void addMessageToReceiver(RegionIdentifier receiver, GeneralDcopMessage msg) {
        receiverMessageMap.put(receiver, msg);
    }

//    @Override
//    public String toString() {
//        StringBuffer buf = new StringBuffer();
//        for (Entry<RegionIdentifier, AbstractDcopMessage> entry : receiverMessageMap.entrySet()) {
//            buf.append("Receiver: ");
//            buf.append(entry.getKey());
//            buf.append(": ");
//            buf.append(entry.getValue());
//            buf.append("\n");
//        }
//        
//        return buf.toString();
//    }


    /**
     * @return the messageMap
     */
    public Map<RegionIdentifier, GeneralDcopMessage> getReceiverMessageMap() {
        return receiverMessageMap;
    }

    /**
     * @param receiver receiver
     * @return the message sent to this receiver
     */
    public GeneralDcopMessage getMessageForThisReceiver(RegionIdentifier receiver) {
        return receiverMessageMap.get(receiver);
    }
    
    /**
     * @param receiver .
     * @param abstractDcopMsg .
     */
    public void setMessageToTheReceiver(RegionIdentifier receiver, GeneralDcopMessage abstractDcopMsg) {
        receiverMessageMap.put(receiver, abstractDcopMsg);
    }
    
    /**
     * @param receiver .
     * @param defaultDcopMsg .
     * @return message by this receiver or defaultDcopMsg
     */
    public GeneralDcopMessage getMessageForThisReceiverOrDefault(RegionIdentifier receiver, GeneralDcopMessage defaultDcopMsg) {
        return receiverMessageMap.getOrDefault(receiver, defaultDcopMsg);
    }
    
    /**
     * @param receiver
     *            receiver of the message
     * @return true if the message is sent to the receiver 
     */
    public boolean isSentTo(RegionIdentifier receiver) {
        return receiverMessageMap.containsKey(receiver);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final DcopReceiverMessage other = (DcopReceiverMessage) obj;
            return Objects.equals(getSender(), other.getSender()) //
                    && getIteration() == other.getIteration() //
                    && Objects.equals(getReceiverMessageMap(), other.getReceiverMessageMap())
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sender, iteration, receiverMessageMap);
    }

    /**
     * @return sender
     */
    public RegionIdentifier getSender() {
        return sender;
    }
    
    /**
     * @return the last iteration of the previous DCOP round
     */
    public int getIteration() {
        return iteration;
    }
    
    /**
     * @param sender .
     */
    public void setSender(RegionIdentifier sender) {
        this.sender = sender;
    }

    /**
     * @param iteration .
     */
    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MessagePerIteration [sender=");
        builder.append(sender);
        builder.append(", iteration=");
        builder.append(iteration);
        builder.append(", receiverMessageMap=");
        builder.append(receiverMessageMap);
        builder.append("]");
        return builder.toString();
    }
}