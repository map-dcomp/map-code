package com.bbn.map.dcop;

/**
 * @author khoihd
 *
 */
public interface DcopConstants {

    /**
     * WAITING_FOR_MSG.
     */
    boolean WAITING_FOR_MSG = true;
    /**
     * Time for each agent to sleep in waiting for messages.
     */
    long SLEEPTIME_WAITING_FOR_MESSAGE = 200; // in milliseconds
    
    /**
     * Time waiting before clearing the mailbox in milliseconds.
     */
    long SLEEPTIME_AFTER_CLEARING_INBOX = 500; // in milliseconds

    /**
     * TIME TO STOP WHILE LOOP.
     */
    long TIME_FOR_LOOP = 3000000; // in nanoseconds;

    /**
     * TIME_SEARCHING_NEIGHBORS.
     */
    long TIME_SEARCHING_NEIGHBORS = 15000; // in milliseconds

    /**
     * Discrete tick used for generating DCOP table.
     */
    double DISCRETE_TICK = 1.0;

    /**
     * Capacity of Data Center.
     */
    int DATACENTER_CAPACITY = -1;
    
    /**
     * Used in reading message function .
     */
    boolean READING_TREE = true;
    
    /**
     * Used in reading message function.
     */
    boolean NOT_READING_TREE = false;
    
    /** True if it's the leaf region of the service.
     * 
     */
    boolean IS_ROOT_AND_LEAF = true;
    
    /** False if it's the leaf region of the service.
     * 
     */
    boolean IS_NOT_LEAF = false;

    /**
     * Do nothing.
     */
    void go();
}
