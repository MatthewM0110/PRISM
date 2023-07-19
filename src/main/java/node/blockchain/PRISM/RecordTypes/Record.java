package node.blockchain.PRISM.RecordTypes;

import java.io.Serializable;

public class Record implements Serializable{
    public enum RecordType  {
        Project,
        ProvenanceRecord,
      
    }

    private RecordType recordType;  
    private String workflowID;

    public RecordType getRecordType() {
        return recordType;
    }
    Record(RecordType type, String workflowID) {
        this.recordType = type;
        this.workflowID = workflowID;
    }
}
