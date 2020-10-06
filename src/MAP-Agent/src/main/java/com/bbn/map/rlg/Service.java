package com.bbn.map.rlg;

import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * An instance of a service running on an NCP to help RLG locate and divide it.
 * This object represents all of the containers running a particular service on a node.
 */
public class Service {
    private ServiceIdentifier<?> serviceName;
    private double serviceLoad;
    private int divideFactor;
    private Server originalServer;
    private double serverTotal;
    private boolean addedToRlg;

    /**
     * Construct a new service object.
     * @param serviceName
     *          name of the service
     * @param serviceLoad
     *          load of the service in terms of task containers
     * @param divideFactor
     *            how much the service has been divided. i.e. 1 if on original
     *            server, 2 if on original server plus the next one, and so on
     */
    public Service(ServiceIdentifier<?> serviceName, double serviceLoad, int divideFactor) {
        this.serviceName = serviceName;
        this.serviceLoad = serviceLoad;
        this.divideFactor = divideFactor;
        this.originalServer = null;
        this.serverTotal = 0.0;
        this.addedToRlg = false;
    }

    /**
     * @return the name of this service.
     */
    public ServiceIdentifier<?> getName() {
        return serviceName;
    }

    /**
     * @return the load of this service.
     */
    public double getLoad() {
        return serviceLoad;
    }

    /**
     * @return true if this service has been added to the RLG plan.
     */
    public boolean isAdded() {
        return addedToRlg;
    }

    /**
     * Mark this service as added to the RLG plan.
     */
    public void addToRlg() {
        addedToRlg = true;
    }

    /**
     * @return the string description of the service.
     */
    @Override
    public String toString() {
        return "Service name: " + serviceName + ". Service load: " +
            serviceLoad + ". Divide factor: " + divideFactor + ".";
    }

    /**
     * Increase the divide factor of the service by 1 (for RLG book-keeping).
     */
    public void divide() {
        divideFactor++;
    }

    /**
     * @return the divide factor of this service.
     */
    public int getDivideFactor() {
        return divideFactor;
    }

    /**
     * Sets the pointer to the {@link Server} originally hosting this service.
     * @param server
     *            pointer to the original server
     */
    public void setOriginal(Server server) {
        this.originalServer = server;
        this.serverTotal = server.getCapacity();
    }

    /**
     * @return the total capacities of servers hosting the service.
     */

    public double getServerTotal() {
        return serverTotal;
    }

    /**
     * Change the total capacity number of servers hosting the service.
     * @param newTotal
     *            the new total capacity to set
     */
    public void setServerTotal(double newTotal) {
        this.serverTotal = newTotal;
    }

    /**
     * @return the {@link Server} originally hosting this service.
     */
    public Server getOriginal() {
        return originalServer;
    }

    /**
     * @return the fractional load of the capacity with respect to the given {@link Server}.
     * @param server
     *            pointer to the server for which fractional load is to be computed
     */
    public double getFracLoad(Server server) {
        return serviceLoad * server.getCapacity() / serverTotal;
    }
}