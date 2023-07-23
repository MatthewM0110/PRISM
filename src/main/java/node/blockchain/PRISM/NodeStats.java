package node.blockchain.PRISM;

public class NodeStats {
    private int eligibleCount;
    private int quorumCount;
    private int minerCount;

    public NodeStats() {
        this.eligibleCount = 0;
        this.quorumCount = 0;
        this.minerCount = 0;
    }

    public void incrementEligibleCount() {
        this.eligibleCount++;
    }

    public void incrementQuorumCount() {
        this.quorumCount++;
    }

    public void incrementMinerCount() {
        this.minerCount++;
    }

    public int getEligibleCount() {
        return this.eligibleCount;
    }

    public int getQuorumCount() {
        return this.quorumCount;
    }

    public int getMinerCount() {
        return this.minerCount;
    }

    @Override
    public String toString() {
        return "Eligible Count: " + eligibleCount + ", Quorum Count: " + quorumCount + ", Miner Count: " + minerCount;
    }
}
