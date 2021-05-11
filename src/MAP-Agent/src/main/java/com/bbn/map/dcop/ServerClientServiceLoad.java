package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import static com.bbn.map.dcop.AbstractDcopAlgorithm.compareDouble;

/**
 * @author khoihd
 *
 */
public final class ServerClientServiceLoad implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -2402222227876953941L;

    private final RegionIdentifier server;
    
    private final RegionIdentifier client;
    
    private final ServiceIdentifier<?> service;
        
    private final double load;
    
    /**
     * @param server .
     * @param client .
     * @param service .
     * @param load .
     */
    private ServerClientServiceLoad(RegionIdentifier server, RegionIdentifier client, ServiceIdentifier<?> service, double load) {
        this.server = server;
        this.client = client;
        this.service = service;
        this.load = load;
        // don't include things that have a fuzzy match in equals
        this.hashCode = Objects.hash(server, client, service);
    }
    
    /**
     * @param server .
     * @param client .
     * @param service .
     * @param load .
     * @return a new ServerClientServiceLoad object
     */
    public static ServerClientServiceLoad of(RegionIdentifier server, RegionIdentifier client, ServiceIdentifier<?> service, double load) {
        return new ServerClientServiceLoad(server, client, service, load);
    }
    
    /**
     * @param object .
     * @return a deep copy from ServerClientServiceLoad object
     */
    public static ServerClientServiceLoad deepCopy(ServerClientServiceLoad object) {
        return new ServerClientServiceLoad(object.getServer(), object.getClient(), object.getService(), object.getLoad());
    }

    /**
     * @return the server
     */
    public RegionIdentifier getServer() {
        return server;
    }

    /**
     * @return the client
     */
    public RegionIdentifier getClient() {
        return client;
    }

    /**
     * @return the service
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ServerClientServiceLoad other = (ServerClientServiceLoad) obj;
            if(this.hashCode != other.hashCode) {
                return false;
            }
            
            return Objects.equals(getServer(), other.getServer()) //
                    && Objects.equals(getClient(), other.getClient()) //
                    && Objects.equals(getService(), other.getService())
                    && compareDouble(load, other.getLoad()) == 0
            ;
        }
    }
    
    private final int hashCode;

    @Override
    public int hashCode() {
        return hashCode;
    }


    @Override
    public String toString() {
        return "ServerClientServiceLoad [server=" + server + ", client=" + client + ", service=" + service + ", load=" + load + "]";
    }
}
