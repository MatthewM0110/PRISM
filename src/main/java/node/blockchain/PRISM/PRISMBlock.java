package node.blockchain.PRISM;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import node.blockchain.Block;
import node.blockchain.Transaction;
import node.blockchain.PRISM.RecordTypes.Project;
import node.blockchain.defi.DefiTransaction;
import node.communication.Address;

public class PRISMBlock extends Block {

      
    HashMap<Address, MinerData> minerData;

     public PRISMBlock(HashMap<String, Transaction> txList, String prevBlockHash, int blockId) {

        /* Setting variables inherited from Block class */
        this.txList = new HashMap<>();
        this.prevBlockHash = prevBlockHash;
        this.blockId = blockId;
      

        /* Converting the transaction from Block to DefiTransactions*/
        HashSet<String> keys = new HashSet<>(txList.keySet());
        for(String key : keys){
            PRISMTransaction transactionInList = (PRISMTransaction) txList.get(key);
            this.txList.put(key, transactionInList);
        }
    }
    
   
}
