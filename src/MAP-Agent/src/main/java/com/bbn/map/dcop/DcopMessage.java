package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.dcop.DCOPService.MessageType;
import com.bbn.map.dcop.algorithm3.DcopTree;
import com.bbn.map.dcop.algorithm3.WeightedAlgorithm.TypeMessage;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * @author khoihd
 *
 */
public class DcopMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    
    // RDIFF attributes
    private MessageType type;
    private final Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> clientDemandMap = new HashMap<>();
    private final Map<ServiceIdentifier<?>, Double> excessLoadMapToParent = new HashMap<>();
    private final Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenMap = new HashMap<>();
    private final Map<RegionIdentifier, Set<ServiceIdentifier<?>>> parentServicesMap = new HashMap<>();
    
    // CDIFF attributes
    private RegionIdentifier originalSender;
    private int hop;
    private Map<ServiceIdentifier<?>, Double> loadMap = new HashMap<>();
    private double efficiency;
    private double latency;
    private double availableLoad;
    
 // Priority CDIFF attributes
    private TypeMessage typeMessage;
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>> serviceClientDemandMap;
    private Map<ServiceIdentifier<?>, Boolean> isDataCenter;
    private DcopTree tree;
    private int iteration;
    
    /** Default constructor.
     * 
     */
    public DcopMessage() {
       setAvailableLoad(0);
    }
    
    /** Copy constructor.
     * @param object is the object to be copied
     */
    public DcopMessage(DcopMessage object) {
        this();
        //CDIFF
//        if(!DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
//        {
        originalSender = object.getOriginalSender();
        this.hop = object.getHop();
        this.loadMap = object.getLoadMap();
        this.efficiency = object.getEfficiency();
        this.latency = object.getLatency();
//        }else{
        //RDIFF
        type = object.getType();
        //WCDIFF
        serviceClientDemandMap = new HashMap<>();
        typeMessage = object.getTypeMessage();
        
        // Copying excessLoadMapToParent
        for (Entry<ServiceIdentifier<?>, Double> entry : object.getExcessLoadMapToParent().entrySet()) {
            excessLoadMapToParent.put(entry.getKey(), entry.getValue());
        }
        
        // Copying clientDemandMap 
        for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry : object.getClientDemandMap().entrySet()) {
            Map<ServiceIdentifier<?>, Double> serviceLoad = new HashMap<>();
            for (Entry<ServiceIdentifier<?>, Double> entry2 : entry.getValue().entrySet()) {
                serviceLoad.put(entry2.getKey(), entry2.getValue());
            }
            clientDemandMap.put(entry.getKey(), serviceLoad);
        }
        
        // Copying childrenMap
        for (Entry<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> entry : object.childrenMap.entrySet()) {
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> sourceServiceLoad = new HashMap<>(); 
            for (Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry2 : entry.getValue().entrySet()) {
                Map<ServiceIdentifier<?>, Double> serviceLoad = new HashMap<>();
                for (Entry<ServiceIdentifier<?>, Double> entry3 : entry2.getValue().entrySet()) {
                    serviceLoad.put(entry3.getKey(), entry3.getValue());
                }
                sourceServiceLoad.put(entry2.getKey(), serviceLoad);
            }
            childrenMap.put(entry.getKey(), sourceServiceLoad);
        }
        
        // Copying parentServicesMap
        for (Entry<RegionIdentifier, Set<ServiceIdentifier<?>>> entry : object.parentServicesMap.entrySet()) {
            Set<ServiceIdentifier<?>> newSet = new HashSet<>();
            for (ServiceIdentifier<?> service : entry.getValue()) {
                newSet.add(service);
            }
            parentServicesMap.put(entry.getKey(), newSet);
        }
     // Copying clientDemandMap 
        if(object.getServiceClientDemandMap() != null)
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : object.getServiceClientDemandMap().entrySet()) {
            Map<RegionIdentifier, Double> regionLoad = new HashMap<>();
            for (Entry<RegionIdentifier, Double> entry2 : entry.getValue().entrySet()) {
                regionLoad.put(entry2.getKey(), entry2.getValue());
            }
            serviceClientDemandMap.put(entry.getKey(),regionLoad);
        }
        else
            serviceClientDemandMap = null;
        //copy tree
        if(object.tree != null)
            copyTreeMap(object.tree);
        if(object.isDataCenter != null)
            copyIsDataCenter(object.isDataCenter);
    }
//    }

    /** Constructor.
     * @param parentServicesMap parentServicesMap
     * @param childrenMap childrenMap
     * @param type type
     */
    public DcopMessage(Map<RegionIdentifier, Set<ServiceIdentifier<?>>> parentServicesMap,
            Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> childrenMap,
            MessageType type) {
        this();
        this.setType(type);
        if (null != parentServicesMap) {
            this.parentServicesMap.putAll(parentServicesMap);
        }
        if (null != childrenMap) {
            this.childrenMap.putAll(childrenMap);
        }
    }
    
    /**
     * Constructor.
     * @param type of the message
     * @param clientDemandMap clientDemandMap
     * @param excessLoadMapToParents excessLoadMapToParents
     */
    public DcopMessage(MessageType type,
            Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> clientDemandMap,
            Map<ServiceIdentifier<?>, Double> excessLoadMapToParents) {
        this();
        this.setType(type);
        if(null != clientDemandMap) {
            this.clientDemandMap.putAll(clientDemandMap);
        }
        if (null != excessLoadMapToParents) {
            this.excessLoadMapToParent.putAll(excessLoadMapToParents);
        }
    }
    
    /**
     * @param type type for PRDIFF
     * @param serviceClientDemandMap clientServiceDemandMap
     */
    public DcopMessage(TypeMessage type, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>serviceClientDemandMap ) {
        this();   
        this.serviceClientDemandMap = new HashMap<>();
        this.setTypeMessage(type); 
           this.setServiceClientDemandMap(serviceClientDemandMap);
       }
    
    /**
     * @param type type
     * @param oldTree oldTree
     * @param iteration last iteration
     */
    public DcopMessage(TypeMessage type, DcopTree oldTree, int iteration){
        this.typeMessage = type;
        this.tree = new DcopTree(oldTree);
        this.iteration = iteration;
    }
    
    /**
     * @param hop
     *            hop
     * @param loadMap
     *            load Message type 1 when asking for help
     *            For prev algorithm
     */
    public DcopMessage(int hop, Map<ServiceIdentifier<?>, Double> loadMap) {
        this();
        originalSender = null;
        this.hop = hop;
        this.loadMap.putAll(loadMap);
        this.efficiency = -1.0;
        this.latency = -1.0;
    }

    /**
     * @param sender
     *            original sender of the message
     * @param hop
     *            number of hopes from original sender
     * @param loadMap
     *            loadMap that is being asked for
     * @param efficiency
     *            send my efficiency
     * @param latency
     *            time to send messages to my parent Message type 2 when
     *            answering with help
     */
    public DcopMessage(RegionIdentifier sender, int hop, Map<ServiceIdentifier<?>, Double> loadMap, double efficiency, double latency) {
        originalSender = sender;
        this.hop = hop;
        this.loadMap.putAll(loadMap);
        this.efficiency = efficiency;
        this.latency = latency;
    }
    
    /**
     * @param selfRegionID
     *              selfRegionID
     * @param hop2
     *              hop2
     * @param efficiency2
     *              efficiency2
     * @param latency
     *              latency
     */
    public DcopMessage(RegionIdentifier selfRegionID, int hop2, double efficiency2, Double latency) {
        // TODO Auto-generated constructor stub
        this.originalSender = selfRegionID;
        this.hop = hop2;
        this.efficiency = efficiency2;
        this.latency = latency;
    }

    @Override
    public String toString() {
        if(DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            return "[type=" + type + ", setExcessLoadMapToParent=" + excessLoadMapToParent + ", clientDemandMap="
                    + clientDemandMap + ", childrenMap=" + childrenMap + ", parentServicesMap=" + parentServicesMap
                    + "]";
        else if(DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION.equals(AgentConfiguration.getInstance().getDcopAlgorithm()))
            return "[message: " + "originalSender=" + originalSender + ", hop=" + hop + ", load=" + loadMap
                    + ", efficiency=" + efficiency + ", latency=" + latency + "]";
        else
            return "[type=" + typeMessage + ", serviceClientDemandMap="
                  + serviceClientDemandMap + ", tree=" + tree + ", isDataCenter "+isDataCenter+ ", last iteration "+iteration+"]";
    }
    
  ///*** PRDIFF methods
    /**
     * @return the type
     */
    public TypeMessage getTypeMessage() {
        return this.typeMessage;
    }
    
    /**
     * @param type the type to set
     */
    public void setTypeMessage(TypeMessage type) {
        this.typeMessage = type;
    }
    
    
    /**
     * @return the serviceClientDemandMap
     */
    public Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> getServiceClientDemandMap() {
        return serviceClientDemandMap;
    }
    
    /**
     * @param clientDemandMap the serviceClientDemandMap to set
     */
    public void setServiceClientDemandMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> clientDemandMap) {
        for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry:clientDemandMap.entrySet()){
            ServiceIdentifier<?> service = entry.getKey();
            for(Entry<RegionIdentifier, Double> clientLoad:entry.getValue().entrySet()){
                RegionIdentifier client = clientLoad.getKey();
                this.serviceClientDemandMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>())
                .put(client, clientLoad.getValue());
            }
        }
    }
    
    /**
     * @return tree
     */
    public DcopTree getTree(){
        return tree;
    }
    
    /**
     * @return last iteration
     */
    public int getLastIteration(){
        return iteration;
    }
    
    /**
     * @param service service
     * @return isDataCenter.get(service)
     */
    public boolean isDataCenter(ServiceIdentifier<?> service){
        return isDataCenter.get(service);
    }
    
    /**
     * @return isDataCenter
     */
    public Map<ServiceIdentifier<?>, Boolean> getIsDataCenter(){
        return isDataCenter;
    }
    
    /**
     * @param dataCenter dataCenter
     * @postcondition isDataCenter has a copy of dataCenter
     */
    public void copyIsDataCenter(Map<ServiceIdentifier<?>,Boolean> dataCenter){
        isDataCenter = new HashMap<ServiceIdentifier<?>,Boolean>();
        if(dataCenter.entrySet() != null)
        for(Entry<ServiceIdentifier<?>, Boolean> entry: dataCenter.entrySet())
            isDataCenter.put(entry.getKey(), entry.getValue());
    }
    
    /**
     * @param tree tree
     */
    private void copyTreeMap(DcopTree tree){
        this.tree = new DcopTree(tree);
        
    }
    ///
    
    @Override
    public boolean equals(Object dcopMessageObj) {
      // If the object is compared with itself then return true  
      if (dcopMessageObj == this) {
          return true;
      }
      
      if (!(dcopMessageObj instanceof DcopMessage)) {
        return false;
      }
         
      DcopMessage castedDcopMessageObj = (DcopMessage) dcopMessageObj;
        
      if(AgentConfiguration.getInstance().getDcopAlgorithm().equals(DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION)) {
          return this.getType() == castedDcopMessageObj.getType() &&
                  this.getClientDemandMap().equals(castedDcopMessageObj.getClientDemandMap()) &&
                  this.getExcessLoadMapToParent().equals(castedDcopMessageObj.getExcessLoadMapToParent());
      }
      else if(AgentConfiguration.getInstance().getDcopAlgorithm().equals(DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION)) {
          return this.originalSender.equals(castedDcopMessageObj.getOriginalSender()) &&
                  this.getHop() == castedDcopMessageObj.getHop() &&
                  this.getLoadMap() == castedDcopMessageObj.getLoadMap() &&
                  Double.compare(this.getEfficiency(), castedDcopMessageObj.getEfficiency()) == 0 &&
                  Double.compare(this.getLatency(), castedDcopMessageObj.getLatency()) == 0;
      }
      else {return this.getTypeMessage() == castedDcopMessageObj.getTypeMessage() &&
              this.getServiceClientDemandMap().equals(castedDcopMessageObj.getServiceClientDemandMap()); 
      }
    }
    
    @Override
    public int hashCode() {
        if(AgentConfiguration.getInstance().getDcopAlgorithm().equals(DcopAlgorithm.DISTRIBUTED_ROUTING_DIFFUSION)) {
            return Objects.hash(type, clientDemandMap, excessLoadMapToParent);
        }
        else if(AgentConfiguration.getInstance().getDcopAlgorithm().equals(DcopAlgorithm.DISTRIBUTED_CONSTRAINT_DIFFUSION)) {
            return Objects.hash(originalSender, hop, loadMap, efficiency, latency);
        }
        else return Objects.hash(typeMessage, serviceClientDemandMap);
    }


    /**
     * @return the type
     */
    public MessageType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(MessageType type) {
        this.type = type;
    }

    /**
     * @return the clientDemandMap
     */
    public Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> getClientDemandMap() {
        return clientDemandMap;
    }

    /**
     * @return the excessLoadMapToParent
     */
    public Map<ServiceIdentifier<?>, Double> getExcessLoadMapToParent() {
        return excessLoadMapToParent;
    }

    /**
     * @return original sender
     */
    public RegionIdentifier getOriginalSender() {
        return originalSender;
    }

    /**
     * @param originalSender
     *            original sender
     */
    public void setOriginalSender(RegionIdentifier originalSender) {
        this.originalSender = originalSender;
    }

    /**
     * @return number of hops
     */
    public int getHop() {
        return hop;
    }

    /**
     * @param hop
     *            number of hops
     */
    public void setHop(int hop) {
        this.hop = hop;
    }

    /**
     * @return load
     */
    public Map<ServiceIdentifier<?>, Double> getLoadMap() {
        return loadMap;
    }

    /**
     * @param loadMap
     *            load
     */
    public void setLoadMap(Map<ServiceIdentifier<?>, Double> loadMap) {
        this.loadMap.putAll(loadMap);
    }

    /**
     * @return efficiency
     */
    public double getEfficiency() {
        return efficiency;
    }

    /**
     * @param efficiency
     *            efficiency
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    /**
     * @return latency
     */
    public double getLatency() {
        return latency;
    }

    /**
     * @param latency
     *            latency
     */
    public void setLatency(double latency) {
        this.latency = latency;
    }
    /**
     * @return Comparator 
     *              sortByHop
     */
    public static Comparator<DcopMessage> getSortByHop() {
        return sortByHop;
    }

    /**
     * @param sortByHop1
     *              setter function of Comparator sortByHop
     */
    public static void setSortByHop(Comparator<DcopMessage> sortByHop1) {
        sortByHop = sortByHop1;
    }


    /**
     * @return the parentServicesMap
     */
    public Map<RegionIdentifier, Set<ServiceIdentifier<?>>> getParentServicesMap() {
        return parentServicesMap;
    }


    /**
     * @return the childrenMap
     */
    public Map<RegionIdentifier, Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>> getChildrenMap() {
        return childrenMap;
    }

    /**
     * @return the availableLoad
     */
    public double getAvailableLoad() {
        return availableLoad;
    }

    /**
     * @param availableLoad the availableLoad to set
     */
    public void setAvailableLoad(double availableLoad) {
        this.availableLoad = availableLoad;
    }

    /**
     * @author cwayllace For the aggregation: lower number of hops is better
     */
    private static Comparator<DcopMessage> sortByHop = new Comparator<DcopMessage>() {
        public int compare(DcopMessage o1, DcopMessage o2) {
            final int before = -1;
            final int equal = 0;
            final int after = 1;
            if (o1.getHop() == o2.getHop())
                return equal;
            else if (o1.getHop() < o2.getHop())
                return before;
            else
                return after;
        }
    };

}



