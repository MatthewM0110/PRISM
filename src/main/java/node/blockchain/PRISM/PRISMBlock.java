package node.blockchain.PRISM;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        HashMap<String, Integer> outputHashCounts = new HashMap<>();
    
        for (MinerData data : minerData.values()) {
            String outputHash = data.getOutputHash();
            if (outputHashCounts.containsKey(outputHash)) {
                outputHashCounts.put(outputHash, outputHashCounts.get(outputHash) + 1);
            } else {
                outputHashCounts.put(outputHash, 1);
            }
        }
    
        String mostPopularOutputHash = null;
        int maxCount = 0;
    
        for (Map.Entry<String, Integer> entry : outputHashCounts.entrySet()) {
            if (mostPopularOutputHash == null || entry.getValue() > maxCount) {
                mostPopularOutputHash = entry.getKey();
                maxCount = entry.getValue();
            }
        }
    
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
