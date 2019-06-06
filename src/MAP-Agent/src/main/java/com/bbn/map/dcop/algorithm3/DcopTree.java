package com.bbn.map.dcop.algorithm3;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dcop.DCOPService;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;


/**
 * @author cwayllace
 *
 */
public class DcopTree implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(DCOPService.class);
    private ResourceSummary summary;
    private Map<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> serviceClientChildMap;
    private Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>>neighServiceClientPools; 
    private Map<ServiceIdentifier<?>,Map<RegionIdentifier,RegionIdentifier>> serviceClientParentMap;
    private Map<ServiceIdentifier<?>,Integer> numChildren;
    private Map<ServiceIdentifier<?>,Integer> numParents;
    private int totalChildren, totalParents;
    private Set<RegionIdentifier> neighborSet;
    private Map<ServiceIdentifier<?>,Set<RegionIdentifier>> notInTree;
    
    /**
     * constructor initialize all maps.
     */
    public DcopTree(){
        numChildren = new HashMap<ServiceIdentifier<?>,Integer>();
        numParents = new HashMap<ServiceIdentifier<?>,Integer>();
        serviceClientChildMap = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>>();
        neighServiceClientPools = new HashMap<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>>();
        serviceClientParentMap = new HashMap<ServiceIdentifier<?>,Map<RegionIdentifier,RegionIdentifier>>();
        totalChildren = 0;
        totalParents = 0;
    }

    
    /**
     * @return serviceClientChildMap
     */
    public Map<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> getServiceClientChildMap(){
        return serviceClientChildMap;
    }

    

    /**
     * @return serviceClientParentMap
     */
    public Map<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> getServiceClientParentMap() {
        return serviceClientParentMap;
    }
    
    

    /**
     * @return numChildren
     */
    public Map<ServiceIdentifier<?>, Integer> getNumChildren() {
        return numChildren;
    }

    /**
     * @param service service
     * @param val value
     */
    public void setNumChildren(ServiceIdentifier<?>service, int val){
        this.numChildren.put(service, val);
    }
    /**
     * @return numParents
     */
    public Map<ServiceIdentifier<?>, Integer> getNumParents() {
        return numParents;
    }

    
    /**
     * @return totalChildren
     */
    public int getTotalChildren(){
        return totalChildren;
    }
    
    /**
     * @param val value
     */
    public void setTotalChildren(int val){
        totalChildren = val;
    }
    
    /**
     * @return totalParents
     */
    public int getTotalParents(){
        return totalParents;
    }
    
    /**
     * @param val value
     */
    public void setTotalParents(int val){
        totalParents = val;
    }
    
    /**
     * @param summary summary
     * @param neighbSet neighborSet
     */
    public DcopTree(ResourceSummary summary, Set<RegionIdentifier>neighbSet){
        this();
        this.summary =summary;
        this.neighborSet = neighbSet;

    }


    /**
     * build tree. 
     * @param appMgr 
     * @precondition: REQUIRED: Tx != Rx to find parents and children
     */
    public void buildTree(ApplicationManagerApi appMgr){
          /**temporal map to store (service, set of children) **/
          Map<ServiceIdentifier<?>, Set<RegionIdentifier>> tempChildren = new HashMap<ServiceIdentifier<?>, Set<RegionIdentifier>>();
          /**temporal map to store (service, set of parents) **/
          Map<ServiceIdentifier<?>, Set<RegionIdentifier>> tempParents = new HashMap<ServiceIdentifier<?>, Set<RegionIdentifier>>();
          //traverse network demand
          //neighbor->client->service->linkTxRx->load
          for (Entry<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> networkEntry : 
              summary.getNetworkDemand().entrySet()) {
              RegionIdentifier neighborFromNetwork = networkEntry.getKey();
              //client->service->linkTxRx->load
             for (Entry<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> demandEntryGivenNeighbor : 
                 networkEntry.getValue().entrySet()) {
                  RegionIdentifier client = demandEntryGivenNeighbor.getKey();
                  
                  //service->linkTxRx->load
                  ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceMap = demandEntryGivenNeighbor.getValue();
                  for (Entry<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> serviceEntry : serviceMap.entrySet()) {
                     //linkTxRx->load
                      int counter = 0; double tx = -1;
                      for(Entry<LinkAttribute<?>, Double> linkMap : serviceEntry.getValue().entrySet()){
                          ServiceIdentifier<?> service = serviceEntry.getKey();
                          if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                              if(numChildren.get(service) == null) numChildren.put(service, 0);
                              if(numParents.get(service) == null) numParents.put(service, 0);
                              
                              if(counter %2 == 0) tx = linkMap.getValue();
//                              else if(tx <= linkMap.getValue()){
                              else if(isIncoming(service, tx,linkMap.getValue(), appMgr)){//Khoi
                                  tempChildren.computeIfAbsent(service, s->new HashSet<RegionIdentifier>()).add(neighborFromNetwork);
                                  serviceClientChildMap.computeIfAbsent(service, s -> new HashMap<RegionIdentifier, RegionIdentifier>()).put(client, neighborFromNetwork);
                                  neighServiceClientPools.computeIfAbsent(neighborFromNetwork, s -> new HashMap<ServiceIdentifier<?>,Set<RegionIdentifier>>())
                                  .computeIfAbsent(service, c -> new HashSet<>()).add(client);
                              } 
                              else {
                                  tempParents.computeIfAbsent(service, s->new HashSet<RegionIdentifier>()).add(neighborFromNetwork);
                                  serviceClientParentMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier, RegionIdentifier>()).put(client, neighborFromNetwork);
                              }
                              counter++;
                          
                      }
                  }
                  }
             }

          }
          for(Entry<ServiceIdentifier<?>, Set<RegionIdentifier>> c:tempChildren.entrySet()){
              numChildren.put(c.getKey(), c.getValue().size());
              totalChildren += c.getValue().size();
          }
          for(Entry<ServiceIdentifier<?>, Set<RegionIdentifier>> p:tempParents.entrySet()){
              numParents.put(p.getKey(), p.getValue().size());
              totalParents += p.getValue().size();
          }

          LOGGER.info("CHILDREN "+serviceClientChildMap);
          LOGGER.info("PARENTS "+serviceClientParentMap);
      }
    
    /**
     * @param newTree newTree
     * @param selfRegionID selfRegionID
     * @description newTree is computed from network demand. The tree is updated with this info if it is valid.
     */
    public void createValidTree(DcopTree newTree, RegionIdentifier selfRegionID){
        //look for new children
        for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> entry:newTree.getServiceClientChildMap().entrySet()){
            ServiceIdentifier<?>service = entry.getKey();
            if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                for(Entry<RegionIdentifier, RegionIdentifier> clientChild:entry.getValue().entrySet()){
                    RegionIdentifier client = clientChild.getKey();
                    RegionIdentifier child = clientChild.getValue();
                    //ignore if client or child are parents
                    if(this.serviceClientParentMap.get(service) != null){
                        if(this.serviceClientParentMap.get(service).containsValue(child) || 
                                this.serviceClientParentMap.get(service).containsValue(client))
                            continue;
                    }
                    //ignore if client or child are in my region
                    if(client.equals(selfRegionID) || child.equals(selfRegionID)) continue;
                    
                    if(this.serviceClientChildMap.get(service) != null){
                        if(this.serviceClientChildMap.get(service).get(client) == null){
                            this.serviceClientChildMap.get(service).put(client,child);
                            LOGGER.info("*** added child "+this.serviceClientChildMap.get(service));
                            int num = numChildren.getOrDefault(service, 0);
                                numChildren.put(service, num+1);
                                totalChildren++;
                           for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Set<RegionIdentifier>>> servCliPool: 
                               newTree.neighServiceClientPools.entrySet()){
                               RegionIdentifier receiver = servCliPool.getKey();
                               Set<RegionIdentifier> pool = servCliPool.getValue().get(service);
                               if(pool.contains(client))
                               if(this.neighServiceClientPools.get(receiver) == null) 
                                   this.neighServiceClientPools.computeIfAbsent(receiver, s-> new HashMap<ServiceIdentifier<?>,Set<RegionIdentifier>>())//.put(service, pool);
                                   .computeIfAbsent(service, p->new HashSet<RegionIdentifier>()).add(client);
                               else if(this.neighServiceClientPools.get(receiver).get(service) == null)
                                   this.neighServiceClientPools.get(receiver).computeIfAbsent(service, p-> new HashSet<RegionIdentifier>()).add(client);
                               else this.neighServiceClientPools.get(receiver).get(service).add(client);
                           }
                        }
                        
                    }else{
                        serviceClientChildMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier, RegionIdentifier>()).put(client, child);
                        int num = numChildren.getOrDefault(service, 0);
                        numChildren.put(service, num+1);
                        totalChildren++;
                    }
                }
            }
        }
        //look for new parents
        LOGGER.info("*** LOOking for new parents "+newTree.getServiceClientParentMap());
        for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> entry:newTree.getServiceClientParentMap().entrySet()){
            ServiceIdentifier<?>service = entry.getKey();
            if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                for(Entry<RegionIdentifier, RegionIdentifier> clientChild:entry.getValue().entrySet()){
                    RegionIdentifier client = clientChild.getKey();
                    RegionIdentifier parent = clientChild.getValue();
                    //ignore if parent is children
//                    if(this.serviceClientChildMap.get(service) != null){
//                        if(this.serviceClientChildMap.get(service).containsValue(parent) || 
//                           this.serviceClientChildMap.get(service).containsValue(client))
//                            continue;
//                    }
                    if(this.serviceClientChildMap.get(service) != null){
                        if(this.serviceClientChildMap.get(service).containsValue(parent))
                            continue;
                    }
                    //ignore if  parent are in my region
                    if(parent.equals(selfRegionID)) continue;
                    if(this.serviceClientParentMap.get(service) != null){
                        if(this.serviceClientParentMap.get(service).get(client) == null){
                            LOGGER.info("*** added child1 "+this.serviceClientChildMap.get(service));
                            this.serviceClientParentMap.get(service).put(client,parent);
                            int num = numParents.getOrDefault(service, 0);
                            numParents.put(service, num+1);
                            totalParents++;
                        }
                    }else{
                        LOGGER.info("*** Adding a parent serviceClientParentMap "+serviceClientParentMap);
                        serviceClientParentMap.computeIfAbsent(service, c-> new HashMap<RegionIdentifier, RegionIdentifier>()).put(client, parent);
                        int num = numParents.getOrDefault(service, 0);
                        numParents.put(service, num+1);
                        totalParents++;
                    }
                }
            }
        }
        LOGGER.info(this.toString());
    }

    /** 
     * @see java.lang.Object#toString()
     * @return String
     */
    public String toString(){
        return "Children "+serviceClientChildMap+", parents "+serviceClientParentMap+", neighServiceClientPools "+neighServiceClientPools;
    }
    
    /**
     * @param srvClientChildMap child_ClientServiceMap
     */
    private void copyServiceClientChildMap(Map<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> srvClientChildMap){
        this.serviceClientChildMap = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>>();
        for(Entry<ServiceIdentifier<?>, Map<RegionIdentifier, RegionIdentifier>> entry: srvClientChildMap.entrySet()){
            ServiceIdentifier<?> service = entry.getKey();
            if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                Map<RegionIdentifier, RegionIdentifier> clientChild = new HashMap<RegionIdentifier, RegionIdentifier>();
                for(Entry<RegionIdentifier, RegionIdentifier> entryCS:entry.getValue().entrySet()){
                    clientChild.put(entryCS.getKey(), entryCS.getValue());
                }
                this.serviceClientChildMap.put(service, clientChild);
            }
        }
                
    }
    
    /**
     * @param clientServPools client_ServicePools
     */
    private void copyNeighServiceClientPools(Map<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>> nghServClientPools){
        neighServiceClientPools = new HashMap<RegionIdentifier,Map<ServiceIdentifier<?>,Set<RegionIdentifier>>>();
        
        for(Entry<RegionIdentifier, Map<ServiceIdentifier<?>, Set<RegionIdentifier>>> entry:nghServClientPools.entrySet()){
            RegionIdentifier neighbor = entry.getKey();
            Map<ServiceIdentifier<?>, Set<RegionIdentifier>> servRegSet = new HashMap<ServiceIdentifier<?>, Set<RegionIdentifier>>();
            for(Entry<ServiceIdentifier<?>, Set<RegionIdentifier>> servRegion: entry.getValue().entrySet()){
                ServiceIdentifier<?>service = servRegion.getKey();
                if(!service.equals(ApplicationCoordinates.AP) && !service.equals(ApplicationCoordinates.UNMANAGED)){
                    Set<RegionIdentifier> clients = servRegion.getValue();
                    for(RegionIdentifier client:clients){
                        clients.add(client);
                    }
                    servRegSet.put(service, clients);
                }
            }
            neighServiceClientPools.put(neighbor, servRegSet);
        }
        
    }
    
 
    
    /**
     * @param serviceClientParentMap serviceClientParentMap
     */
    private void copyServiceClientParentMap( Map<ServiceIdentifier<?>, Map<RegionIdentifier,RegionIdentifier>> serviceClientParentMap){
        this.serviceClientParentMap = new HashMap<ServiceIdentifier<?>, Map<RegionIdentifier,RegionIdentifier>>();
        for(Entry<ServiceIdentifier<?>,Map<RegionIdentifier, RegionIdentifier>> entry: serviceClientParentMap.entrySet()){
            if(!entry.getKey().equals(ApplicationCoordinates.AP) && !entry.getKey().equals(ApplicationCoordinates.UNMANAGED)){
                Map<RegionIdentifier, RegionIdentifier> clientParent = new HashMap<RegionIdentifier, RegionIdentifier>();
                for(Entry<RegionIdentifier, RegionIdentifier> clntParent :entry.getValue().entrySet()){
                    clientParent.put(clntParent.getKey(), clntParent.getValue());
                    
                }
                
                this.serviceClientParentMap.put(entry.getKey(), clientParent);
            }
        }
    }
    

    /**
     * @param numChild num_Children
     */
    private void copyNumChildren(Map<ServiceIdentifier<?>, Integer> numChild){
        if(numChild.isEmpty()) numChildren = new HashMap<>();
        else
        for(Entry<ServiceIdentifier<?>, Integer> entry :numChild.entrySet()){
            if(!entry.getKey().equals(ApplicationCoordinates.AP) && !entry.getKey().equals(ApplicationCoordinates.UNMANAGED))
                numChildren.put(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * @param numParnts num_Parents
     */
    private void copyNumParents(Map<ServiceIdentifier<?>, Integer> numParnts){
        if(numParnts.isEmpty()) numParents = new HashMap<>();
        else
        for(Entry<ServiceIdentifier<?>, Integer> entry :numParnts.entrySet()){
            if(!entry.getKey().equals(ApplicationCoordinates.AP) && !entry.getKey().equals(ApplicationCoordinates.UNMANAGED))
                numParents.put(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Copy constructor.
     * @param old old object
     */
    public DcopTree (DcopTree old){
        this();
        copyServiceClientChildMap(old.serviceClientChildMap);
        copyNeighServiceClientPools(old.neighServiceClientPools);
        copyServiceClientParentMap(old.serviceClientParentMap);
        copyNumChildren(old.numChildren);
        copyNumParents(old.numParents);
        totalChildren = old.totalChildren;
        totalParents = old.totalParents;
        
    }

    /**
     * @return neighServiceClientPools
     */
    public Map<RegionIdentifier, Map<ServiceIdentifier<?>, Set<RegionIdentifier>>> getNeighServiceClientPools() {
        return neighServiceClientPools;
    }


   /**
 * @return true if tree is empty
 */
public boolean isEmpty(){
       return
               serviceClientChildMap.isEmpty() && neighServiceClientPools.isEmpty() &&
               serviceClientParentMap.isEmpty();
  
   }
 
    
/**
 * @param service service
 * @param tx tx
 * @param rx rx
 * @param appMgr appMgr
 * @return false if tx equals rx, true if tx greater than rx and TX_GREATER or tx smaller than rx and RX_GREATER
 */
public boolean isIncoming(ServiceIdentifier<?> service, double tx, double rx, ApplicationManagerApi appMgr){
    LOGGER.info("***** {}", appMgr.getAllApplicationSpecifications());
    for(ApplicationSpecification spec :appMgr.getAllApplicationSpecifications()){
        if(spec.getCoordinates().getIdentifier().equals(service.getIdentifier())){
            if(spec.getTrafficType().toString().equals("RX_GREATER")){
                return ( Double.compare(rx, tx)>0);
            }
            if(spec.getTrafficType().toString().equals("TX_GREATER")){
                return ( Double.compare(rx, tx)<0); 
            }
        }
    
    }
    return false;
}


}
