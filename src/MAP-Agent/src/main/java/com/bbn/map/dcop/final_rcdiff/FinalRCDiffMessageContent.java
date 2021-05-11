package com.bbn.map.dcop.final_rcdiff;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author khoihd
 *
 */
public interface FinalRCDiffMessageContent {
    /**
     * Logger for the interface .
     */
    Logger LOGGER = LoggerFactory.getLogger(FinalRCDiffMessageContent.class);
    
    /**
     * @param object .
     * @return a deep copy of the object 
     */
    static FinalRCDiffMessageContent deepCopy(FinalRCDiffMessageContent object) {
        if (object instanceof FinalRCDiffServerToClient) {
            FinalRCDiffServerToClient casted = (FinalRCDiffServerToClient) object;
            return FinalRCDiffServerToClient.deepCopy(casted);
        }
        else if (object instanceof FinalRCDiffRequest) {
            FinalRCDiffRequest casted = (FinalRCDiffRequest) object;
            return FinalRCDiffRequest.deepCopy(casted);
        }
        else if (object instanceof FinalRCDiffProposal) {
            FinalRCDiffProposal casted = (FinalRCDiffProposal) object;
            return FinalRCDiffProposal.deepCopy(casted);
        }
        else if (object instanceof FinalRCDiffPlan) {
            FinalRCDiffPlan casted = (FinalRCDiffPlan) object;
            return FinalRCDiffPlan.deepCopy(casted);
        }
        else if (object instanceof FinalRCDiffTree) {
            FinalRCDiffTree casted = (FinalRCDiffTree) object;
            return FinalRCDiffTree.deepCopy(casted);
        }
        
        LOGGER.info("WARNING: Cannot find class of instance {} in FinalRCDiffMessageContent when running deepCopy", object);
        
        return object;
    }
    
    /**
     * @param object .
     * @return the time stamp of the object
     */
    static LocalDateTime getTimeStamp(FinalRCDiffMessageContent object) {
        if (object instanceof FinalRCDiffServerToClient) {
            FinalRCDiffServerToClient casted = (FinalRCDiffServerToClient) object;
            return casted.getTimeStamp();
        }
        else if (object instanceof FinalRCDiffRequest) {
            FinalRCDiffRequest casted = (FinalRCDiffRequest) object;
            return casted.getTimeStamp();
        }
        else if (object instanceof FinalRCDiffProposal) {
            FinalRCDiffProposal casted = (FinalRCDiffProposal) object;
            return casted.getTimeStamp();
        }
        else if (object instanceof FinalRCDiffPlan) {
            FinalRCDiffPlan casted = (FinalRCDiffPlan) object;
            return casted.getTimeStamp();
        }
        else if (object instanceof FinalRCDiffTree) {
            FinalRCDiffTree casted = (FinalRCDiffTree) object;
            return casted.getTimeStamp();
        }
        
        LOGGER.info("WARNING: Cannot find class of instance {} in FinalRCDiffMessageContent when running getTimeStamp", object);
        
        return LocalDateTime.now();
    }
}
