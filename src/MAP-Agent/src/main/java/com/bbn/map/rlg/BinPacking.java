package com.bbn.map.rlg;

import java.util.List;
import java.util.LinkedList;
import java.util.Collection;
import java.util.Objects;
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

    private final List<Server> bins;
    private int numberOfBins;
    // private static int numberOfServices1 = 0;
    // private static int numberOfServices2 = 0;
    // private static int numberOfServices3 = 0;
    // private static int numberOfServices4 = 0;
    // private static int numberOfServices5 = 0;
    // private static int numberOfServices6 = 0;
    // private static int removedServices = 0;

    /**
     * Construct a BinPacking object. This will handle load balancing according
     * to the bin packing algorithms (e.g. first fit, best fit, worst fit etc.)
     */
    public BinPacking() {
        bins = new LinkedList<Server>();
        numberOfBins = 0;
        // numberOfServices1 = 0;
        // numberOfServices2 = 0;
    }

    /**
     * Construct a BinPacking object, given a list of Server objects.
     * 
     * @param nodes
     *            a collection of {@link Server} objects to be load balanced.
     */
    public BinPacking(Collection<Server> nodes) {
        bins = new LinkedList<Server>();
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
        for (Server oldServer : bins) {
            // // Variant 1: String equality testing
            // String oldName = Objects.toString(oldServer.getName());
            // String newName = Objects.toString(server.getName());
            // double oldCap = oldServer.getCapacity();
            // double newCap = server.getCapacity();
            // // System.out.print("Comparing " + oldServer.getName() + " and " + server.getName());
            // // System.out.println(" " + (oldName.equals(newName)) + " " + (oldCap == newCap));
            // if (oldName.equals(newName) && oldCap == newCap) {
            //     return true;
            // }

            // Variant 2; NodeIdentifier equality testing
            if (oldServer.getName().equals(server.getName())) {
                return true;
            }
        }
        // if (bins.contains(server))
        //     return true;
        // otherwise
        return false;
    }

    /**
     * @return the total number of bins (servers) in the region.
     */
    public int getTotalBins() {
        return bins.size();
    }

    /**
     * Print the {@link Server} objects. For debugging purposes only.
     */
    public void print() {
        System.out.println("Number of bins: " + bins.size());
        // LOGGER.info("Number of services1: " + numberOfServices1);
        // LOGGER.info("Number of services2: " + numberOfServices2);
        // LOGGER.info("Number of services3: " + numberOfServices3);
        // LOGGER.info("Number of services4: " + numberOfServices4);
        // LOGGER.info("Number of services5: " + numberOfServices5);
        // LOGGER.info("Number of services6: " + numberOfServices6);
        // LOGGER.info("Removed services: " + removedServices);
        // System.out.println("Number of bins: " + numberOfBins);
        for (Server server : bins) {
            System.out.println(server.toString());
        }
    }

    /**
     * @return the total capacity of the region (all the servers).
     */
    public double totalCapacity() {
        double capacity = 0;
        for (Server server : bins) {
            capacity += server.getCapacity();
        }
        return capacity;
    }

    /**
     * @return the remaining capacity of the region (all the servers).
     */
    public double remainingCapacity() {
        double remaining = totalCapacity();
        for (Server server : bins) {
            for (Service service : server.getServices()) {
                if (service.isAdded())
                    remaining -= service.getLoad();
            }
        }
        return remaining;
    }

    // assumes all containers started have serviceLoad 1
    /**
     * Shuts down one container for a given {@link Service} on the specified {@link Server}.
     * 
     * @param serverName
     *            name of the server at which container is to be shutdown
     * @param serviceName
     *            name of the service for which container is to be shutdown
     */
    public void shutdownContainer(NodeIdentifier serverName, ServiceIdentifier<?> serviceName) {
        // check if service has only one container in entire region
        //  if yes, do not remove
        //  if not, remove 1 container per call

        int count = 0;
        for (Server server : bins) {
            // String name = Objects.toString(server.getName());

            // if (name.equals(Objects.toString(serverName))) {
            for (Service service : server.getServices()) {
                // String appName = Objects.toString(service.getName());

                // if (appName.equals(Objects.toString(serviceName))) {
                //     count++;
                // }
                if (service.getName().equals(serviceName))
                    count++;
            }
            // }
        }

        if (count <= 1) {
            System.out.println("Service has only 1 container in region. Aborting remove.");
            return;
        }

        // if count > 1, remove a container
        for (Server server : bins) {
            // String name = Objects.toString(server.getName());

            // if (name.equals(Objects.toString(serverName))) {
            if (server.getName().equals(serverName)) {
                for (Service service : server.getServices()) {
                    // String appName = Objects.toString(service.getName());

                    // if (appName.equals(Objects.toString(serviceName))) {
                    if (service.getName().equals(serviceName)) {
                        // remove service from server
                        server.removeService(service);
                        // removedServices++;
                        return;
                    }
                }
            }
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
                // numberOfServices1++;
                break;
            }
        }
    }

    /**
     * @return true if the region contains the given {@link Service}, false otherwise.
     * 
     * @param serviceName
     *            name of the service to be checked
     * @param serviceLoad
     *            load of the service to be checked
     */
    public boolean containsService(ServiceIdentifier<?> serviceName, double serviceLoad) {
        String newName = Objects.toString(serviceName);
        for (Server server : bins) {
            for (Service oldService : server.getServices()) {
                String oldName = Objects.toString(oldService.getName());
                double oldLoad = oldService.getLoad();

                if (oldName.equals(newName) && oldLoad == serviceLoad)
                    return true;
            }
        }
        return false;
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
        // if (containsService(serviceName, serviceLoad)) {
        //     System.out.println("Duplicate service being added. Cancelling addition.");
        //     return;
        // }

        if (serviceLoad < 0) {
            System.out.println("Service less than zero. Cancelling addition.");
            return;
        }

        if (serviceLoad == 0) {
            serviceLoad = 1;
        }

        int counter = 0;

        boolean added = false;

        while (!added) {
            Server server = findBestFit(serviceLoad);

            // LOGGER.info("Counter: " + counter);
            // LOGGER.info("serviceLoad: " + serviceLoad);
            // counter++;

            if (server != null) {
                // server found that can fully accomodate service

                // Actual double load to be added - after PI meeting
                // server.addNewService(serviceName, serviceLoad);

                // alternate: round up everytime
                if (server.getLoad() + Math.round(serviceLoad) <= server.getCapacity()) {
                    server.addNewService(serviceName, (double) Math.round(serviceLoad));
                    // numberOfServices5++;
                } else {
                    server.addNewService(serviceName, Math.floor(serviceLoad));
                    // numberOfServices6++;
                }

                added = true;

            } else {
                // if there is space in the region
                boolean noCapacityLeft = true;
                for (Server checkServer : bins) {
                    if (Math.round(checkServer.getLoad()) < checkServer.getCapacity()) {
                        noCapacityLeft = false;
                    }
                }
                if (noCapacityLeft) {
                    // LOGGER.info("No capacity in the region to start service.");
                    throw new RuntimeException(
                        "There is not enough capacity in the region to start a new service.");
                }

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

                // alternate: round up everytime
                if (server.getLoad() + Math.round(oldLoad) <= server.getCapacity()) {
                    server.addNewService(serviceName, (double) Math.round(oldLoad));
                    // numberOfServices3++;
                    // LOGGER.info("services3debug:" + server.getName() + "," + oldLoad + 
                    //                 "," + server.getCapacity());
                    // this.print();
                } else {
                    server.addNewService(serviceName, Math.floor(oldLoad));
                    // numberOfServices4++;
                }

                serviceLoad = overflow;
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

                // if this server packs the service more tightly than toReturn,
                //  replace toReturn with this server
                if (leftover < fracRemaining) {
                    fracRemaining = leftover;
                    toReturn = server;
                }
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

        if (toReturn == null) {
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

                if (!service.isAdded()) {
                    service.addToRlg();
                    newServicePlan.addService(serverName, serviceName, 1);
                    // numberOfServices2++;
                }

                // // -- start commented section --
                // // after addition of 'addedToRlg' variable, this code is redundant
                // // per discussion on 12/12/2018 this should be a ceiling to make
                // // sure there is enough capacity
                // final double desiredLoad = service.getLoad();
                // final int desiredContainers = (int) Math.ceil(desiredLoad);

                // // make sure we don't exceed the capacity of the server
                // final int allowedContainers = (int) Math.min(desiredContainers, server.getCapacity());

                // // currentContainersRunningService is a global var, should be subtracted from sum of allowedContainers across all NCPs
                // final int containersToAdd = allowedContainers - currentContainersRunningService; // Jon's version
                // // final int containersToAdd = allowedContainers;                  // temporary fix
                // System.out.println("Service name: " + serviceName + ", server name: " + serverName +
                //                     ", containers to add: " + containersToAdd);
                // System.out.println("Desired containers: " + desiredContainers);
                // if (containersToAdd > 0) {
                //     for (int i = 0; i < containersToAdd; ++i) {
                //         newServicePlan.addService(serverName, serviceName, 1);
                //     }
                // }
                // // -- end commented section --
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