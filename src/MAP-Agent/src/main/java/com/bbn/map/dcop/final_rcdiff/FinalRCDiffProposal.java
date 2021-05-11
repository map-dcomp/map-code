package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

import static com.bbn.map.dcop.AbstractDcopAlgorithm.compareDouble;


/**
 * @author khoihd
 *
 */
public final class FinalRCDiffProposal implements FinalRCDiffMessageContent, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -6423787292121714891L;

    private final RegionIdentifier sender;
    
    private final RegionIdentifier root;
    
    private final RegionIdentifier sink;
    
//    private final int hop;
    
    private final FinalRCDiffTuple tuple;
    
    private final double proposalCapacity;
    
    private static int counter = 0;
    
    private final int objectCounter;
    
    private final LocalDateTime timeStamp;
    
    /**
     * @param sender .
     * @param root .
     * @param sink .
     * @param tuple .
     * @param proposalCapacity .
     * @return a new FinalRCDiffProposal object
     */
    public static FinalRCDiffProposal of(RegionIdentifier sender, RegionIdentifier root, RegionIdentifier sink, FinalRCDiffTuple tuple, double proposalCapacity) {
        return new FinalRCDiffProposal(sender, root, sink, tuple, proposalCapacity);
    }
    
    /**
     * @param object .
     * @return a deep copy of the object
     */
    public static FinalRCDiffProposal deepCopy(FinalRCDiffProposal object) {
        return new FinalRCDiffProposal(object.getSender(), object.getRoot(), object.getSink(), object.getTuple(), object.getProposalCapacity());
    }
    
    /**
     * @param object .
     * @param region .
     * @return a deep copy of the object which has sender replaced by region
     */
    public static FinalRCDiffProposal replaceSender(FinalRCDiffProposal object, RegionIdentifier region) {
        return new FinalRCDiffProposal(region, object.getRoot(), object.getSink(), object.getTuple(), object.getProposalCapacity());
    }
    
    /**
     * @param object .
     * @param load .
     * @return a deep copy of the object which has proposalCapacity replaced by load
     */
    public static FinalRCDiffProposal replaceLoad(FinalRCDiffProposal object, double load) {
        return new FinalRCDiffProposal(object.getSender(), object.getRoot(), object.getSink(), object.getTuple(), load);
    }
    
    /**
     * @param object .
     * @param tuple .
     * @return a deep copy of the object which has proposalCapacity replaced by load
     */
    public static FinalRCDiffProposal replaceTuple(FinalRCDiffProposal object, FinalRCDiffTuple tuple) {
        return new FinalRCDiffProposal(object.getSender(), object.getRoot(), object.getSink(), tuple, object.getProposalCapacity());
    }
    
    /**
     * @return an empty message
     */
    public static FinalRCDiffMessageContent emptyProposal() {
        return new FinalRCDiffProposal(null, null, null, null, -Double.MAX_VALUE);
    }
    
    private FinalRCDiffProposal(RegionIdentifier sender, RegionIdentifier root, RegionIdentifier sink, FinalRCDiffTuple tuple, double proposalCapacity) {
        this.sender = sender;
        this.root = root;
        this.sink = sink;
        this.tuple = tuple;
        this.proposalCapacity = proposalCapacity;
        this.timeStamp = LocalDateTime.now();
        
        this.objectCounter = counter++;
        // don't include anything that does a fuzzy match in equals
        this.hashCode = Objects.hash(tuple, sender, root, sink);
    }

    /**
     * @return sender
     */
    public RegionIdentifier getSender() {
        return sender;
    }

    /**
     * @return root
     */
    public RegionIdentifier getRoot() {
        return root;
    }

    /**
     * @return sink
     */
    public RegionIdentifier getSink() {
        return sink;
    }

    /**
     * @return proposalCapacity
     */
    public double getProposalCapacity() {
        return proposalCapacity;
    }
    
    /**
     * @return true if the proposal is empty
     */
    public boolean isEmptyProposal() {
        return this.equals(FinalRCDiffProposal.emptyProposal());
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
        FinalRCDiffProposal other = (FinalRCDiffProposal) obj;
        if (this.hashCode != other.hashCode) {
            return false;
        } else {
            return Objects.equals(tuple, other.tuple) 
                    && Objects.equals(sender, other.sender)
                    && compareDouble(proposalCapacity, other.getProposalCapacity()) == 0
                    && Objects.equals(root, other.root) && Objects.equals(sink, other.sink);
        }
    }

    @Override
    public String toString() {
        return "FinalRCDiffProposal [sender=" + sender + ", root=" + root + ", sink=" + sink + ", tuple=" + tuple
                + ", proposalCapacity=" + proposalCapacity + ", objectCounter=" + objectCounter + ", timeStamp="
                + timeStamp + "]";
    }

    /**
     * @return tuple
     */
    public FinalRCDiffTuple getTuple() {
        return tuple;
    }

    /**
     * @return timeStamp
     */
    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }
}
