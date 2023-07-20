package node.blockchain.PRISM;

import node.blockchain.Transaction;
import node.blockchain.PRISM.RecordTypes.ProvenanceRecord;
import node.blockchain.PRISM.RecordTypes.Record;
import node.blockchain.PRISM.RecordTypes.Record.RecordType;
import node.communication.utils.Hashing;

public class PRISMTransaction extends Transaction {

    private ProvenanceRecord prismRecord;
    private String timestamp;
    

    public PRISMTransaction(ProvenanceRecord rec, String timestamp) {
      
        System.out.println("WE GOT A PROVRECENCE RECORD");
        UID = Hashing.getSHAString(rec.toString() + timestamp);
        this.prismRecord = rec;
        this.timestamp = timestamp;
    

    }

    public ProvenanceRecord getRecord() {
            return prismRecord;
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return "Record:" + UID + " Timestamp:" + timestamp;
    }

    public String getUID() {
        return UID; 
    }

}
