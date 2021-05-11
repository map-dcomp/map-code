package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

import static com.bbn.map.dcop.AbstractDcopAlgorithm.compareDouble;


/**
 * @author khoihd
 * 
 * This object is used to sort the proposal from children and compute the DCOP plans
 */
public final class FinalRCDiffTuple implements Serializable {    
    /**
     * 
     */
    private static final long serialVersionUID = -5773034856595436144L;

    private final double delay;
    
    private final double datarate;
    
    private final RegionIdentifier child;
    
    private final int hop;
    
    /**
     * @param delay .
     * @param datarate .
     * @param child .
     * @param hop .
     * @return a new FinalRCDiffTuple object
     */
    public static FinalRCDiffTuple of(double delay, double datarate, RegionIdentifier child, int hop) {
        return new FinalRCDiffTuple(delay, datarate, child, hop);
    }
    
    /**
     * @param input is the FinalRCDiffTuple to be copied
     * @return a deep copy object from the input
     */
    public static FinalRCDiffTuple deepCopy(FinalRCDiffTuple input) {
        return FinalRCDiffTuple.of(input.getDelay(), input.getDatarate(), input.getChild(), input.getHop());
    }
    
    /**
     * @param input .
     * @param delay .
     * @param datarate .
     * @return a new deep copy from the input with new values for rate and delay
     */
    public static FinalRCDiffTuple modifyRate(FinalRCDiffTuple input, double delay, double datarate) {
        return FinalRCDiffTuple.of(delay, datarate, input.getChild(), input.getHop());
    }
    
    private FinalRCDiffTuple(double delay, double datarate, RegionIdentifier child, int hop) {
        this.delay = delay;
        this.datarate = datarate;
        this.child = child;
        this.hop = hop;
    }

    /**
     * @return delay
     */
    public double getDelay() {
        return delay;
    }

    /**
     * @return datarate
     */
    public double getDatarate() {
        return datarate;
    }

    /**
     * @return child region
     */
    public RegionIdentifier getChild() {
        return child;
    }

    /**
     * @return number of hops from root
     */
    public int getHop() {
        return hop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(child, datarate, delay, hop);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof FinalRCDiffTuple))
            return false;
        FinalRCDiffTuple other = (FinalRCDiffTuple) obj;
        return Objects.equals(child, other.child)
                && compareDouble(datarate, other.datarate) == 0
                && compareDouble(delay, other.delay) == 0
                && hop == other.hop;
    }

    @Override
    public String toString() {
        return "FinalRCDiffTuple [delay=" + delay + ", datarate=" + datarate + ", child=" + child + ", hop=" + hop
                + "]";
    }
}
