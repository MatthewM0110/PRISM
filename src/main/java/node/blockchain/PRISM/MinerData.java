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

    private long timestamp; // assuming this is a Unix timestamp
    private String outputHash;

    public String getOutputHash() {
        return outputHash;
    }

    public void setOutputHash(String outputHash) {
        this.outputHash = outputHash;
    }

    public MinerData(Address address, long timestamp, String outputHash) {
        this.address = address;
        this.timestamp = timestamp;
        this.outputHash = outputHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String toString(){
        return "Address: " + address + ", timestamp: " + timestamp + ", outputHash: " + outputHash;

    }

    public boolean equals(MinerData other){
        if(this.timestamp == other.getTimestamp() && this.address == other.getAddress() && this.outputHash == other.getOutputHash()){
            return true;
        }
        return false;

    }

}