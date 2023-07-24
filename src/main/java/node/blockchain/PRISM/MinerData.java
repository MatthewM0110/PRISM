package node.blockchain.PRISM;

import java.io.Serializable;

import node.communication.Address;

public class MinerData implements Serializable {

    private Address address;

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    private float timestamp; // assuming this is a Unix timestamp
    private String outputHash;

    public String getOutputHash() {
        return outputHash;
    }

    public void setOutputHash(String outputHash) {
        this.outputHash = outputHash;
    }

    public MinerData(Address address, float timestamp, String outputHash) {
        this.address = address;
        this.timestamp = timestamp;
        this.outputHash = outputHash;
    }

    public float getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(float timestamp) {
        this.timestamp = timestamp;
    }

    public String toString() {

        
        return "Address: " + address.getPort() + ", timestamp: " + timestamp + ", outputHash: " + outputHash.substring(0, 1);

    }

    public boolean equals(MinerData other) {
        if (this.timestamp == other.getTimestamp() && this.address.equals(other.getAddress())
                && this.outputHash.equals(other.getOutputHash())) {
            return true;
        }
        return false;

    }

}