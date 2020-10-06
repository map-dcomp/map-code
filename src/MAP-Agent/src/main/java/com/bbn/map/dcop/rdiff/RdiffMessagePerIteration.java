//package com.bbn.map.dcop.rdiff;
//
//import java.io.Serializable;
//
//import com.bbn.map.dcop.DcopReceiverMessage;
//import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
//
///**
// * @author khoihd
// *
// */
//public class RdiffMessagePerIteration extends DcopReceiverMessage implements Serializable {
//
//    /**
//     * 
//     */
//    private static final long serialVersionUID = 2679732291222149747L;
//    
//    /**
//     * Default constructor.
//     */
//    public RdiffMessagePerIteration() {
//        super();
//    }
//    
//    /**
//     * Copy constructor.
//     * @param object .
//     */
//    public RdiffMessagePerIteration(DcopReceiverMessage object) {
//        super(object);
//    }
//    
//    /**
//     * @param regionID
//     *          regionID
//     * @param iteration
//     *          iteration
//     */
//    public RdiffMessagePerIteration(RegionIdentifier regionID, int iteration) {
//        super(regionID, iteration);
//    }
//}