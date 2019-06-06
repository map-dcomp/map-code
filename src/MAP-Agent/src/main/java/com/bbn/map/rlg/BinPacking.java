package com.bbn.map.rlg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * A bin packing implementation of RLG.
 */
public class BinPacking {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinPacking.class);

    private final ArrayList<Server> bins = new ArrayList<Server>();
    private int numberOfBins;

    // Replace with Double.POSITIVE_INFINITY
    // private final double positiveInfinity = 1000000.0;

    /**
     * Construct a BinPacking object. This will handle load balancing according
     * to the bin packing algorithms (e.g. first fit, best fit, worst fit etc.)
     */
    public BinPacking() {
        numberOfBins = 0;
    }

    /**
     * Construct a BinPacking object, given a list of Server objects.
     * 
     * @param nodes
     *            a collection of {@link Server} objects to be load balanced.
     */
    public BinPacking(Collection<Server> nodes) {
        for (Server node : nodes) {
            addServer(node);
            numberOfBins++;
        }
    }

    // simplest way to add servers to list of bins
    /**
     * Add a {@link Server} to the list of bins.
     * 
     * @param server
     *            the {@link Server} to be added
     */
    public void addServer(Server server) {
        bins.add(server);
        numberOfBins++;
    }

    /**
     * @return true if the given{@link Server} is in our list, false otherwise.
     * @param server
     *            the {@link Server} to check
     */
    public boolean hasServer(Server server) {
        if (bins.indexOf(server) == -1)
            return false;
        // otherwise
        return true;
    }

    /**
     * Print the {@link Server} objects. For debugging purposes only.
     */
    public void print() {
        for (Server server : bins) {
            System.out.println(server.toString());
        }
    }

    /**
     * Add a {@link Service} to the system using first fit bin packing.
     * 
     * @param serviceName
     *            name of the service to be added
     * @param serviceLoad
     *            load of the service to be added
     */
    public void addFirstFit(ServiceIdentifier<?> serviceName, double serviceLoad) {
        for (Server server : bins) {
            if (serviceLoad <= server.getCapacity() - server.getLoad()) {
                server.addNewService(serviceName, serviceLoad);
                break;
            }
        }
    }

    /**
     * Add a {@link Service} to the system using best fit bin packing.
     * 
     * @param serviceName
     *            name of the service to be added
     * @param serviceLoad
     *            load of the service to be added
     */
    public void addBestFit(ServiceIdentifier<?> serviceName, double serviceLoad) {
        boolean added = false;

        while (!added) {
            Server server = findBestFit(serviceLoad);

            if (server == null) {
                // no server can fully accomodate service
                server = findLeastOverflowing(serviceLoad);
                double overflow = serverOverflow(server, serviceLoad);
                double oldLoad = 0.0;

                if (overflow > 0) {
                    oldLoad = serviceLoad - overflow;
                } else {
                    oldLoad = serviceLoad;
                    added = true;
                }
                // Actual double load to be added - after PI meeting
                // server.addNewService(serviceName, oldLoad);

                // temporary - before September PI meeting
                if (server.getLoad() + Math.round(oldLoad) <= server.getCapacity()) {
                    server.addNewService(serviceName, (double) Math.round(oldLoad));
                } else {
                    server.addNewService(serviceName, Math.floor(oldLoad));
                }

                serviceLoad = overflow;

            } else {
                // server found that can fully accomodate service

                // Actual double load to be added - after PI meeting
                // server.addNewService(serviceName, serviceLoad);

                // temporary - before September PI meeting
                if (server.getLoad() + Math.round(serviceLoad) <= server.getCapacity()) {
                    server.addNewService(serviceName, (double) Math.round(serviceLoad));
                } else {
                    server.addNewService(serviceName, Math.floor(serviceLoad));
                }

                added = true;
            }
        }
    }

    /**
     * @return the best fitting {@link Server}, i.e. the one that most tightly
     *         packs service. If there is no server that can completely
     *         accomodate the {@link Service}, return null.
     * @param serviceLoad
     *            load of the service to be added
     */
    public Server findBestFit(double serviceLoad) {
        Server toReturn = null;
        double fracRemaining = 1.0;

        for (Server server : bins) {
            if (server.getLoad() + serviceLoad <= server.alpha() * server.getCapacity()) {
                double leftover = (server.getCapacity() - server.getLoad() - serviceLoad) / server.getCapacity();

                if (leftover < fracRemaining) {
                    fracRemaining = leftover;
                    toReturn = server;
                }
            } else {
                continue;
            }
        }

        return toReturn;
    }

    /**
     * @return the {@link Server} that would be overflowed the least while
     *         adding the given load.
     * @param serviceLoad
     *            load of the service to be added
     */
    public Server findLeastOverflowing(double serviceLoad) {
        Server toReturn = null;
        double leastOverflow = Double.POSITIVE_INFINITY;

        for (Server server : bins) {
            double overflow = server.getLoad() + serviceLoad - server.getCapacity();
            if (overflow < leastOverflow) {
                leastOverflow = overflow;
                toReturn = server;
            }
        }

        if (null == toReturn) {
            throw new RuntimeException(
                    "Cannot find a node in the region to start a service on. This could happen if the capacity or load of the nodes is Infinity");
        }

        return toReturn;
    }

    /**
     * @return the amount of overflow caused on the {@link Server} by adding
     *         given load.
     * @param server
     *            the server to find overload for
     * @param serviceLoad
     *            the load of the service to be added
     */
    public double serverOverflow(Server server, double serviceLoad) {
        return server.getLoad() + serviceLoad - server.getCapacity();
    }

    // /**
    // * @return an RLG plan.
    // */
    // public Map<ServiceIdentifier<?>, Set<NodeIdentifier>> constructRLGPlan()
    // {
    // Map<ServiceIdentifier<?>, Set<NodeIdentifier>> toReturn = new
    // HashMap<>();

    // for (Server server : bins) {
    // // iterate through the list of services
    // for (Service service : server.getServices()) {
    // // if it's already in the map, add this server to its set of servers
    // ServiceIdentifier serviceName = service.getName();
    // NodeIdentifier serverName = server.getName();
    // if (toReturn.containsKey(serviceName)) {
    // Set set = toReturn.get(serviceName);
    // set.add(serverName);
    // System.out.println("New set: " + Objects.toString(set));
    // toReturn.replace(serviceName, set);
    // }
    // // otherwise, create a new map entry
    // else {
    // Set set = new HashSet<Server>();
    // set.add(serverName);
    // toReturn.put(serviceName, set);
    // }
    // }
    // }

    // return toReturn;
    // }

    private static int countContainersRunningService(final ServiceIdentifier<?> service,
            final Collection<LoadBalancerPlan.ContainerInfo> infos) {
        return (int) infos.stream().filter(i -> !i.isStop() && !i.isStopTrafficTo() && service.equals(i.getService()))
                .count();
    }

    /**
     * @param newServicePlan
     *            the service plan that has been computed so far. This will be
     *            modified to contain a merge of the existing plan and the
     *            result of the bin packing algorithm.
     */
    public void constructRLGPlan(final LoadBalancerPlanBuilder newServicePlan) {
        for (final Server server : bins) {
            // iterate through the list of services
            for (final Service service : server.getServices()) {                
                final ServiceIdentifier<?> serviceName = service.getName();
                final NodeIdentifier serverName = server.getName();

                final int currentContainersRunningService = newServicePlan.getPlan().entrySet().stream()
                        .map(Map.Entry::getValue).mapToInt(infos -> countContainersRunningService(serviceName, infos))
                        .sum();

                // choose the max number of container between the already
                // computed service plan and the result of the bin packing
                // algorithm

                // per discussion on 12/12/2018 this should be a ceiling to make
                // sure there is enough capacity
                final double desiredLoad = service.getLoad();
                final int desiredContainers = (int) Math.ceil(desiredLoad);

                // make sure we don't exceed the capacity of the server
                final int allowedContainers = (int) Math.min(desiredContainers, server.getCapacity());

                final int containersToAdd = allowedContainers - currentContainersRunningService;
                if (containersToAdd > 0) {
                    for (int i = 0; i < containersToAdd; ++i) {
                        newServicePlan.addService(serverName, serviceName, 1);
                    }
                }
            }
        }
        LOGGER.trace("New plan: " + newServicePlan);
    }

    // public static void main(String[] args) {
    // ArrayList<Server> myCollection = new ArrayList<Server>();
    // Server server1 = new Server("nodeA0", 10.0, 0.0);
    // Server server2 = new Server("nodeA1", 10.0);
    // Server server3 = new Server("nodeA2", 10.0, 0.0);
    // Server server4 = new Server("nodeA3", 10.0, 0.0);
    // myCollection.add(server1);
    // myCollection.add(server2);
    // myCollection.add(server3);
    // myCollection.add(server4);
    // BinPacking myBins = new BinPacking(myCollection);
    // myBins.print();

    // String s1Name = "image-recognition1";
    // double s1Load = 5.0;
    // String s2Name = "image-recognition2";
    // double s2Load = 6.0;
    // String s3Name = "image-recognition3";
    // double s3Load = 6.0;
    // String s4Name = "blah";
    // double s4Load = 4.0;
    // String s5Name = "blah2";
    // double s5Load = 5.0;
    // myBins.addBestFit(s1Name, s1Load);
    // myBins.addBestFit(s2Name, s2Load);
    // myBins.addBestFit(s3Name, s3Load);
    // myBins.addBestFit(s4Name, s4Load);
    // myBins.addBestFit(s5Name, s5Load);
    // myBins.print();

    // }

}