package com.bbn.map.rlg;

import java.util.ArrayList;

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * An instance of a server used for the RLG implementation.
 */
public class Server {
    private final NodeIdentifier nodeName;
    private final double totalCapacity;
    // private final int totalCapacity;
    private double usedCapacity;
    // private int usedCapacity;
    private final double overloadParam;
    private ArrayList<Service> servicesHosted;
    // servicesHosted

    /**
     * Construct a new server object.
     * @param nodeName
     *          name of the server
     * @param totalCapacity
     *          capacity of the server in terms of task containers
     */
    public Server(NodeIdentifier nodeName, double totalCapacity) {
        this.nodeName = nodeName;
        this.totalCapacity = totalCapacity;
        this.usedCapacity = 0.0;
        this.overloadParam = AgentConfiguration.getInstance().getRlgLoadThreshold();
        this.servicesHosted = new ArrayList<Service>();
    }
    // public Server(NodeIdentifier nodeName, int totalCapacity) {
    //     this.nodeName = nodeName;
    //     this.totalCapacity = totalCapacity;
    //     this.usedCapacity = 0.0;
    //     this.overloadParam = 1.0;
    //     this.servicesHosted = new ArrayList<Service>();
    // }

    /**
     * Construct a new server object (overloaded constructor).
     * @param nodeName
     *          name of the server
     * @param totalCapacity
     *          capacity of the server in terms of task containers
     * @param usedCapacity
     *          the capacity that has already been used on the server
     */
    public Server(NodeIdentifier nodeName, double totalCapacity, double usedCapacity) {
        this.nodeName = nodeName;
        this.totalCapacity = totalCapacity;
        this.usedCapacity = usedCapacity;
        this.overloadParam = AgentConfiguration.getInstance().getRlgLoadThreshold();
        this.servicesHosted = new ArrayList<Service>();
    }
    // public Server(NodeIdentifier nodeName, int totalCapacity, int usedCapacity) {
    //     this.nodeName = nodeName;
    //     this.totalCapacity = totalCapacity;
    //     this.usedCapacity = usedCapacity;
    //     this.overloadParam = 1.0;
    //     this.servicesHosted = new ArrayList<Service>();
    // }

    /**
     * @return the name of the node.
     */
    public NodeIdentifier getName() {
        return nodeName;
    }

    /**
     * @return the total capacity of the node.
     */
    public double getCapacity() {
        return totalCapacity;
    }

    /**
     * @return the load on the node.
     */
    public double getLoad() {
        return usedCapacity;
    }

    /**
     * @return the overload parameter (0.0-1.0)
     */
    public double alpha() {
        return overloadParam;
    }

    /**
     * @return true if the server is overloaded i.e. used capacity > total capacity
     * times an overload factor in the range [0,1].
     */
    public boolean isOverloaded() {
        return (usedCapacity > overloadParam * totalCapacity) ? 
                true : false;
    }

    /**
     * @return the overload amount.
     */
    public double overload() {
        return (usedCapacity - totalCapacity * overloadParam);
    }

    /**
     * @return the string description of the server.
     */
    @Override
    public String toString() {
        return "Server name: " + nodeName + ". Total capacity: " +
            totalCapacity + ". Used capacity: " + usedCapacity;
    }

    /**
     * Adds a new {@link Service} to the server.
     * @param serviceName
     *          name of the service to be added
     * @param serviceLoad
     *          load of the service in terms of task containers
     */
    public void addNewService(ServiceIdentifier<?> serviceName, double serviceLoad) {
        Service service = new Service(serviceName, serviceLoad, 1);
        servicesHosted.add(service);
        usedCapacity += serviceLoad/1;
        service.setOriginal(this);
        // System.out.println("New service added to server: " + nodeName);
    }
    // public void addNewService(ServiceIdentifier serviceName, int serviceLoad) {
    //     Service service = new Service(serviceName, serviceLoad, 1);
    //     servicesHosted.add(service);
    //     usedCapacity += serviceLoad;
    //     service.setOriginal(this);
    //     // System.out.println("New service added to server: " + nodeName);
    // }

    // public void addService(String serviceName, double serviceLoad, int divFactor) {
    //     Service service = new Service(serviceName, serviceLoad, 1);
    //     servicesHosted.add(service);
    //     usedCapacity += serviceLoad/divFactor;
    //     System.out.println("New service added to server: " + nodeName);
    // }

    // this is for adding an existing service to a new server (in spreading step)
    /**
     * Adds an existing {@link Service} to the server.
     * @param service
     *          pointer to the service being added
     */
    public void addOldService(Service service) {
        servicesHosted.add(service);

        // // for equal division
        // usedCapacity += service.getLoad()/service.getDivideFactor();

        // for capacity-proportional division
        service.setServerTotal(service.getServerTotal() + this.totalCapacity);
        usedCapacity += service.getFracLoad(this);

        System.out.println("Previously created service added to server: " + nodeName);
    }

    /**
     * Reduces the load on a server by an amount.
     * @param reduction
     *          the amount of load to reduce
     */
    public void reduceLoad(double reduction) {
        usedCapacity -= reduction;
    }
    // public void reduceLoad(int reduction) {
    //     usedCapacity -= reduction;
    // }

    /**
     * Increases the load on a server by an amount.
     * @param increment
     *          the amount of load to increase
     */
    public void increaseLoad(double increment) {
        usedCapacity += increment;
    }
    // public void increaseLoad(int increment) {
    //     usedCapacity += increment;
    // }

    // heaviest service = one with max load. If it's already divided N times,
    //    it's no longer the heaviest service
    /**
     * @return the {@link Service} causing the most load on the server.
     */
    public Service heaviestService() {
        int mapSize = servicesHosted.size();
        // System.out.println("Map size: " + mapSize);

        if (mapSize == 0) {
            return null;
        }
        else if (mapSize == 1) {
            return servicesHosted.iterator().next();
        }
        else {
            return servicesHosted.stream().max((entry1, entry2) -> 
                entry1.getLoad() > entry2.getLoad() ? 1 : -1).get();
        }
    }

    /**
     * @return a list of services hosted by the server.
     */
    public ArrayList<Service> getServices() {
        return servicesHosted;
    }
}