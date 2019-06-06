package com.bbn.map.rlg;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * A consistent hashing implementation of RLG.
 */
public class ConsistentHash {

    private final MessageDigest md;
    private final int numberOfReplicas;
    private final TreeMap<String, Server> circle =
        new TreeMap<String, Server>();
    private int numberNodes;

    /**
     * Construct a BinPacking object. This will handle the load balancing.
     * @param md
     *          the hashing algorithm to be used
     * @param numberOfReplicas
     *          a parameter of the consistent hashing algorithm. Determines
     *          how many servers to replicate a service to
     * @param nodes
     *          a collection of {@link Server} objects to start with
     */
    public ConsistentHash(MessageDigest md,
        int numberOfReplicas, Collection<Server> nodes) {

        this.md = md;
        this.numberOfReplicas = numberOfReplicas;
        this.numberNodes = 0;

        for (Server node : nodes) {
            add(node);
        }
    }

    /**
     * @return a hash of a string.
     * @param input
     *          a string to compute the hash of
     */
    public String hash(String input) {
        byte[] mDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        BigInteger number = new BigInteger(1, mDigest);
        String hashText = number.toString(16);
        return hashText;
    }

    /**
     * Adds a {@link Server} to the system.
     * @param node
     *          the server to be added
     */
    public void add(Server node) {
        // for (int i = 0; i < numberOfReplicas; i++) {
            // circle.put(hash(node.getName() + i),
            //     node);
            circle.put(hash(node.getName().getName()), node);
            numberNodes++;
        // }
    }

    /**
     * Removes a {@link Server} from the system.
     * @param node
     *          the server to be removed
     */
    public void remove(Server node) {
        // for (int i = 0; i < numberOfReplicas; i++) {
            // circle.remove(hash(node.getName() + i));
            circle.remove(hash(node.getName().getName()));
            numberNodes--;
        // }
    }

    /**
     * @return the {@link Server} responsible for a key.
     * @param key
     *          the key object for which corresponding server needs to be found
     */
    public Server get(Object key) {
        if (circle.isEmpty()) {
            return null;
        }
        String hashVal = hash(key.toString());
        // System.out.println("From get(), hashVal: " + hashVal);
        if (!circle.containsKey(hashVal)) {
            // System.out.println("Came here0");
            SortedMap<String, Server> tailMap =
                circle.tailMap(hashVal);
            hashVal = tailMap.isEmpty() ?
                         circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hashVal);
    }

    /**
     * @return the {@link Server} responsible for a key (in string format).
     * @param hashVal
     *          the string for which corresponding server needs to be found
     */
    public Server getFromKey(String hashVal) {
        if (circle.isEmpty()) {
            return null;
        }
        return circle.get(hashVal);
    }

    /**
     * @return the next key-server pair in the circle responsible for a key.
     * @param hashVal
     *          the string for which next server needs to be found
     */
    public Map.Entry<String, Server> getNextEntry(String hashVal) {
        if (circle.isEmpty()) {
            return null;
        }
        Map.Entry<String, Server> nextKey = null;
        nextKey = circle.higherEntry(hashVal);
        if (nextKey == null) {
            nextKey = circle.firstEntry();
        }
        return nextKey;
    }

    /**
     * @return the previous key-server pair in the circle responsible for a key.
     * @param hashVal
     *          the string for which previous server needs to be found
     */
    public Map.Entry<String, Server> getPrevEntry(String hashVal) {
        if (circle.isEmpty()) {
            return null;
        }
        Map.Entry<String, Server> prevKey = null;
        prevKey = circle.lowerEntry(hashVal);
        if (prevKey == null) {
            prevKey = circle.lastEntry();
        }
        return prevKey;
    }

    // public Server getNext(String hashVal) {
    //     if (circle.isEmpty()) {
    //         return null;
    //     }
    //     Map.Entry<String, Server> nextKey = null;
    //     nextKey = circle.higherEntry(hashVal);
    //     if (nextKey == null) {
    //         nextKey = circle.firstEntry();
    //     }
    //     return nextKey.getValue();
    // }

    /**
     * @return the next {@link Server} in the circle responsible for a key.
     * @param hashVal
     *          the string for which next server needs to be found
     */
    public Server getNext(String hashVal) {
        return getNextEntry(hashVal).getValue();
    }

    /**
     * Prints the servers in the circle. For debugging purposes only.
     */
    public void print() {
        for (Map.Entry<String, Server> entry : circle.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", value: " + 
                entry.getValue().toString() + ".");
        }
    }

    /**
     * Finds and prints an object in the circle. For debugging purposes only.
     * @param key
     *          the object to be printed
     */
    public void getAndPrint(Object key) {
        Server node = get(key);
        System.out.println("Searching for object...");
        System.out.println("Hash of object: " + hash(key.toString()));
        System.out.println("Object should be at node: " + node.getName() + ".");
    }

    /**
     * Balance the load by spreading services from overloaded servers.
     */
    public void balanceLoad() {
        // for now, simply print "server overloaded"
        boolean overload = true;
        // while (overload) {
        for (int j=0; j < 3; j++) {
            overload = false;
            double maxLoad = 0;
            String maxKey = "";
            Server toBalance = null;

            Iterator<Map.Entry<String, Server>> it = circle.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Server> entry = it.next();
            // for (Map.Entry<String, Server> entry : circle.entrySet()) {
                Server server = entry.getValue();
                String key = entry.getKey();

                // find most overloaded server
                if (server.isOverloaded()) {
                    System.out.println("Server " + server.getName() + " is overloaded!");

                    overload = true;
                    if (server.overload() > maxLoad) {
                        maxLoad = server.overload();
                        maxKey = key;
                    }
                }
            }
            if (!overload) {
                // no server overloaded
                break;
            }
            System.out.println("Max key:" + maxKey);
            toBalance = getFromKey(maxKey);
            System.out.println("Most overloaded server:");
            System.out.println(toBalance.toString());

            // find heaviest service, spread it evenly
            Service service = toBalance.heaviestService();
            if (service == null) {
                System.out.println("No load to be balanced.");
                return;
            }

            System.out.println("Heaviest service:");
            System.out.println(service.toString());
            ServiceIdentifier<?> serviceName = service.getName();
            double serviceLoad = service.getLoad();
            int oldDivFactor = service.getDivideFactor();

            // Move service when it is too big to be divided? No
            // boolean divided = false;
            // for (double divFactor = 2.0; divFactor <= 10; divFactor++) {
            //     if (toBalance.overload() - serviceLoad/divFactor <= 0) {
            //         System.out.println("Service can be divided to next server.");
            //         divided = true;
            //         break;
            //     }
            // }
            // if (!divided) {
            //     System.out.println("Service was too big to be divided. Being shifted to next server.");
            // }

            // TODO: spread service
            // case: if service is causing overload at 2nd server, do we split at original server too?
            //    answer (not sure): spread evenly. so yes divide at original server too
            // case: what if next server doesn't have enough capacity?
            //    answer (not sure): then it should have been the most overloaded server. Or it will become the new
            //    most overloaded server and balanceLoad will fix it.

            service.divide();    // just increases divideFactor of service object
            int newDivFactor = oldDivFactor + 1;

            // reduce load from all servers currently hosting service
            // start from original server hosting the service
            int counter = 0;
            Server currentServer = toBalance;
            String currentKey = maxKey;
            while (service.getOriginal() != currentServer) {
                Map.Entry<String, Server> prevEntry = getPrevEntry(currentKey);
                currentKey = prevEntry.getKey();
                currentServer = prevEntry.getValue();

                counter++;
                if (counter > numberNodes) {
                    System.out.println("ERROR: Circle completed, running in loop.");
                }
            }

            String iterateKey = currentKey;
            Server iterateServer = currentServer;

            // iterate through circle to find next server
            for (int i = 0; i < oldDivFactor; i++) {
                // // for equal division
                // currentServer.reduceLoad(serviceLoad/oldDivFactor - serviceLoad/newDivFactor);
                
                // for capacity-proportional divison
                currentServer.reduceLoad(service.getFracLoad(currentServer));

                Map.Entry<String, Server> nextEntry = getNextEntry(currentKey);
                currentKey = nextEntry.getKey();
                currentServer = nextEntry.getValue();
            }
            // now we are at last server. Add service to it
            currentServer.addOldService(service);

            // iterate through circle to update service load on old servers
            for (int i = 0; i < oldDivFactor; i++) {
                iterateServer.increaseLoad(service.getFracLoad(iterateServer));
                Map.Entry<String, Server> nextEntry = getNextEntry(iterateKey);
                iterateKey = nextEntry.getKey();
                iterateServer = nextEntry.getValue();
            }

            print();
            // System.out.println("Load balance iteration finished");

        }
        // System.out.println("Balance load finished.");
    }

    /**
     * Adds a new {@link Service} to the system.
      * @param serviceName
     *          name of the service to be added
     * @param serviceLoad
     *          load of the service to be added
     */
    public void addServiceToMap(ServiceIdentifier<?> serviceName, double serviceLoad) {
        Server server = get(serviceName);
        server.addNewService(serviceName, serviceLoad);
    }

    /**
     * Perform the unspreading step of the algorithm.
     */
    public void unspread() {
        System.out.println("Unspreading.");
    }

    /**
     * @return an RLG plan.
     */
    public Map<ServiceIdentifier<?>, Map<NodeIdentifier, Double>> constructRLGPlan() {
        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Double>> toReturn = new HashMap<>();

        for (Map.Entry<String, Server> entry : circle.entrySet()) {
            Server server = entry.getValue();

            // iterate through the list of services
            for (Service service : server.getServices()) {
                // if it's already in the map, add this server to its set of servers
                ServiceIdentifier<?> serviceName = service.getName();
                NodeIdentifier serverName = server.getName();
                if (toReturn.containsKey(serviceName)) {
                    Map<NodeIdentifier, Double> set = toReturn.get(serviceName);
                    set.put(serverName, service.getFracLoad(server));
                    System.out.println("New set: " + Objects.toString(set));
                    toReturn.replace(serviceName, set);
                }
                // otherwise, create a new map entry
                else {
                    Map<NodeIdentifier, Double> set = new HashMap<>();
                    set.put(serverName, service.getFracLoad(server));
                    toReturn.put(serviceName, set);
                }
            }
        }

        return toReturn;
    }

    // public static void main(String[] args) {
    //     MessageDigest md = null;
    //     try {
    //         md = MessageDigest.getInstance("MD5");
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    //     ArrayList<Server> myCollection = new ArrayList<Server>();
    //     Server server1 = new Server("nodeA0", 10.0, 0.0);
    //     Server server2 = new Server("nodeA1", 10.0);
    //     Server server3 = new Server("nodeA2", 10.0, 0.0);
    //     Server server4 = new Server("nodeA3", 10.0, 0.0);
    //     myCollection.add(server1);
    //     myCollection.add(server2);
    //     myCollection.add(server3);
    //     myCollection.add(server4);
    //     ConsistentHash myCircle = new ConsistentHash(md, 1,
    //         myCollection);

    //     // myCircle.print();
    //     // myCircle.balanceLoad();

    //     // TODO: Need a function in ConsistentHash class that takes service and
    //     //    finds which server to add to, and then calls the addNewService
    //     //    function on that server

    //     // service comes, need to add to server
    //     String service1Name = "image-recognition";
    //     double service1Load = 5.0;
    //     Server temp = myCircle.get(service1Name);
    //     String service2Name = "image-recognition2";
    //     double service2Load = 11.0;
    //     temp.addNewService(service1Name, service1Load);
    //     temp.addNewService(service2Name, service2Load);

    //     myCircle.print();
    //     myCircle.balanceLoad();
    // }
}