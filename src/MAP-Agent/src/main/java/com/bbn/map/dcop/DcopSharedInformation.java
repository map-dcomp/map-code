package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;


/**
 * Information shared between DCOP instances in other regions.
 */
public class DcopSharedInformation implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -2447204674044360290L;
    /** iterationDcopInfoMap of new algorithm.
     * 
     */
    private final Map<Integer, MessagesPerIteration> iterationDcopInfoMap = new HashMap<>();
    /** iterationDcopInfoMap for previous algorithm.
     * 
     */
    private final Map<Integer, DcopInfoMessage> itDcopInfoMap = new HashMap<>(); 

    /**
     *  Default constructor.
     */
    public DcopSharedInformation() {
    }

    /**
     * Copy constructor.
     * 
     * @param o
     *            is the object to copy
     */
    public DcopSharedInformation(DcopSharedInformation o) {
        if (null != o) {
            for (Map.Entry<Integer, MessagesPerIteration> entry : o.getIterationDcopInfoMap().entrySet()) {
                this.addIterationDcopInfo(entry.getKey(), new MessagesPerIteration(entry.getValue()));
            }
            for (Map.Entry<Integer, DcopInfoMessage> entry : o.getItDcopInfoMap().entrySet()) {
                this.addIterationDcopInfo(entry.getKey(), new DcopInfoMessage(entry.getValue()));
            }
        }
    }

    /**
     * @param iteration
     *            DCOP iteration
     * @return DcopInfoMessage
     */
    public MessagesPerIteration getMessageAtIteration(int iteration) {
        return iterationDcopInfoMap.get(iteration);

    }

    /**
     * @return a mapping from iteration to DcopInfoMessage
     */
    public Map<Integer, MessagesPerIteration> getIterationDcopInfoMap() {
        return iterationDcopInfoMap;
    }  


    /**
     * @param iteration
     *            DCOP iteration
     * @param dcopInfoMessage
     *            dcopInfoMessage to add
     */
    public void addIterationDcopInfo(int iteration, MessagesPerIteration dcopInfoMessage) {
        iterationDcopInfoMap.put(iteration, dcopInfoMessage);
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        if(DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            for (Map.Entry<Integer, MessagesPerIteration> entry : iterationDcopInfoMap.entrySet()) {
                buf.append("ITERATION " + entry.getKey() + " " + entry.getValue() + "\n");
            }
        else
            for (Map.Entry<Integer, DcopInfoMessage> entry : itDcopInfoMap.entrySet()) {
                buf.append("ITERATION " + entry.getKey() + " " + entry.getValue() + "\n");
            }
        return buf.toString();
    }

    /**
     * @param iteration
     *            iteration to remove message in the inbox
     */
    public void removeMessageAtIteration(int iteration) {
        if(DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm())) iterationDcopInfoMap.remove(iteration);
        else itDcopInfoMap.remove(iteration);
    }
    
    /**
     * Clear all messages from all iterations.
     */
    public void clear() {
        iterationDcopInfoMap.clear();
    }
    
    /**
     * Clear all messages from all iterations.
     */
    public void clearItDcopInfomap() {
        itDcopInfoMap.clear();
    }
    
    ///Methods for previous algorithm
    /**
     * @param iteration
     *            DCOP iteration
     * @param dcopInfoMessage
     *            dcopInfoMessage to add
     */
    public void addIterationDcopInfo(int iteration, DcopInfoMessage dcopInfoMessage) {
        itDcopInfoMap.put(iteration, dcopInfoMessage);
    }
    
    /**
     * @param iteration
     *            DCOP iteration
     * @return DcopInfoMessage
     * Replaces to getMessageAtIteration (different return type)
     */
    public DcopInfoMessage getMsgAtIteration(int iteration) {
        return itDcopInfoMap.get(iteration);
    }
    /**
     * @return a mapping from iteration to DcopInfoMessage
     */
    public Map<Integer, DcopInfoMessage> getItDcopInfoMap() {
        return itDcopInfoMap;
    }
        
    
    /** Check if the map is empty.
     * @return true if the map is empty
     */
    public boolean isEmptyNewAlgorithm() {
        return iterationDcopInfoMap.isEmpty();
    }
}