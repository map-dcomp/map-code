package com.bbn.map.dcop.algorithm3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopConstants;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopMessage;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dcop.MessagesPerIteration;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * @author cwayllace
 *
 */
public class WeightedAlgorithm {
    /**
     * This is all the message types sent between DCOP agents.
     */
    public enum TypeMessage {
        /**
         * Telling a neighbor that I'm your parent and you're my children.
         */
        PARENT_TO_CHILDREN,
        /**
         * Sending load to the parent.
         */
        CHILDREN_TO_PARENT,
        /**
         * Write tree information of this DCOP iteration.
         */
        TREE
    }
    private static final int TREE_ITERATION = -1;
    private static final Logger LOGGER = LoggerFactory.getLogger(DCOPService.class);
  //For new algorithm
    
    /*
     * service->total incoming load (including server demand)
     */
    private Map<ServiceIdentifier<?>,Double> serviceInLoadMap;
    /*
     * neighbor->service->total outgoing load
     */
    private Map<RegionIdentifier,Map<ServiceIdentifier<?>,Double>> recServiceTotalOutLoadMap;   
    /*
     * receiver->service->client->load
     */
    private Map<RegionIdentifier, Map<ServiceIdentifier<?>,Map<RegionIdentifier,Double>>> recServiceClientOutLoadMap;
    /*
     * service->client->load
     */
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceClientKeepLoadMap;
    private Map<ServiceIdentifier<?>, Map< RegionIdentifier, Double>> tmpServiceClientKeepLoadMap;
    private Map<ServiceIdentifier<?>, Map< RegionIdentifier, Double>> tmpServiceClientOutLoadMap;
    private Map<ServiceIdentifier<?>,Map<RegionIdentifier,RegionIdentifier>> tmpServiceClientRecMap;
    /*
     * service->client->load
     */
    private Map<ServiceIdentifier<?>,Map<RegionIdentifier, Double>> serviceClientExcessLoadMap;
    
    private Map<ServiceIdentifier<?>, Double> totalNetworkDemand;
    private Map<ServiceIdentifier<?>, Double> totalServerDemand;
    private double regionCapacity;
    
    //////////////////////////////

    /*
     *  Assumes one Data Center per Service
     */
    private Map<ServiceIdentifier<?>, Boolean> isDataCenter;
    private ResourceSummary summary;
    private DcopSharedInformation inbox;
    private DcopTree tree;
    private DcopInfoProvider dcopInfoProvider;
    private RegionIdentifier selfRegionID;
    /**Number of parents per service that sent meaningful data*/
    private Map<ServiceIdentifier<?>,Integer> countParents;
    /**Number of children per service that sent meaningful data*/
    private Map<ServiceIdentifier<?>,Integer> countChildren;
    private ApplicationManagerApi appMgr;
    /**0: waiting for messages; 1: received 2: excess load has been sent */
    private Map<ServiceIdentifier<?>, Integer >receivedFromAllParents;
    private Set<RegionIdentifier> neighborSet;
    
    /**
     * to be used in compute plan: incomingoLoad - excessLoad can generate very small values instead of 0.
     */
    public static final double VALUE_AFTER_DIFFERENCE = 0.0001;

    /**
     * @param summary summary
     * @param inbx inbox
     * @param dcopInfoProvider2 dcopInfoProvider
     * @param selfRgID this region id
     * @param regCap region capacity
     * @param appMgr application manager
     * @param neighborSet neighborSet
     */
    public WeightedAlgorithm(ResourceSummary summary, DcopSharedInformation inbx,
            DcopInfoProvider dcopInfoProvider2, 
            RegionIdentifier selfRgID, double regCap,
            ApplicationManagerApi appMgr, Set<RegionIdentifier> neighborSet ){
        this.summary = summary;
        inbox = inbx;
        dcopInfoProvider = dcopInfoProvider2;
        selfRegionID = selfRgID;
        serviceClientExcessLoadMap = new HashMap<>();
        serviceInLoadMap = new HashMap<ServiceIdentifier<?>, Double>();
        recServiceTotalOutLoadMap = new HashMap<RegionIdentifier, Map<ServiceIdentifier<?>, Double>>();
        recServiceClientOutLoadMap = new HashMap<>();
        serviceClientKeepLoadMap = new HashMap<>();
        totalNetworkDemand = new HashMap<ServiceIdentifier<?>, Double>();
        totalServerDemand  = new HashMap<ServiceIdentifier<?>, Double>();
        regionCapacity =  regCap;
        this.appMgr = appMgr;
        countParents = new HashMap<ServiceIdentifier<?>,Integer>();
        countChildren = new HashMap<ServiceIdentifier<?>,Integer>();
        receivedFromAllParents = new HashMap<ServiceIdentifier<?>, Integer >();
        this.neighborSet = neighborSet;
    }
    
    /**
     * @return dcopInfoProvider
     */
    public DcopInfoProvider getDcopInfoProvider() {
        return dcopInfoProvider;
    }

    /**
     * @param dcopInfoProvider dcopInfoProvider
     */
    public void setDcopInfoProvider(DcopInfoProvider dcopInfoProvider) {
        this.dcopInfoProvider = dcopInfoProvider;
    }

    /**
     * @return inbox
     */
    public DcopSharedInformation getInbox() {
        return inbox;
    }

    /**
     * @param inbox inbox
     */
    public void setInbox(DcopSharedInformation inbox) {
        this.inbox = inbox;
    }

   
    
    /**
     * @param service
     * @param appMgr
     * @precondition service is not UNKNOWN
     * @return priority of service
     */
    private int getPriorityFrom(ServiceIdentifier<?> service, ApplicationManagerApi appMgr){
        ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(appMgr,service);
        return spec.getPriority();
    }

    /**
     * Run weighted algorithm.
     * @param newIteration newIteration
     * @return plan
     */
    public RegionPlan run(int newIteration){
        if (inbox != null) {
            if (!inbox.isEmptyNewAlgorithm()) {
                
                Map<RegionIdentifier, DcopMessage> messageTreeMap;
                try {
                    messageTreeMap = waitForMessages(TREE_ITERATION, DcopConstants.READING_TREE);
                    readPreviousTreeFromMessage(messageTreeMap);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            }
            
        }  else{// 1st DCOP run
            
            tree = new DcopTree(summary, neighborSet);
            tree.buildTree(appMgr);
        } 
    computeTotalServerDemand(summary);
    setDataCenter(summary);  LOGGER.info("*** isDataCenter "+isDataCenter+" total server demand "+totalServerDemand); 
    if(!totalServerDemand.isEmpty()) {
        DcopTree tree2 = new DcopTree(summary, neighborSet);
        tree2.buildTree(appMgr);
        tree.createValidTree(tree2,selfRegionID);
        computeTotalNetworkDemand(summary);
        
    }
    clearSelfInbox();
    //initialize serviceInLoadMap with server demand
    if(!totalNetworkDemand.isEmpty()) copyAddTotalLoadMapFrom(totalServerDemand, serviceInLoadMap);
    else serviceInLoadMap = new HashMap<ServiceIdentifier<?>,Double>();
    //if it has server demand of any service prepare outload OR EXCESS
    Set<ServiceIdentifier<?>> services = new HashSet<ServiceIdentifier<?>>();
    for(Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry: summary.getServerDemand().entrySet()) 
        if(!entry.getKey().equals(ApplicationCoordinates.AP) && !entry.getKey().equals(ApplicationCoordinates.UNMANAGED))
            services.add(entry.getKey()); //services in Server Demand
    fillOutLoadMapPerService(summary, newIteration, services);
    fillExcessLoadMap();
    clearSelfInbox();
    Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>> parentServiceClientMap = setParentServiceClientMap();
    tmpServiceClientKeepLoadMap = new HashMap<ServiceIdentifier<?>, Map< RegionIdentifier, Double>>();
    tmpServiceClientOutLoadMap = new HashMap<ServiceIdentifier<?>, Map< RegionIdentifier, Double>>();
    tmpServiceClientRecMap = new HashMap<ServiceIdentifier<?>,Map<RegionIdentifier, RegionIdentifier>>();
    setCountParentsAndChildren();
    //iterations
    int lastIteration = 0;
    for (int iteration = newIteration; iteration < AgentConfiguration.getInstance().getDcopIterationLimit() + newIteration; iteration++) {
        Map<RegionIdentifier, DcopMessage> receivedMessageMap;
        keepInboxUpdated();
        //send messages
        LOGGER.info("*** Sending messages "+recServiceClientOutLoadMap);
        sendMessages(iteration, parentServiceClientMap);

        
        // waiting for CHILD or EMPTY messages from neighbors
        try {
           receivedMessageMap = waitForMessages(iteration, DcopConstants.NOT_READING_TREE);
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.warn("InterruptedException when waiting for messages. Return the default DCOP plan: {} ",
                    e.getMessage(), e);
            return null; //will return default plan
        }
        
        processReceivedMessages(receivedMessageMap);
        
        LOGGER.info("Iteration {} Region {} Incoming {}", iteration, selfRegionID, getServiceInLoadMap());
        LOGGER.info("Iteration {} Region {} Outgoing {}", iteration, selfRegionID, getRecServiceTotalOutLoadMap());
        LOGGER.info("Iteration {} Region {} LoadToKeep {}", iteration, selfRegionID, getServiceClientKeepLoadMap());
        LOGGER.info("Iteration {} Region {} ExcessLoad {}", iteration, selfRegionID, getServiceClientExcessLoadMap());
        lastIteration = iteration;
    }//end of DCOP iterations
    return computePlan(lastIteration);
    }
    
    

    /**
     * @return tree
     */
    public DcopTree getTree() {
        return tree;
    }

    /**
     * @description Sets countParents and countParents to 0 for all services found in 
     * tree.getNumParents() and tree.getNumChildren() also set receivedFromAllParents to 0
     */
    private void setCountParentsAndChildren() {
        if(tree.getNumParents().entrySet() != null)
            for(Entry<ServiceIdentifier<?>, Integer> parentCount:tree.getNumParents().entrySet()){
                if(!parentCount.getKey().equals(ApplicationCoordinates.AP) && !parentCount.getKey().equals(ApplicationCoordinates.UNMANAGED)){
                    countParents.put(parentCount.getKey(), 0);
                    receivedFromAllParents.put(parentCount.getKey(), 0);
                }
            }
        if(tree.getNumChildren().entrySet() != null)
           for(Entry<ServiceIdentifier<?>, Integer> childrenCount:tree.getNumChildren().entrySet()) {
               if(!childrenCount.getKey().equals(ApplicationCoordinates.AP) && !childrenCount.getKey().equals(ApplicationCoordinates.UNMANAGED))
                   countChildren.put(childrenCount.getKey(), 0);
           }
        
    }

    /**
     * @param iteration
     * @param receivedMessageMap Messages received
     * @param summary2
     * @param parentServiceClientMap 
     * @description from receivedMessageMap fill tmpServiceClientKeepLoadMap and tmpServiceClientOutLoadMap
     */
    private void processReceivedMessages(Map<RegionIdentifier, DcopMessage> receivedMessageMap) {
        boolean isValidPC = false;
        boolean isValidCP = false;
        Map<Integer, Set<ServiceIdentifier<?>>> priorityService = new TreeMap<Integer, Set<ServiceIdentifier<?>>>(Collections.reverseOrder());
        for(Entry<RegionIdentifier, DcopMessage> entry:receivedMessageMap.entrySet()){
            RegionIdentifier sender = entry.getKey();
            for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> servCliLoad:
                entry.getValue().getServiceClientDemandMap().entrySet()){
                ServiceIdentifier<?> service = servCliLoad.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    priorityService.computeIfAbsent(getPriorityFrom(service, appMgr), s-> new HashSet<ServiceIdentifier<?>>()).add(service);
                    for(Entry<RegionIdentifier, Double> cliLoad:servCliLoad.getValue().entrySet()){
                        RegionIdentifier client = cliLoad.getKey();
                        double load = cliLoad.getValue();
                        if(cliLoad.getValue()!=-1.0){ //valid load
                            if(entry.getValue().getTypeMessage().name().equals("PARENT_TO_CHILDREN")){

                                if(client.equals(selfRegionID)){
                                    //update keepLoad and ExcessLoad
                                    double tmpLoad = 0.0;
                                    if(tmpServiceClientKeepLoadMap.get(service) != null)
                                    tmpLoad = tmpServiceClientKeepLoadMap.get(service).getOrDefault(client, 0.0);
                                    tmpServiceClientKeepLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, load+tmpLoad);
                                }else{
                                    //update ouload
                                    double tmpLoad = 0.0;
                                    if(tmpServiceClientOutLoadMap.get(service) != null)
                                    tmpLoad = tmpServiceClientOutLoadMap.get(service).getOrDefault(client, 0.0);
                                    tmpServiceClientOutLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, load+tmpLoad);
                                }
                                isValidPC = true;
                                double storedLoad = serviceInLoadMap.getOrDefault(service, 0.0);
                                serviceInLoadMap.put(service, load + storedLoad);
    
                            }else if(entry.getValue().getTypeMessage().name().equals("CHILDREN_TO_PARENT")){
                                //if client is this region, add to income
                                if(client.equals(selfRegionID)){
                                    double inLoad = serviceInLoadMap.getOrDefault(service, 0.0);
                                    serviceInLoadMap.put(service, inLoad+load);
                                }
                                //update keepLoad and ExcessLoad
                                double tmpLoad = 0.0;
                                if(tmpServiceClientKeepLoadMap.get(service) != null)
                                    tmpLoad = tmpServiceClientKeepLoadMap.get(service).getOrDefault(client, 0.0);
                                tmpServiceClientKeepLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, load+tmpLoad);
                                tmpServiceClientRecMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier, RegionIdentifier>()).put(client, sender);
                                isValidCP = true;
                            }
                        }else{
                            isValidPC = false;
                            isValidCP = false;
                        }
                    }
                    
                    if(isValidPC){
                        int num =  countParents.getOrDefault(service, 0);
                        countParents.put(service, num+1);
                    }
                    if(isValidCP){
                        int num =  countChildren.getOrDefault(service, 0);
                        countChildren.put(service, num+1);
                    }
                }
            }
        }
            //if all messages have been received compute excess, outload
            //update keepload, services are sorted according to priority

            for(Entry<Integer, Set<ServiceIdentifier<?>>> servicePriority: priorityService.entrySet()){
                Set<ServiceIdentifier<?>> setService = servicePriority.getValue();
                for(ServiceIdentifier<?> service:setService){
                if(countChildren.get(service) != null && countChildren.get(service).equals(tree.getNumChildren().get(service))
                        && receivedFromAllParents.get(service)==1) {
                    updateKeepExcess(service, true);
                    if(tmpServiceClientKeepLoadMap.get(service) != null) tmpServiceClientKeepLoadMap.remove(service);
                    if(tmpServiceClientRecMap.get(service) != null) tmpServiceClientRecMap.remove(service);
                    
                }
                if(countParents.get(service)!= null && countParents.get(service).equals(tree.getNumParents().get(service)) && receivedFromAllParents.get(service)==0) {
                    receivedFromAllParents.put(service,1);
                    updateKeepExcess(service, false);
                    if(tmpServiceClientKeepLoadMap.get(service) != null) tmpServiceClientKeepLoadMap.remove(service);
                    if(!tree.getNumParents().get(service).equals(0)){
                        if(tmpServiceClientOutLoadMap.get(service) != null)
                            updateOutLoad(service);
                        if(tmpServiceClientOutLoadMap.get(service) != null) tmpServiceClientOutLoadMap.remove(service);
                    }
                }
                }
                
            }//for servicePriority
        
        
        
    }
   

    /**
     * @return DCOP plan
     */
    private RegionPlan computePlan(int iteration) {
        keepInboxUpdated();
        sendTreeInfomation(iteration); //write tree information to be sent next DCOP round

      LOGGER.info("AFTER DCOP FOR LOOP, Region {} Incoming {}", selfRegionID, getServiceInLoadMap());
      LOGGER.info("AFTER DCOP FOR LOOP, Region {} Outgoing {}", selfRegionID, getRecServiceTotalOutLoadMap());
      LOGGER.info("AFTER DCOP FOR LOOP, Region {} LoadToKeep {}", selfRegionID, getServiceClientKeepLoadMap());
      LOGGER.info("AFTER DCOP FOR LOOP, Region {} ExcessLoad {}", selfRegionID, getServiceClientExcessLoadMap());
      
      if(!getServiceInLoadMap().isEmpty() && this.isDataCenter.isEmpty() && tree.getTotalChildren() == 0 && tree.getTotalParents() == 0)
          LOGGER.info("ERROR DETECTING TREE, CHECK NETWORK DEMAND "+this.totalNetworkDemand);
      Map<ServiceIdentifier<?>, Double> totalKeepLoad = totalLoad(serviceClientKeepLoadMap);
      // Return default plan if totalLoads coming is 0
      //if total incoming load is 0 execute the plan by default. For now just execute it all the time
      if(Double.compare(getServiceInLoadMap().values().stream().mapToDouble(Double::doubleValue).sum(),0) == 0){
          return null;
      }

      // here generate the true plan
      Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlanBuilder = ImmutableMap.builder();
      Builder<RegionIdentifier, Double> regionPlanBuilder;

      for (Entry<ServiceIdentifier<?>, Double> serviceEntry : serviceInLoadMap.entrySet()) {
          ServiceIdentifier<?> service = serviceEntry.getKey();
          if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
              double totalLoadFromThisService = serviceEntry.getValue();
              
              Map<ServiceIdentifier<?>, Double> totalExcessLoad = totalLoad(serviceClientExcessLoadMap);
              for(Entry<ServiceIdentifier<?>, Double> entry: totalExcessLoad.entrySet()){
                  if(entry.getKey()!= null && entry.getKey().equals(service)){
                      
                      double load = entry.getValue();
                      if(load < 0) load = 0.0;
                      totalLoadFromThisService -= load;
                  }
              }
              
              regionPlanBuilder = ImmutableMap.builder();
              for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> entry: recServiceTotalOutLoadMap.entrySet()){
                  double load = entry.getValue().getOrDefault(service,0.0);
                  if(Double.compare(totalLoadFromThisService, VALUE_AFTER_DIFFERENCE) > 0)
                      regionPlanBuilder.put(entry.getKey(), load / totalLoadFromThisService);
                  else regionPlanBuilder.put(entry.getKey(), 1.0);
              }
              
              if(Double.compare(totalLoadFromThisService, VALUE_AFTER_DIFFERENCE) > 0){ //can generate very small values instead of 0
                  regionPlanBuilder.put(selfRegionID,totalKeepLoad.get(service) / totalLoadFromThisService);
              }
              else{
                  regionPlanBuilder.put(selfRegionID, 1.0);
              }
              ImmutableMap<RegionIdentifier, Double> regionPlan = regionPlanBuilder.build();
              
            
              
              servicePlanBuilder.put(service, regionPlan);
          }
      }

      ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopPlan = servicePlanBuilder
              .build();

      LOGGER.info("REGION PLAN Region {}: {}", selfRegionID, dcopPlan.toString());

      final RegionPlan rplan = new RegionPlan(summary.getRegion(), dcopPlan);

      return rplan;
      
    }

    /**
     * @param iteration last iteration
     * @description Send message type TREE to copy relevant information of this.tree.
     */
    private void sendTreeInfomation(int iteration) {
        DcopMessage dcopMsg = new DcopMessage(TypeMessage.TREE, this.tree, iteration);
        
        sendMessage(selfRegionID,TREE_ITERATION,dcopMsg);
        
    }

    /**
     * @return 0 (or more if more than second DCOP run)
     */
    public int initializeIteration(){
        int newIteration = 0;
        if (inbox != null) {
            // initialize newIteration by taking the max iteration from the inbox and increment it
            newIteration = inbox.getIterationDcopInfoMap().keySet().stream().mapToInt(Integer::intValue).max().getAsInt() + 1;
        }
        return newIteration;
        
    }
    
    /**
     * @param receivedMessageMap tree from prev DCOP run
     */
    public void readPreviousTreeFromMessage(Map<RegionIdentifier, DcopMessage> receivedMessageMap){
        //copy the received tree
        tree = receivedMessageMap.get(selfRegionID).getTree();

        isDataCenter = receivedMessageMap.get(selfRegionID).getIsDataCenter();
                

        LOGGER.info("Number of children {}:", this.tree.getNumChildren());
        LOGGER.info("Number of parents {}:", this.tree.getNumParents());
        LOGGER.info("Children from TREE: {}", this.tree.getServiceClientChildMap());
        LOGGER.info("Parents from TREE: {}", this.tree.getServiceClientParentMap());
        LOGGER.info("Client pools: {} ", this.tree.getNeighServiceClientPools());
    }
    
    
   
    
    /** Find Data Center based on serverDemand from ResourceSummary.
     * @param summary is the ResourceSummary
     * @precondition assume no new datacenters are added after 1st DCOP run
     * @postcondition isDataCenter is true if this is a DataCenter in the first DCOP run
     * @useful
     */
    public void setDataCenter(ResourceSummary summary) {
        isDataCenter = new HashMap<ServiceIdentifier<?>, Boolean>();
    
    
        for(Entry<ServiceIdentifier<?>, Double> serviceLd:totalServerDemand.entrySet()){
            if(!serviceLd.getKey().equals(ApplicationCoordinates.AP) && !serviceLd.getKey().equals(ApplicationCoordinates.UNMANAGED)){
                if(tree.getServiceClientParentMap().get(serviceLd.getKey()) == null && Double.compare(serviceLd.getValue(), 0) > 0){
                    isDataCenter.put(serviceLd.getKey(), true);
                }
            }
    
        }

    }

    /**
     * Compute total load from networkDemand.
     * used to infer the client source.
     * @param summary summary
     */
    private void computeTotalNetworkDemand(ResourceSummary summary){
        //from networkDemand
        if(!summary.getNetworkDemand().isEmpty())
        for(Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> entryNet:
            summary.getNetworkDemand().entrySet()){
            if(!entryNet.getValue().isEmpty())
            for(Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> clientService:entryNet.getValue().entrySet()){
                if(!clientService.getValue().isEmpty())
                for(Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceLoad : clientService.getValue().entrySet()){
                    ServiceIdentifier<?> service = serviceLoad.getKey();
                    if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                        double tx = -1;
                        
                        for(Entry<LinkAttribute<?>, Double> link:serviceLoad.getValue().entrySet()){
                            if(link.getKey().toString().contains("TX")){
                                
                                tx = link.getValue();
//                            }else if(link.getKey().toString().contains("RX") && Double.compare(tx,link.getValue()) <= 0){//if it is receiving or RX==TX 
                            }else if(link.getKey().toString().contains("RX") && tree.isIncoming(service, tx, link.getValue(), appMgr)){//Khoi 

                                double temp = totalNetworkDemand.getOrDefault(service,0.0);
                                temp += tx;
                                totalNetworkDemand.put(service, temp);
                                
                            }
                        }
                    }
                    
                }
            }
        }//end networkDemand
        LOGGER.info("*** total networkDemand "+totalNetworkDemand);
    }
    /**
     * Compute the total load received from serverDemand per service.
     * will be equivalent to value received in GLOBAL.
     * @param summary summary
     */
    private  void computeTotalServerDemand(ResourceSummary summary){
        
        //from serverDemand
        for (Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry : summary.getServerDemand().entrySet()) {
            double totalLoad = 0.0;
            if(entry != null){
                ServiceIdentifier<?> service = entry.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    for (Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> innerEntry : entry.getValue().entrySet()) {
                        if(innerEntry != null){
                            totalLoad += innerEntry.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0.0);
                            totalServerDemand.put(service, totalLoad);
                            
                        }//end if innerEntry != null
                    }//end for
                }
            }
        }
    }
    
    /**
     * Prepare outLoad and excessLoad.
     * @param summary summary
     * @param iteration iteration
     * @param service service
     * @precondition this region has serverDemand. services do not contain UNKNOWN
     */
    private void fillOutLoadMapPerService(ResourceSummary summary, int iteration, Set<ServiceIdentifier<?>> services){
        Map<RegionIdentifier,Map<ServiceIdentifier<?>,Map<RegionIdentifier, Double>>> recServiceClientLoad; 
        if(!totalServerDemand.isEmpty()){
            recServiceClientLoad = findSourceClient(summary,services);
            for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> entry:recServiceClientLoad.entrySet()){
                RegionIdentifier receiver = entry.getKey();
                for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> servCliLd:entry.getValue().entrySet()){
                    ServiceIdentifier<?>service = servCliLd.getKey();
                    for(Entry<RegionIdentifier, Double> cliLd: servCliLd.getValue().entrySet()){
                        RegionIdentifier client = cliLd.getKey();
                        if(tree.getNeighServiceClientPools().get(receiver) != null &&
                           tree.getNeighServiceClientPools().get(receiver).get(service) != null &&
                           tree.getNeighServiceClientPools().get(receiver).get(service).contains(client)){
                            recServiceClientOutLoadMap.computeIfAbsent(receiver, s-> new HashMap<ServiceIdentifier<?>,Map<RegionIdentifier,Double>>()).computeIfAbsent(service,c-> new HashMap<RegionIdentifier,Double>()).put(client, cliLd.getValue());
                        }else if(tree.getServiceClientParentMap().get(service)!= null ){
                            if(tree.getServiceClientParentMap().get(service).values().contains(client)){
                                serviceClientExcessLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, cliLd.getValue());
                            }
                            
                        }
                    }
                }
            }
            
        }

        //child->service->client->oLoad
        //fill outload with -1.0 for all children per service 
        if(this.tree.getServiceClientChildMap() != null)
            for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>>entry: this.tree.getServiceClientChildMap().entrySet()){
               ServiceIdentifier<?> service = entry.getKey();
               if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    for(Entry<RegionIdentifier, RegionIdentifier> clientChild:entry.getValue().entrySet()){
                        RegionIdentifier client = clientChild.getKey();
                        RegionIdentifier child = clientChild.getValue();
                        if(recServiceClientOutLoadMap.get(child) != null){
                            if(recServiceClientOutLoadMap.get(child).get(service) == null)
                                recServiceClientOutLoadMap.get(child).computeIfAbsent(service, c-> new HashMap<RegionIdentifier, Double>()).put(client, -1.0);
                        }else recServiceClientOutLoadMap.computeIfAbsent(child, s -> new HashMap<ServiceIdentifier<?>,
                                Map<RegionIdentifier, Double>>()).computeIfAbsent(service, r -> new HashMap<RegionIdentifier, Double>()).put(client, -1.0);
                    }
               }
            }

        
        //copy to recServiceTotalOutLoadMap
        for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> from:recServiceClientOutLoadMap.entrySet()){
            Map<ServiceIdentifier<?>, Double> serviceTotalOutLoadMap = totalLoad(from.getValue());
            recServiceTotalOutLoadMap.put(from.getKey(), serviceTotalOutLoadMap);
        }
        
    }
    
    /**
     * Fills serviceClientKeepLoadMap with 0.0 if there is no other load.
     */
    private void fillExcessLoadMap(){
        //service->client->Parent
        //fill keepload with -1.0 for all parents per service 
        if(this.tree.getServiceClientParentMap() != null && tree.getServiceClientParentMap().entrySet() != null)
            for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>>entry: this.tree.getServiceClientParentMap().entrySet()){
                ServiceIdentifier<?> service = entry.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    for(Entry<RegionIdentifier, RegionIdentifier> clientParent:entry.getValue().entrySet()){
                        RegionIdentifier client = clientParent.getKey();
                        if(serviceClientExcessLoadMap.get(service) != null){
                            if(serviceClientExcessLoadMap.get(service).get(client) == null){
                                if(tree.getNumChildren().get(service).equals(0))
                                    serviceClientExcessLoadMap.get(service).putIfAbsent(client, 0.0);
                                else
                                    serviceClientExcessLoadMap.get(service).putIfAbsent(client, -1.0);
                            }
                        }
                        else {
                            if(tree.getNumChildren().get(service) == null)  tree.setNumChildren(service, 0);
                            if(tree.getNumChildren().get(service).equals(0)){
                                serviceClientExcessLoadMap.computeIfAbsent(service, s -> new HashMap<RegionIdentifier,
                                        Double>()).put(client, 0.0);
                            
                        }else
                            serviceClientExcessLoadMap.computeIfAbsent(service, s -> new HashMap<RegionIdentifier,
                                 Double>()).put(client, -1.0);
                        }
                    }
                }
                
            }
        
    }
    
    


    /**
     * From serverDemand INFER the clientsPer service and an amount of load for each one.
     * using networkDemand
     * @precondition services does not contain UNKNOWN
     * @param summary summary
     * @param services services
     * @return neighbor->inferredClientSource->service->load
     * Equivalent to ServerDemand, if source = selfRegionId neighbor <- selfRegionId
     * Checks if the values in network demand correspond to children in the tree
     */
    private Map<RegionIdentifier,Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>> findSourceClient(ResourceSummary summary, Set<ServiceIdentifier<?>> services){
        Map<RegionIdentifier,Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>> recServiceClientInferredLoad =
                new HashMap<RegionIdentifier,Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>>();
       
        //neighbor->sourceClient->service->load
        for(Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> entryNet:
            summary.getNetworkDemand().entrySet()){
            RegionIdentifier receiver = entryNet.getKey();
          //sourceClient -> service -> load
           
            Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>serviceSourceLoad = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>();
            Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>myServiceSourceLoad = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier,Double>>();
            for(Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>
            clientService:entryNet.getValue().entrySet()){
                RegionIdentifier source = clientService.getKey();
                
                
                for(Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceLoad : clientService.getValue().entrySet()){
                     ServiceIdentifier<?> service = serviceLoad.getKey();
                     double tx = -1;
                    if(services.contains(service))
                        
                    for(Entry<LinkAttribute<?>, Double> link:serviceLoad.getValue().entrySet()){
                        //if tx<=rx this region is receiving
                        
                        if(link.getKey().toString().contains("TX")) {
                            tx = link.getValue(); 
//                        }else if(link.getKey().toString().contains("RX")  && Double.compare(tx,link.getValue()) <= 0){
                        }else if(link.getKey().toString().contains("RX")  && tree.isIncoming(service, tx, link.getValue(), appMgr)){ //khoi
                            double loadPerClient = tx*totalServerDemand.getOrDefault(service, 0.0)/totalNetworkDemand.getOrDefault(service, 1.0);
                            
                            if(!source.equals(selfRegionID)){
                                serviceSourceLoad.computeIfAbsent(service, c-> new HashMap<RegionIdentifier, Double>()).put(source, loadPerClient);
                            }else
                                myServiceSourceLoad.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(source, loadPerClient);
                        }
                    }
                 }
                
                if(!serviceSourceLoad.isEmpty()) recServiceClientInferredLoad.put(receiver, serviceSourceLoad);
                    
                if(!myServiceSourceLoad.isEmpty()) recServiceClientInferredLoad.put(selfRegionID, myServiceSourceLoad);
            }
            
        }
        LOGGER.info("*** inferred demand "+ recServiceClientInferredLoad);
        return recServiceClientInferredLoad;
    }
    
    

    
    
    /**
     * @param iteration iteration
     * @precondition recServiceClientOutLoadMap and serviceClientExcessLoadMap have all required values
     * @description Send messages to parents and children. 
     * Aggregate information for all (services, clients) to send one message per region
     * If haven't received valid messages from all parents, send -1 to all children
     * If haven't received valid messages from all children, send -1 to all parents
     */
    private void sendMessages(int iteration, Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>> parentServiceClientMap) {
    //If (isDataCenter(service) OR (received PARENT_TO_CHILDREN message AND countParents(service) = numParents(service))) AND haven’t sent PARENT_TO_CHILDREN messages yet :
        DcopMessage msg;
        if(!recServiceClientOutLoadMap.isEmpty()){
        for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> tempRecServClientLoad:
            recServiceClientOutLoadMap.entrySet() ){
            RegionIdentifier receiver = tempRecServClientLoad.getKey();
            Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> forChildren = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>();

            for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> tempServClientLoad: 
                tempRecServClientLoad.getValue().entrySet()){
                ServiceIdentifier<?> service = tempServClientLoad.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    Map<RegionIdentifier, Double> regLoad = new HashMap<RegionIdentifier, Double>();
                    for(Entry<RegionIdentifier, Double> tempClientLd: 
                        tempServClientLoad.getValue().entrySet()){
                        if(isDataCenter.getOrDefault(service, false) || countParents.get(service).equals(tree.getNumParents().get(service))){
                            regLoad.put(tempClientLd.getKey(), tempClientLd.getValue());
                            //copy to store recServiceClientOutLoadMap
                            
                        }
                        else //change load to -1
                            regLoad.put(tempClientLd.getKey(), -1.0);
                    }
                    if(isDataCenter.getOrDefault(service, false) || countParents.get(service).equals(tree.getNumParents().get(service))){
                        //before reseting store recServiceClientOutLoadMap
                        resetOutLoadMap(service, regLoad.keySet());
                    }
                    
                    forChildren.put(service, regLoad);
                }
               
            }
            
            msg = new DcopMessage(TypeMessage.PARENT_TO_CHILDREN,forChildren);
            sendMessage(receiver, iteration, msg);
        }
        resetCountParents();
        }
//        If received CHILDREN_TO_PARENT messages AND countChildren(service) = numChildren(service) AND haven’t sent CHILDREN_TO_PARENT message yet:
     
        
        DcopMessage dcopMsg;
        if(!parentServiceClientMap.isEmpty()){
        for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Set<RegionIdentifier>>> parentServClient:
            parentServiceClientMap.entrySet()){
            RegionIdentifier parent = parentServClient.getKey();
            Map<ServiceIdentifier<?>,Map<RegionIdentifier,Double>> forParent = new HashMap<ServiceIdentifier<?>,Map<RegionIdentifier,Double>>();
            
            for(Entry<ServiceIdentifier<?>, Set<RegionIdentifier>> servClient: parentServClient.getValue().entrySet()){
                ServiceIdentifier<?> service = servClient.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    Map<RegionIdentifier, Double> tempClientLoad = new HashMap<RegionIdentifier, Double>();
                    for(Entry<RegionIdentifier, Double> tempClientLd:serviceClientExcessLoadMap.get(service).entrySet()){
                        if(servClient.getValue().contains(tempClientLd.getKey()) || 
                                tempClientLd.getKey().equals(tree.getServiceClientParentMap().get(service).get(selfRegionID))){
                            if(countChildren.get(service).equals(tree.getNumChildren().get(service)) && receivedFromAllParents.get(service)==1)
                                tempClientLoad.put(tempClientLd.getKey(), tempClientLd.getValue());
                            else
                                tempClientLoad.put(tempClientLd.getKey(), -1.0);
                        }
                    }
                    forParent.put(service, tempClientLoad);
                }
                
            }
            dcopMsg = new DcopMessage(TypeMessage.CHILDREN_TO_PARENT, forParent);
            if(parent != null) sendMessage(parent, iteration, dcopMsg);
        }
        
        //reset receivedFromAllParents
        
        for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Set<RegionIdentifier>>> parentServClient:
            parentServiceClientMap.entrySet()){
            for(ServiceIdentifier<?> service: parentServClient.getValue().keySet()){
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED))
                if(countChildren.get(service).equals(tree.getNumChildren().get(service)) && receivedFromAllParents.get(service)==1)
                    receivedFromAllParents.put(service, 2);
            }
        }
        resetCountChildren();
        }
   
    }
    
    

    /**
     * reset countChildren to 0 if all messages from children have been received.
     */
    private void resetCountChildren() {
        
        for(Entry<ServiceIdentifier<?>, Integer> servCount:countChildren.entrySet()){
            ServiceIdentifier<?> service = servCount.getKey();
            if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED))
            if(countChildren.get(service).equals(tree.getNumChildren().get(service)))
                countChildren.put(service, 0);
        }
        
    }

    /**
     * reset countParents to 0 if all messages from parents have been received.
     */
    private void resetCountParents() {
        for(Entry<ServiceIdentifier<?>, Integer> servCoun:countParents.entrySet()){
            ServiceIdentifier<?> service = servCoun.getKey();
            if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED))
            if(countParents.get(service).equals(tree.getNumParents().get(service)))
                countParents.put(service, 0);
        }
    }
    


    
    /**
     * @param service service
     * @param setClients 
     * @precondition last valid values of recServiceClientOutLoadMap have been sent
     * @description reset loads of recServiceClientOutLoadMap(service, setClients) to -1.0
     */
    private void resetOutLoadMap(ServiceIdentifier<?> service, Set<RegionIdentifier> setClients) {
        if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
        if(this.tree.getServiceClientChildMap() != null)
            for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>>entry: this.tree.getServiceClientChildMap().entrySet()){
                if(entry.getKey().equals(service) && entry.getValue().keySet().containsAll(setClients))
                for(Entry<RegionIdentifier, RegionIdentifier> clientChild:entry.getValue().entrySet()){
                    RegionIdentifier client = clientChild.getKey();
                    RegionIdentifier child = clientChild.getValue();
                    if(setClients.contains(client))
                    if(recServiceClientOutLoadMap.get(child) != null)
                        if(recServiceClientOutLoadMap.get(child).get(service) != null)
                          recServiceClientOutLoadMap.get(child).get(service).put(client, -1.0);
                    
                        else recServiceClientOutLoadMap.get(child).computeIfAbsent(service, c-> new HashMap<RegionIdentifier, Double>()).put(client, -1.0);
                    else recServiceClientOutLoadMap.computeIfAbsent(child, s -> new HashMap<ServiceIdentifier<?>,
                            Map<RegionIdentifier, Double>>()).computeIfAbsent(service, r -> new HashMap<RegionIdentifier, Double>()).put(client, -1.0);
                }
                
            }
        }

    }
    
    /**
     * @return parentServiceClientMap
     * Reverse parentMapping so parent is first
     */
    private Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>> setParentServiceClientMap(){
         Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>> parentServiceClientMap = 
                 new HashMap<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>>();
         LOGGER.info("*** REVERSING tree.getServiceClientParentMap() "+tree.getServiceClientParentMap() );
         for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> entry:
             tree.getServiceClientParentMap().entrySet()){
             ServiceIdentifier<?> service = entry.getKey();
             if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                 RegionIdentifier parent;
                 for(Entry<RegionIdentifier, RegionIdentifier> clientParent:entry.getValue().entrySet()){
                     RegionIdentifier client = clientParent.getKey();
                     parent = clientParent.getValue();
                     
                     parentServiceClientMap.computeIfAbsent(parent, s->new HashMap<ServiceIdentifier<?>,Set<RegionIdentifier>>())
                     .computeIfAbsent(service, c->new HashSet<RegionIdentifier>()).add(client);
                 }
             }
             
         }
         LOGGER.info("*** REVERSED "+parentServiceClientMap);
         return parentServiceClientMap;
     }


    
    /**
     * @param service service
     * @param isFromChildren 
     * @description copy tmpServiceClientKeepLoadMap into serviceClientKeepLoadMap
     * if regionCapacity allows it, otherwise adds extra to serviceClientExcessLoadMap
     */
    private void updateKeepExcess(ServiceIdentifier<?>service, boolean isFromChildren){
        if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED))
        for( Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry: tmpServiceClientKeepLoadMap.entrySet()){
            if(service.equals(entry.getKey())){
            for(Entry<RegionIdentifier, Double> cliLoad : entry.getValue().entrySet()){
                RegionIdentifier client = cliLoad.getKey();
                double load = cliLoad.getValue();

                if(isDataCenter.getOrDefault(service, false) && load > 0){ //Assumption: a DataCenter has infinite capacity
                    //compute total load kept
                    
                    double loadKept = loadKeptPerClient(serviceClientKeepLoadMap, service, client);
                    serviceClientKeepLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, load+loadKept);
                    regionCapacity -= load;
                    if(isFromChildren && tree.getNumChildren().get(service) > 0 && !client.equals(selfRegionID)) {
                        RegionIdentifier sender = tmpServiceClientRecMap.get(service).get(client);
                        double outLd = recServiceTotalOutLoadMap.get(sender).get(service);
                        if(outLd < 0) outLd = 0.0;
                        if(load <= outLd) recServiceTotalOutLoadMap.get(sender).put(service, outLd-load);
                        else {
                            recServiceTotalOutLoadMap.get(sender).put(service, 0.0);
                            double inLoad = serviceInLoadMap.get(service);
                            serviceInLoadMap.put(service, inLoad + load - outLd);
                        }
                    }
                }else{//update excessLoad: check if there is space
                    if(regionCapacity >= load){
                      //compute total load kept
                        double storedLoad = loadKeptPerClient(serviceClientKeepLoadMap, service, client);
                        serviceClientKeepLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, load+storedLoad);
                        regionCapacity -= load;
                        storedLoad = loadKeptPerClient(serviceClientExcessLoadMap, service, client);
                        serviceClientExcessLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client,storedLoad);
                        if(isFromChildren && tree.getNumChildren().get(service) > 0 && !client.equals(selfRegionID)) {
                            RegionIdentifier sender = tmpServiceClientRecMap.get(service).get(client);
                            double outLd = recServiceTotalOutLoadMap.get(sender).get(service);
                            if(outLd < 0) outLd = 0.0;
                            if(load <= outLd) recServiceTotalOutLoadMap.get(sender).put(service, outLd-load);
                            else{
                                recServiceTotalOutLoadMap.get(sender).put(service, 0.0);
                                double inLoad = serviceInLoadMap.get(service);
                                serviceInLoadMap.put(service, inLoad + load - outLd);
                            }
                        }
                    }else{
                        double excess = load - regionCapacity;
                        double excessStored = loadKeptPerClient(serviceClientExcessLoadMap, service, client);
                        serviceClientKeepLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, regionCapacity);
                        
                        serviceClientExcessLoadMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier,Double>()).put(client, excessStored+excess);
                        if(isFromChildren && tree.getNumChildren().get(service) > 0 && !client.equals(selfRegionID)) {
                            RegionIdentifier sender = tmpServiceClientRecMap.get(service).get(client);
                            double outLd = recServiceTotalOutLoadMap.get(sender).get(service);
                            if(outLd < 0) outLd = 0.0;
                            if(outLd >= load) recServiceTotalOutLoadMap.get(sender).put(service, outLd-load);
                            else {
                                recServiceTotalOutLoadMap.get(sender).put(service, 0.0);
                                double inLoad = serviceInLoadMap.get(service);
                                serviceInLoadMap.put(service, inLoad + load - outLd);
                            }
                        }
                        regionCapacity = 0;
                    }
                }
          
            
            }
          }//end if
        }
    }
    
    /**
     * @param serviceClientKeepLoadMap
     * @param service 
     * @param client
     * @return 0.0 if is not storing anything yet or the load stored
     * @description Compute total load stored in serviceClientKeepLoadMap for this service and client
     * @precondition this method is called only to update valid values of load
     */
    private double loadKeptPerClient(Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> serviceClientKeepLoadMap, ServiceIdentifier<?> service, RegionIdentifier client) {
        double load = 0.0;
        if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED))
        if(serviceClientKeepLoadMap.get(service) != null )
            load = serviceClientKeepLoadMap.get(service).getOrDefault(client, 0.0);
        if(load == -1.0) load = 0.0;
        return load;
    }

    /**
     * @param service
     * @precondition Assume (service, client) are requested by only ONE interface
     */
    private void updateOutLoad(ServiceIdentifier<?>service){
        if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
            for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> outLoad:recServiceClientOutLoadMap.entrySet()){
                RegionIdentifier receiver = outLoad.getKey();
                for( Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry: outLoad.getValue().entrySet()){
                
                if(service.equals(entry.getKey())){
                for(Entry<RegionIdentifier, Double> cliLoad : entry.getValue().entrySet()){
                    RegionIdentifier client = cliLoad.getKey();
                    double storedLoad = cliLoad.getValue();
                    if(storedLoad <=0) storedLoad = 0.0;
                    double load = 0.0;
                    if(tmpServiceClientOutLoadMap.get(service) != null) 
                        load = tmpServiceClientOutLoadMap.get(service).getOrDefault(client, 0.0);
                    recServiceClientOutLoadMap.get(receiver).get(service).put(client, load+storedLoad);
              
                
                }
              }//end if
            }
            
        }
    
            
            //copy to recServiceTotalOutLoadMap
    
            for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>> from:recServiceClientOutLoadMap.entrySet()){
                Map<ServiceIdentifier<?>, Double> serviceTotalOutLoadMap = totalLoad(from.getValue(),service);
                if(!serviceTotalOutLoadMap.isEmpty())
                    recServiceTotalOutLoadMap.get(from.getKey()).put(service, serviceTotalOutLoadMap.get(service));
                
            }
        }
    }
    
   
    
    /**
     * @param serviceClientLoad
     * @return total load per service without client information
     */
    private Map<ServiceIdentifier<?>,Double> totalLoad(Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>> serviceClientLoad){
        
        Map<ServiceIdentifier<?>,Double> totalLoadMap = new HashMap<ServiceIdentifier<?>,Double>();
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : 
            serviceClientLoad.entrySet()) {
            double totalLoad = 0.0;
            if(entry != null){
                ServiceIdentifier<?> service = entry.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    for (Entry<RegionIdentifier, Double> innerEntry : entry.getValue().entrySet()) {
                        if(innerEntry.getValue() != null)
                            totalLoad += innerEntry.getValue();
                    }
                    totalLoadMap.put(service, totalLoad);
                    
                }
            }
        }
        return totalLoadMap;
    }
    
 
/**
 * @param serviceClientLoad
 * @param service
 * @return one entry with totalLoadMap for this service or empty
 */
private Map<ServiceIdentifier<?>,Double> totalLoad(Map<ServiceIdentifier<?>, Map<RegionIdentifier,Double>> serviceClientLoad, ServiceIdentifier<?>service){
        
    Map<ServiceIdentifier<?>,Double> totalLoadMap = new HashMap<ServiceIdentifier<?>,Double>();
    if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
        for (Entry<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> entry : 
            serviceClientLoad.entrySet()) {
            double totalLoad = 0.0;
            if(entry != null){
                if(service.equals(entry.getKey())){   
                    for (Entry<RegionIdentifier, Double> innerEntry : entry.getValue().entrySet()) {
                        if(innerEntry.getValue() != null)
                            totalLoad += innerEntry.getValue();
                    }
                    totalLoadMap.put(service, totalLoad);
                }
            }
        }
    }
        return totalLoadMap;
    }
    /**
     * @param fromMap
     * @param toMap
     * @return a deep copy from fromMap added to the values in toMap
     */
    private Map<ServiceIdentifier<?>,Double> copyAddTotalLoadMapFrom(Map<ServiceIdentifier<?>,Double> fromMap,
            Map<ServiceIdentifier<?>,Double> toMap){
        
        if(toMap == null) toMap = new HashMap<ServiceIdentifier<?>,Double>();
        for(Entry<ServiceIdentifier<?>, Double> entry:fromMap.entrySet()){
            if(!entry.getKey().equals(ApplicationCoordinates.AP) && !entry.getKey().equals(ApplicationCoordinates.UNMANAGED)){
                if(totalNetworkDemand.get(entry.getKey()) != null){
                double load = toMap.getOrDefault(entry.getKey(), 0.0);
                toMap.put(entry.getKey(), entry.getValue()+load);
                }
            }
        }
        return toMap;
    }
    
    /**
     * @return serviceInLoadMap
     */
    public Map<ServiceIdentifier<?>, Double> getServiceInLoadMap() {
        return serviceInLoadMap;
    }


    /**
     * @return serviceClientKeepLoadMap
     */
    public Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> getServiceClientKeepLoadMap() {
        return serviceClientKeepLoadMap;
    }

    /**
     * @return recServiceTotalOutLoadMap
     */
    public Map<RegionIdentifier, Map<ServiceIdentifier<?>, Double>> getRecServiceTotalOutLoadMap() {
        return recServiceTotalOutLoadMap;
    }


    /**
     * @return serviceClientExcessLoadMap
     */
    public Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>> getServiceClientExcessLoadMap() {
        return serviceClientExcessLoadMap;
    }

 
    
    /**
     * @param iteration iteration
     * @param isReadingTree isReadingTree
     * @return a mapping from neighbor -> Message
     * @throws InterruptedException when the sleeping thread is interrupted
     */
    public Map<RegionIdentifier, DcopMessage> waitForMessages(int iteration, boolean isReadingTree) throws InterruptedException {
        int counter = 0;
        //receive only from children or parents
        Map<RegionIdentifier, DcopMessage> messageMap = new HashMap<RegionIdentifier, DcopMessage>();
        int noMessageToRead = isReadingTree ? 1 : tree.getTotalChildren()+tree.getTotalParents();
        do {
            keepInboxUpdated();
            
            for (Map.Entry<RegionIdentifier, DcopSharedInformation> entry : dcopInfoProvider
                    .getAllDcopSharedInformation().entrySet()) {
                MessagesPerIteration message = entry.getValue().getMessageAtIteration(iteration);
                if (message != null && message.isSentTo(selfRegionID, iteration)
                        && !message.getMessageForThisReceiver(selfRegionID).equals(new DcopMessage())) {
                    if(messageMap.put(message.getSender(), message.getMessageForThisReceiver(selfRegionID)) == null){
                        if(isReadingTree) counter++;
                        else counter += message.getMessageForThisReceiver(selfRegionID).getServiceClientDemandMap().entrySet().size();
                        LOGGER.info("*** MESSAGEMAP "+messageMap);
                    }
                }
            }
            // wait before continue a new loop of reading messages
            Thread.sleep(DcopConstants.SLEEPTIME_WAITING_FOR_MESSAGE);
        } while (counter < noMessageToRead);
        
        for (Entry<RegionIdentifier, DcopMessage> entry : messageMap.entrySet()) {
            LOGGER.info("Iteration {} Region {} receives message {} from {}", iteration, selfRegionID, entry.getValue(), entry.getKey());
        }
        return messageMap;
    }
    
    /**
     * @param receiver receiver
     * @param iteration iteration
     * @param dcopMsg dcopMsg
     */
    private void sendMessage(RegionIdentifier receiver, int iteration,DcopMessage dcopMsg) {
        keepInboxUpdated();
        // The first time adding a message to this iteration
        if (inbox.getMessageAtIteration(iteration) == null) {
            MessagesPerIteration msgPerIter = new MessagesPerIteration(selfRegionID, iteration);
            msgPerIter.addMessageToReceiver(receiver, dcopMsg);
            inbox.addIterationDcopInfo(iteration, msgPerIter);
        }
        else { 
            MessagesPerIteration currentMsgAtThisIteration = inbox.getMessageAtIteration(iteration);
            currentMsgAtThisIteration.addMessageToReceiver(receiver, dcopMsg);
            inbox.addIterationDcopInfo(iteration, currentMsgAtThisIteration);
        }
        DcopSharedInformation messageToSend = new DcopSharedInformation(inbox);
        dcopInfoProvider.setLocalDcopSharedInformation(messageToSend);
        LOGGER.info("Iteration {} Region {} sends message {} to {}", iteration, selfRegionID, dcopMsg, receiver);
    }
    
    /**
     * To clear self mailbox from previous DCOP round.
     * This can prevent delete other's mailbox while they're reading their trees.
     * @useful
     */
    private void clearSelfInbox() {
        dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID).clear();
    }
    /**
    Read AllDcopSharedInformation for this region.
    @useful
    */
    private void keepInboxUpdated() {
       inbox = dcopInfoProvider.getAllDcopSharedInformation().get(selfRegionID);
            
    }
  

}
