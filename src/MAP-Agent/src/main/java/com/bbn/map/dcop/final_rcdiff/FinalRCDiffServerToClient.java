package com.bbn.map.dcop.final_rcdiff;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

import static com.bbn.map.dcop.AbstractDcopAlgorithm.compareDouble;

/**
 * @author khoihd
 *
 */
public final class FinalRCDiffServerToClient implements FinalRCDiffMessageContent, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -6241366924267352358L;
    
    private final RegionIdentifier server;
    
    private final RegionIdentifier client;
    
    private final ServiceIdentifier<?> service;
    
    private final double load;
    
    /**
     * @param server .
     * @param client .
     * @param service . 
     * @param load .
     * @return a new FinalRCDiffServerToClient object
     */
    public static FinalRCDiffServerToClient of(RegionIdentifier server, RegionIdentifier client, ServiceIdentifier<?> service, double load) {
        return new FinalRCDiffServerToClient(server, client, service, load);
    }
    
    /**
     * @param object .
     * @return a deep copy of the object
     */
    public static FinalRCDiffServerToClient deepCopy(FinalRCDiffServerToClient object) {
        return new FinalRCDiffServerToClient(object.getServer(), object.getClient(), object.getService(), object.getLoad());
    }
    
    private FinalRCDiffServerToClient(RegionIdentifier server, RegionIdentifier client, ServiceIdentifier<?> service, double load) {
        this.server = server;
        this.client = client;
        this.service = service;
        this.load = load;
        // don't include anything that does a fuzzy match in equals
        this.hashCode = Objects.hash(server, client, service);
    }
    
    /**
     * @return server
     */
    public RegionIdentifier getServer() {
        return server;
    }

    /**
     * @return client
     */
    public RegionIdentifier getClient() {
        return client;
    }

    /**
     * @return service
     */
    public ServiceIdentifier<?> getService() {
        return service;
    }

    /**
     * @return load
     */
    public double getLoad() {
        return load;
    }

    private final int hashCode;
    
    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FinalRCDiffServerToClient other = (FinalRCDiffServerToClient) obj;
        if(this.hashCode != other.hashCode) { 
            return false;
        }
        
        return Objects.equals(server, other.getServer())
                && Objects.equals(service, other.getService())
                && Objects.equals(client, other.getClient())
                && compareDouble(load, other.getLoad()) == 0 
                ;
    }

    @Override
    public String toString() {
        return "FinalRCDiffServerToClient [server=" + server + ", client=" + client + ", service=" + service + ", load="
                + load + "]";
    }
    
    
}
