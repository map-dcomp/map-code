package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bbn.map.dcop.DCOPService.Stage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * Information shared between DCOP instances in other regions.
 */
public class DcopSharedInformation implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -2447204674044360290L;
    private Map<Integer, DcopInfoMessage> iterationDcopInfoMap;

    /**
     *  Default constructor.
     */
    public DcopSharedInformation() {
        iterationDcopInfoMap = new HashMap<>();
    }

    /**
     * @param o
     *            is the object to copy
     */
    public DcopSharedInformation(DcopSharedInformation o) {
        this();

        if (o != null) {
            for (Map.Entry<Integer, DcopInfoMessage> entry : o.getIterationDcopInfoMap().entrySet()) {
                this.addIterationDcopInfo(entry.getKey(), new DcopInfoMessage(entry.getValue()));
            }
        }
    }

    /**
     * @param iteration
     *            DCOP iteration
     * @return DcopInfoMessage
     */
    public DcopInfoMessage getMessageAtIteration(int iteration) {
        return iterationDcopInfoMap.get(iteration);
    }

    /**
     * @return a mapping from iteration to DcopInfoMessage
     */
    public Map<Integer, DcopInfoMessage> getIterationDcopInfoMap() {
        return iterationDcopInfoMap;
    }

    /**
     * @param iterationDcopInfoMap
     *            iterationDcopInfoMap
     */
    public void setIterationDcopInfoMap(Map<Integer, DcopInfoMessage> iterationDcopInfoMap) {
        this.iterationDcopInfoMap = iterationDcopInfoMap;
    }

    /**
     * @param iteration
     *            DCOP interation
     * @param dcopInfoMessage
     *            dcopInfoMessage to add
     */
    public void addIterationDcopInfo(int iteration, DcopInfoMessage dcopInfoMessage) {
        iterationDcopInfoMap.put(iteration, dcopInfoMessage);
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        for (Map.Entry<Integer, DcopInfoMessage> entry : iterationDcopInfoMap.entrySet()) {
            buf.append("ITERATION " + entry.getKey() + " " + entry.getValue() + "\n");
        }
        return buf.toString();
    }

    /**
     * @param iteration
     *            iteration to remove message in the inbox
     */
    public void removeMessageAtIteration(int iteration) {
        iterationDcopInfoMap.remove(iteration);
    }
}