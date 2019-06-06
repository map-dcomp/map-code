package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * Messages of a region in an iteration
 * Containing a mapping receiver -> DcopMessage
 */
/**
 * @author khoihd
 *
 */
public class MessagesPerIteration implements Serializable {

    private static final long serialVersionUID = -2447204674044360290L;

    private final RegionIdentifier sender;
    private final int iteration;
    /**
     * A mapping from receiver -> message
     */
    private final Map<RegionIdentifier, DcopMessage> messageMap = new HashMap<>(); 
    
   
    /**
     * Constructor.
     * @param sender sender 
     * @param iteration iteration
     */
    public MessagesPerIteration(RegionIdentifier sender, int iteration) {
        this.sender = sender;
        this.iteration = iteration;
    }
    
    /**
     * Copy constructor.
     * 
     * @param object
     *          the object to be copied
     */
    public MessagesPerIteration(MessagesPerIteration object) {
        this.sender = object.getSender();
        this.iteration = object.getIteration();
        
        for (Entry<RegionIdentifier, DcopMessage> entry : object.getMessageMap().entrySet()) {
            messageMap.put(entry.getKey(), new DcopMessage(entry.getValue()));
        }

    }
    
    /**
     * @param receiver the receiver
     * @param msg the message needs to be added to the messageMap
     */
    public void addMessageToReceiver(RegionIdentifier receiver, DcopMessage msg) {
        messageMap.put(receiver, msg);
    }

    @Override
    public String toString() {
        return "[sender=" + sender + ", iteration=" + iteration + ", messageMap=" + messageMap;
    }

    /**
     * @param receiver
     *            receiver of the message
     * @param iteration
     *            the iteration where the message is sent
     * @return true if the message is sent to the receiver in this iteration
     */
    public boolean isSentTo(RegionIdentifier receiver, int iteration) {
        return messageMap.containsKey(receiver) && this.iteration == iteration;
    }
    
    @Override
    public boolean equals(Object dcopInfoMsgObj) {
        // TODO
      // If the object is compared with itself then return true  
      if (dcopInfoMsgObj == this) {
          return true;
      }
      
      if (!(dcopInfoMsgObj instanceof MessagesPerIteration)) {
        return false;
      }
         
      MessagesPerIteration castedDcopInfoMsgObj = (MessagesPerIteration) dcopInfoMsgObj;
        
      return this.getSender().equals(castedDcopInfoMsgObj.getSender()) &&
              this.getIteration() == castedDcopInfoMsgObj.getIteration() &&
              this.getMessageMap().equals(castedDcopInfoMsgObj.getMessageMap());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sender, iteration, messageMap);
    }

    /**
     * @return the sender
     */
    public RegionIdentifier getSender() {
        return sender;
    }

    /**
     * @return the iteration
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * @return the messageMap
     */
    public Map<RegionIdentifier, DcopMessage> getMessageMap() {
        return messageMap;
    }

    /**
     * @param receiver receiver
     * @return the message sent to this receiver
     */
    public DcopMessage getMessageForThisReceiver(RegionIdentifier receiver) {
        return messageMap.get(receiver);
    }
}