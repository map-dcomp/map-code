package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.bbn.map.dcop.DCOPService.Stage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * Information shared between DCOP instances in other regions.
 */
public class DcopInfoMessage implements Serializable {

    private static final long serialVersionUID = -2447204674044360290L;

    private RegionIdentifier sender;
    private int iteration;

    // Message list for the aggregated message. Messages type 1 and 2 have only one element in the list
    private Map<RegionIdentifier, List<DcopMessage>> messages; 
    private Stage stage;
    private RegionIdentifier parent;    
    /*
     * 0: only stage and parent.
     * 1: ask for help or forward. 
     * 2: reply with help, may be aggregated.
     * 3: send plan from root to children.
     * 4:send plan from parent (not root) to children
     */
    private int type;
    
    /**
     * Default constructor.
     */
    public DcopInfoMessage() {
        iteration = -1;
        messages = new HashMap<RegionIdentifier, List<DcopMessage>>();
        stage = Stage.FREE;
        type = -1;
    }

    /**
     * @param sender sender
     * @param receiver receiver
     * @param iteration iteration
     * @param stage stage
     * @param parent parent
     * @postcondition type = 0
     */
    public DcopInfoMessage(RegionIdentifier sender,
            RegionIdentifier receiver,
            int iteration,
            Stage stage,
            RegionIdentifier parent) {
        this();
        this.sender = sender;
        this.stage = stage;
        this.iteration = iteration;
        List<DcopMessage> l = new ArrayList<DcopMessage>();
        this.messages.put(receiver, l);
        this.parent = parent;
        type = 0;
    }

    /**
     * @param sender
     *            sender of this message even if it is forwarding
     * @param receiver
     *            receiver
     * @param message
     *            Message to send with load and hop
     * @param iteration
     *            iteration number (to synchronize)
     * @param serviceID
     *            servideID
     * @param stage
     *            stage
     * @param parent
     *            parent to send type 1 messages: Overloaded region asking for
     *            help or forwarding message (load,hop) and type 2 leaf
     *            answering with help
     * 
     */
    public DcopInfoMessage(RegionIdentifier sender,
            RegionIdentifier receiver,
            DcopMessage message,
            int iteration,
            ServiceIdentifier<?> serviceID,
            Stage stage,
            RegionIdentifier parent) {
        this();
        this.sender = sender;
        this.iteration = iteration;
//        this.serviceID = serviceID;
        List<DcopMessage> messageList = new ArrayList<DcopMessage>();
        messageList.add(message);
        this.messages.put(receiver, messageList);
        this.stage = stage;
        this.parent = parent;
        type = 1;
    }

    /**
     * @param sender
     *            who is answering with help
     * @param receiver
     *            receiver
     * @param aggregatedMessage
     *            List of messages received by children and aggregated to be
     *            sent to parent
     * @param iteration
     *            iteration
     * @param stage
     *            stage
     * @param parent
     *            parent Type 2 message answering with aggregated help
     */
    public DcopInfoMessage(RegionIdentifier sender,
            RegionIdentifier receiver,
            List<DcopMessage> aggregatedMessage,
            int iteration,
            Stage stage,
            RegionIdentifier parent) {
        this();
        this.sender = sender;
        this.iteration = iteration;
        this.messages.put(receiver, aggregatedMessage);
        this.stage = stage;
        this.parent = parent;
        type = 2;
    }

    /**
     * Copy constructor.
     * 
     * @param object
     *            copy constructor
     */
    public DcopInfoMessage(DcopInfoMessage object) {
        this();
        this.sender = object.getSender();
        this.iteration = object.getIteration();
        for (Map.Entry<RegionIdentifier, List<DcopMessage>> entry : object.getMessages().entrySet()) {
//            this.messages.put(entry.getKey(), entry.getValue());
            final List<DcopMessage> newValue = entry.getValue().stream().map(m -> new DcopMessage(m))
                    .collect(Collectors.toList());
            this.messages.put(entry.getKey(), newValue);
        }

        this.stage = object.stage;

        this.parent = object.getParent();
        this.type = object.getType();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "[Stage=" + stage + ", type=" + type + ", sender=" + sender + ", iteration=" + iteration + ", messages="
                + messages.toString() + ", parent=" + parent + "]";

    }

    /**
     * @param receiver
     *            receiver of the message
     * @param iteration
     *            iteration
     * @return true if the message is sent to the receiver in this iteration
     */
    public boolean isSentTo(RegionIdentifier receiver, int iteration) {
        return messages.containsKey(receiver) && this.iteration == iteration;
    }

    /**
     * @return stage
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * @param stage
     *            stage
     * 
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * @return parent
     */
    public RegionIdentifier getParent() {
        return parent;
    }

    /**
     * @param parent
     *            parent
     */
    public void setParent(RegionIdentifier parent) {
        this.parent = parent;
    }

    /**
     * @return sender
     */
    public RegionIdentifier getSender() {
        return sender;
    }

    /**
     * @param sender
     *            sender
     */
    public void setSender(RegionIdentifier sender) {
        this.sender = sender;
    }

    /**
     * @return iteration
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * @param iteration
     *            iteration
     */
    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    /**
     * @return <receiver,list of messages>
     */
    public Map<RegionIdentifier, List<DcopMessage>> getMessages() {
        return messages;
    }

    /**
     * @param message
     *            <receiver,list of messages>
     */
    public void setMessages(Map<RegionIdentifier, List<DcopMessage>> message) {
        this.messages = message;
    }

    /**
     * @return 0: only stage and parent, 1: ask for help or forward, 2: reply
     *         with help, may be aggregated, 3: send plan from root to children
     *         4:send plan from parent (not root) to children
     */
    public int getType() {
        return type;
    }

    /**
     * @param type
     *            0: only stage and parent, 1: ask for help or forward, 2: reply
     *            with help, may be aggregated, 3: send plan from root to
     *            children 4:send plan from parent (not root) to children
     */
    public void setType(int type) {
        this.type = type;
    }
    
    @Override
    public boolean equals(Object dcopInfoMsgObj) {
      // If the object is compared with itself then return true  
      if (dcopInfoMsgObj == this) {
          return true;
      }
      
      if (!(dcopInfoMsgObj instanceof DcopInfoMessage)) {
        return false;
      }
         
      DcopInfoMessage castedDcopInfoMsgObj = (DcopInfoMessage) dcopInfoMsgObj;
        
      return this.getSender().equals(castedDcopInfoMsgObj.getSender()) &&
              this.getIteration() == castedDcopInfoMsgObj.getIteration() &&
              this.getMessages().equals(castedDcopInfoMsgObj.getMessages()) &&
              this.getStage() == castedDcopInfoMsgObj.getStage() &&
              this.getParent() == castedDcopInfoMsgObj.getParent() &&
              this.getType() == castedDcopInfoMsgObj.getType();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sender, iteration, messages, stage, parent, type);
    }
}