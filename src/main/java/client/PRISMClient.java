package client;
import java.util.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PrivateKey;
import node.communication.Address;
import node.communication.messaging.Message;
import node.communication.messaging.Messager;
import node.communication.utils.DSA;
import node.blockchain.PRISM.PRISMTransaction;
import node.blockchain.PRISM.RecordTypes.ProvenanceRecord;
import node.blockchain.defi.Account;
import node.blockchain.defi.DefiTransaction;
import node.blockchain.merkletree.MerkleTreeProof;

public class PRISMClient {
    
    
    BufferedReader reader; // To read user input
    Address myAddress;
    ArrayList<Address> fullNodes; // List of full nodes we want to use
    Object updateLock; // Lock for multithreading
    boolean test; // Boolean for test vs normal output


    public PRISMClient(BufferedReader reader, Address myAddress, ArrayList<Address> fullNodes, Object updateLock) {
        this.reader = reader;
        this.myAddress = myAddress;
        this.fullNodes = fullNodes;
        this.updateLock = updateLock;
    }
    
    protected void submitProvenanceRecord() throws IOException{
        alertFullNode();
        System.out.println("Generating Provenance Record");
        System.out.println("WorkflowID:");
        String workflowID = reader.readLine();
        System.out.println("inputData:");
        String inputData = reader.readLine(); 
        System.out.println("task:");
        String task = reader.readLine();
        
        submitProvenanceTransaction(new PRISMTransaction(new ProvenanceRecord(inputData, task, workflowID), String.valueOf(System.currentTimeMillis()) ), fullNodes.get(0));
        System.out.println("PRISM Transaction Provenance Record Submitted");
    }   


    protected void alertFullNode() throws IOException{
        synchronized(updateLock){
            Messager.sendOneWayMessage(new Address(fullNodes.get(0).getPort(), fullNodes.get(0).getHost()),
            new Message(Message.Request.ALERT_WALLET, myAddress), myAddress);
            System.out.println("PrismCleitn alerted full node");

        }
    }


    protected void submitProvenanceTransaction(PRISMTransaction tx, Address address){

        try {
            Socket s = new Socket(address.getHost(), address.getPort());
            OutputStream out = s.getOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            Message message = new Message(Message.Request.ADD_TRANSACTION, tx);
            oout.writeObject(message);
            oout.flush();
            Thread.sleep(1000);
            s.close();
        } catch (IOException e) {
            System.out.println("Full node at " + address + " appears down.");
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
