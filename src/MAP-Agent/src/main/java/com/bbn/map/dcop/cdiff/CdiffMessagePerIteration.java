//package com.bbn.map.dcop.cdiff;
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
//public class CdiffMessagePerIteration extends DcopReceiverMessage implements Serializable {
//
//    /**
//     * 
//     */
//    private static final long serialVersionUID = 2679732291222149747L;
//    
//    /**
//     * Default constructor.
//     */
//    public CdiffMessagePerIteration() {
//        super();
//    }
//    
//    /**
//     * Copy constructor.
//     * @param object .
//     */
//    public CdiffMessagePerIteration(DcopReceiverMessage object) {
//        super(object);
//    }
//    
//    /**
//     * @param regionID
//     *          regionID
//     * @param iteration
//     *          iteration
//     */
//    public CdiffMessagePerIteration(RegionIdentifier regionID, int iteration) {
//        super(regionID, iteration);
//    }
//
//}