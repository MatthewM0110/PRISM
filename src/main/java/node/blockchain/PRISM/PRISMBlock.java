package node.blockchain.PRISM;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import node.blockchain.Block;
import node.blockchain.Transaction;
import node.blockchain.PRISM.RecordTypes.Project;
import node.blockchain.defi.DefiTransaction;
import node.communication.Address;

public class PRISMBlock extends Block {

    HashMap<Address, MinerData> minerData;
    String correctOutput;

    public String getCorrectOutput() {
        return correctOutput;
    }

    public void setCorrectOutput(String correctOutput) {
        this.correctOutput = correctOutput;
    }

    public HashMap<Address, MinerData> getMinerData() {
        return minerData;
    }

    public void setMinerData(HashMap<Address, MinerData> minerData) {
        this.minerData = minerData;

        /* Looping through minerData to find the most popular outputHash */
        HashMap<String, Integer> outputHashFrequency = new HashMap<>();
        for (MinerData data : minerData.values()) {
            String outputHash = data.getOutputHash();
            outputHashFrequency.put(outputHash, outputHashFrequency.getOrDefault(outputHash, 0) + 1);
        }

        String mostPopularOutputHash = Collections.max(outputHashFrequency.entrySet(),
                Comparator.comparingInt(Map.Entry::getValue)).getKey();

        this.correctOutput = mostPopularOutputHash;

    }

    public PRISMBlock(HashMap<String, Transaction> txList, String prevBlockHash, int blockId,
            HashMap<Address, MinerData> minerData) {

        /* Setting variables inherited from Block class */
        this.txList = new HashMap<>();
        this.prevBlockHash = prevBlockHash;
        this.blockId = blockId;
        this.minerData = minerData;

        /* Converting the transaction from Block to DefiTransactions */
        HashSet<String> keys = new HashSet<>(txList.keySet());
        for (String key : keys) {
            PRISMTransaction transactionInList = (PRISMTransaction) txList.get(key);
            this.txList.put(key, transactionInList);
        }

    }

}
