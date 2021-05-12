package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

import static com.bbn.map.dcop.AbstractDcopAlgorithm.compareDouble;

/**
 * @author khoihd
 *
 */
public final class FinalRCDiffPlan implements FinalRCDiffMessageContent, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1956847852006797342L;
    
    private final RegionIdentifier root;
    
    private final double load;
    
    private final RegionIdentifier sink;
    
    private final RegionIdentifier sender;
    
    private final RegionIdentifier receiver;
    
    private final ServiceIdentifier<?> service;
    
    private final PlanType type;
    
    /**
     * @author khoihd
     *
     */
    enum PlanType {
        /**
         * Plan for children senders
         */
        OUTPUT,
        /**
         * Plan from parent
         */
        INPUT,
    }
    
    /**
     * @param root .
     * @param load .
     * @param sink .
     * @param sender .
     * @param receiver .
     * @param service .
     * @param type .
     * @return a new FinalRCDiffPlan object 
     */
    public static FinalRCDiffPlan of(RegionIdentifier root, double load, RegionIdentifier sink, RegionIdentifier sender, RegionIdentifier receiver, ServiceIdentifier<?> service, PlanType type) {
        return new FinalRCDiffPlan(root, load, sink, sender, receiver, service, type);
    }
    
    /**
     * @param object .
     * @return a deep copy of the provided object
     */
    public static FinalRCDiffPlan deepCopy(FinalRCDiffPlan object) {
        return new FinalRCDiffPlan(object.getRoot(), object.getLoad(), object.getSink(), object.getSender(), object.getReceiver(), object.getService(), object.getType());
    }
    
    /**
     * @param object .
     * @param type .
     * @return a deep copy of the provided object except for the object's type. The type of the returned copy is the provided type
     */
    public static FinalRCDiffPlan deepCopy(FinalRCDiffPlan object, PlanType type) {
        return new FinalRCDiffPlan(object.getRoot(), object.getLoad(), object.getSink(), object.getSender(), object.getReceiver(), object.getService(), type);
    }
    
    private FinalRCDiffPlan(RegionIdentifier root, double load, RegionIdentifier sink, RegionIdentifier sender, RegionIdentifier receiver, ServiceIdentifier<?> service, PlanType type) {
        this.root = root;
        this.load = load;
        this.sink = sink;
        this.sender = sender;
        this.receiver = receiver;
        this.service = service;
        this.type = type;
        // don't include anything that does a fuzzy comparison in equals
        this.hashCode = Objects.hash(root, sink, sender, receiver, service, type);
    }

    /**
     * @return root
     */
    public RegionIdentifier getRoot() {
        return root;
    }

    /**
     * @return load
     */
    public double getLoad() {
        return load;
    }

    /**
     * @return sink
     */
    public RegionIdentifier getSink() {
        return sink;
    }

    /**
     * @return sender
     */
    public RegionIdentifier getSender() {
        return sender;
    }

    /**
     * @return receiver
     */
    public RegionIdentifier getReceiver() {
        return receiver;
    }
    

    /**
     * @return service
     */
    public ServiceIdentifier<?> getService() {
        return service;
    }    
    
    /**
     * @return type
     */
    public PlanType getType() {
        return type;
    }

    private final int hashCode;
    
    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FinalRCDiffPlan other = (FinalRCDiffPlan) obj;
        if(this.hashCode != other.hashCode) {
            return false;
        }
        
        return compareDouble(load, other.load) == 0
                && Objects.equals(receiver, other.receiver) 
                && Objects.equals(root, other.root)
                && Objects.equals(sender, other.sender) 
                && Objects.equals(sink, other.sink)
                && Objects.equals(service, other.service)
                && type == other.getType();
    }

    @Override
    public String toString() {
        return "FinalRCDiffPlan [root=" + root + ", load=" + load + ", sink=" + sink + ", sender=" + sender
                + ", receiver=" + receiver + ", service=" + service + ", type=" + type + "]";
    }
}
