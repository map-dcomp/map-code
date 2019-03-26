package com.bbn.map.dcop;

/**
 * @author khoihd
 *
 */
public interface DcopInfo {
    /**
     * WAITING_FOR_MSG.
     */
    boolean WAITING_FOR_MSG = true;
    /**
     * Time for each agent to sleep in waiting for messages.
     */
    long SLEEPTIME_WAITING_FOR_MESSAGE = 500; // in miliseconds

    /**
     * TIME TO STOP WHILE LOOP.
     */
    long TIME_FOR_LOOP = 3000000; // in nanoseconds;

    /**
     * TIME_SEARCHING_NEIGHBORS.
     */
    long TIME_SEARCHING_NEIGHBORS = 15000; // in miliseconds

    /**
     * With this message type, regions CAN choose to send back to parent.
     */
    int MSG_NO_FORCE = 0;

    /**
     * With this message type, regions ARE NOT ALLOWED to send back to parent.
     */
    int MSG_FORCE = 1;

    /**
     * Message type in sending empty message for regions to count and stop
     * waiting forever.
     */
    int MSG_EMPTY = 3;

    /**
     * Message type in string.
     */
    String[] MSG_TYPES = { "MSG_NO_FORCE", "MSG_FORCE" };

    /**
     * Threshold of processing time.
     */
    int THRESHOLD_PROCESSING_TIME = 3500000;

    /**
     * Processing power.
     */
    int PROCESSING_POWER = 28800 / 5;

    /**
     * Process ratio.
     */
    int PROCESS_RATIO = 10;
    /**
     * Flag used when creating sendingTable or receivingTable.
     */
    boolean SEND = true;
    /**
     * Flag used when creating sendingTable or receivingTable.
     */
    boolean RECEIVE = false;
    /**
     * Discrete tick used for generating DCOP table.
     */
    double DISCRETE_TICK = 1.0;

    /**
     * Capacity of Data Center.
     */
    int DATACENTER_CAPACITY = -1;

    /**
     * Do nothing.
     */
    void go();
}
