package com.bbn.map.dcop;

import java.io.Serializable;
import java.util.Objects;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * @author khoihd
 *
 */
public class ServerClientService implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -7687053729145396197L;

    private RegionIdentifier server;
    
    private RegionIdentifier client;
    
    private ServiceIdentifier<?> service;
    
    /**
     * Default constructor.
     */
    public ServerClientService() {}
    
    /**
     * @param server .
     * @param client .
     * @param service .
     */
    public ServerClientService(RegionIdentifier server, RegionIdentifier client, ServiceIdentifier<?> service) {
        this.server = server;
        this.client = client;
        this.service = service;
    }
    
    /**
     * Copy constructor.
     * @param object .
     */
    public ServerClientService(ServerClientService object) {
        this(object.getServer(), object.getClient(), object.getService());
    }

    /**
     * @return the server
     */
    public RegionIdentifier getServer() {
        return server;
    }

    /**
     * @param server the server to set
     */
    public void setServer(RegionIdentifier server) {
        this.server = server;
    }

    /**
     * @return the client
     */
    public RegionIdentifier getClient() {
        return client;
    }

    /**
     * @param client the client to set
     */
    public void setClient(RegionIdentifier client) {
        this.client = client;
    }

    /**
     * @return the service
     */
    public ServiceIdentifier<?> getService() {
        return service;
    }

    /**
     * @param service the service to set
     */
    public void setService(ServiceIdentifier<?> service) {
        this.service = service;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || getClass() != obj.getClass()) {
            return false;
        } else {
            final ServerClientService other = (ServerClientService) obj;
            return Objects.equals(getServer(), other.getServer()) //
                    && Objects.equals(getClient(), other.getClient()) //
                    && Objects.equals(getService(), other.getService())
            ;
        }
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(server, client, service);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ServerClientService [server=");
        builder.append(server);
        builder.append(", client=");
        builder.append(client);
        builder.append(", service=");
        builder.append(service);
        builder.append("]");
        return builder.toString();
    }
}
