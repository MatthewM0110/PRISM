package node.communication;

import java.io.Serializable;

public class Address implements Serializable {
    private final int port;
    private final String host;

    public Address(int port, String host){
        this.port = port;
        this.host = host;
    }

    public int getPort(){
        return port;
    }

    public String getHost(){
        return host;
    }

    public boolean equals(Address address){
        // A1: 8001, 127.0.0.1
        // A1: 8001, 127.0.0.1
        // A2: 8009, 127.0.0.1
        // A1: 8001, 127.0.0.2
        // A!: 8001, localhost
        // System.out.println("Comparing ports " + this.port + " and " + address.getPort() + " Hosts: " 
        // + this.host + " and " + address.getHost() + ". Result:" + (this.port == address.getPort() && this.host.equals(address.getHost())));
        return this.port == address.getPort() && this.host.equals(address.getHost());
    }

    @Override
    public String toString() {
        return String.valueOf(port).concat("_" + host);
    }

    // public int hashCode(){
    //     return port + host.hashCode();
    // }
}
