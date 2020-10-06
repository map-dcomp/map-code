package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.protelis.vm.CodePath;

import com.bbn.map.AgentConfiguration;

/**
 * Information shared between DCOP instances in other regions.
 */
public class DcopSharedInformation implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -9057295256757549573L;

    private final Map<Integer, DcopReceiverMessage> iterationMessageMap = new HashMap<>();

    private DcopReceiverMessage asynchronousMessage = new DcopReceiverMessage();

    /**
     * Default constructor.
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
            for (Map.Entry<Integer, DcopReceiverMessage> entry : o.getIterationMessageMap().entrySet()) {
                DcopReceiverMessage msg = entry.getValue();
                this.putMessageAtIteration(entry.getKey(),
                        new DcopReceiverMessage(msg, AgentConfiguration.getInstance().getDcopAlgorithm()));
            }

            asynchronousMessage = new DcopReceiverMessage(o.getAsynchronousMessage(),
                    AgentConfiguration.getInstance().getDcopAlgorithm());
            // don't copy Protelis state as that will already be immutable or
            // a copy
        }
    }

    /**
     * @param iteration
     *            DCOP iteration
     * @return DcopInfoMessage
     */
    public DcopReceiverMessage getMessageAtIteration(int iteration) {
        return iterationMessageMap.get(iteration);

    }

    /**
     * @param iteration
     *            .
     * @param msgPerIteration
     *            .
     * @return MessagePerIteration or msgPerIteration
     */
    public DcopReceiverMessage getMessageAtIterationOrDefault(int iteration, DcopReceiverMessage msgPerIteration) {
        return iterationMessageMap.getOrDefault(iteration, msgPerIteration);
    }

    /**
     * @param iteration
     *            .
     * @return containsKey()
     * 
     */
    public boolean containMessageAtIteration(int iteration) {
        return iterationMessageMap.containsKey(iteration);
    }

    /**
     * @return a mapping from iteration to DcopInfoMessage
     */
    public Map<Integer, DcopReceiverMessage> getIterationMessageMap() {
        return iterationMessageMap;
    }

    /**
     * @param iteration
     *            .
     * @param msgPerIteration
     *            .
     */
    public void setMessageAtIteration(int iteration, DcopReceiverMessage msgPerIteration) {
        iterationMessageMap.put(iteration, msgPerIteration);
    }

    /**
     * @param iteration
     *            DCOP iteration
     * @param msgPerIteration
     *            dcopInfoMessage to add
     */
    public void putMessageAtIteration(int iteration, DcopReceiverMessage msgPerIteration) {
        iterationMessageMap.put(iteration, msgPerIteration);
    }

    /**
     * @param iteration
     *            iteration to remove message in the inbox
     */
    public void removeMessageAtIteration(int iteration) {
        iterationMessageMap.remove(iteration);
    }

    /**
     * Clear all messages from all iterations.
     */
    public void clear() {
        iterationMessageMap.clear();
    }

    /**
     * Check if the map is empty.
     * 
     * @return true if the map is empty
     */
    public boolean isIterationMessageMapEmpty() {
        return iterationMessageMap.isEmpty();
    }

    /**
     * Check if the inbox has tree message sent by this region.
     * 
     * @param treeIteration
     *            the iteration
     * @return true if the inbox contains tree message
     */
    public boolean hasDataCenterTreeMessage(int treeIteration) {
        return iterationMessageMap.containsKey(treeIteration);
    }

    /**
     * @return the asynchronousMessage
     */
    public DcopReceiverMessage getAsynchronousMessage() {
        return asynchronousMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DcopSharedInformation [iterationMessageMap=");
        builder.append(iterationMessageMap);
        builder.append(", asynchronousMessage=");
        builder.append(asynchronousMessage);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(iterationMessageMap, asynchronousMessage);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final DcopSharedInformation other = (DcopSharedInformation) obj;
            return Objects.equals(getIterationMessageMap(), other.getIterationMessageMap())
                    && Objects.equals(getAsynchronousMessage(), other.getAsynchronousMessage());
        }
    }

    /**
     * @param asynchronousMessage
     *            the asynchronousMessage to set
     */
    public void setAsynchronousMessage(DcopReceiverMessage asynchronousMessage) {
        this.asynchronousMessage = asynchronousMessage;
    }

    private Map<CodePath, Object> protelisState = null;

    /**
     * 
     * @param v
     *            the state to share with protelis
     */
    public void setProtelisState(final Map<CodePath, Object> v) {
        protelisState = v;
    }

    /**
     * 
     * @return see {@link #setProtelisState(Map)}
     */
    public Map<CodePath, Object> getProtelisState() {
        return protelisState;
    }
}