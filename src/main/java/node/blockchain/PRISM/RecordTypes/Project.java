package node.blockchain.PRISM.RecordTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.StringJoiner;

import node.blockchain.Transaction;
import node.blockchain.PRISM.PRISMTransaction;
import node.communication.Address;
import node.communication.utils.Hashing;

public class Project extends Record {


    String uniqueWorkflowID;
    String hypothesis;
    Address author;
    String timestap;
     
    /*
     * A project will be placed in a WorkflowInceptionBlock(WIB)
     * A project must contain:
     * Unique Workflow ID (UWID) Defined as SHA-256 Hash of Hypothesis
     * Authors, defined is an array for a MAX of 10 authors that contains every
     * authorized user to contribute to additions in the workflow (may not be
     * necessary for our implementation)
     * Hypothesis: A string of authors descretion that defines what the point of the
     * project is and what the tasks will ential.
     * 
     * 
     * 
     */

    public Project(String hypothesis, String workflowID, Address author) {
        super(RecordType.Project, workflowID);
        this.hypothesis = hypothesis;
        this.author = author;
        this.uniqueWorkflowID = Hashing.getSHAString(hypothesis);
    }

    public Address getAuthor() {
        return author;
    }

    public void setAuthor(Address author) {
        this.author = author;
    }

  
}
