package node;

import node.blockchain.*;
import node.blockchain.PRISM.EnumSubWorkflow;
import node.blockchain.PRISM.MinerData;
import node.blockchain.PRISM.NodeStats;
import node.blockchain.PRISM.PRISMTransaction;
import node.blockchain.PRISM.PRISMTransactionValidator;
import node.blockchain.PRISM.RepData;
import node.blockchain.PRISM.SubWorkflow;
import node.blockchain.PRISM.PRISMBlock;
import node.blockchain.PRISM.RecordTypes.ProvenanceRecord;
import node.blockchain.healthcare.*;
import node.blockchain.defi.DefiBlock;
import node.blockchain.defi.DefiTransaction;
import node.blockchain.defi.DefiTransactionValidator;
import node.blockchain.merkletree.MerkleTree;
import node.communication.*;
import node.communication.messaging.Message;
import node.communication.messaging.Messager;
import node.communication.messaging.MessagerPack;
import node.communication.messaging.Message.Request;
import node.communication.utils.Hashing;
import node.communication.utils.Utils;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.*;

import static node.communication.utils.DSA.*;
import static node.communication.utils.Hashing.getBlockHash;
import static node.communication.utils.Hashing.getSHAString;
import static node.communication.utils.Utils.*;

/**
 * A Node represents a peer, a cooperating member within a network following
 * this Quorum-based blockchain protocol
 * as implemented here.
 *
 * This node participates in a distributed and decentralized network protocol,
 * and achieves this by using some of
 * the following architecture features:
 *
 * Quorum Consensus
 * DSA authentication
 * Blockchain using SHA-256
 * Multithreading
 * Servant Model
 * Stateful Model
 * TCP/IP communication
 *
 *
 * Beware, any methods below are a WIP
 */
public class Node {

    /**
     * Node constructor creates node and begins server socket to accept connections
     *
     * @param port               Port
     * @param maxPeers           Maximum amount of peer connections to maintain
     * @param initialConnections How many nodes we want to attempt to connect to on
     *                           start
     */
    public Node(String use, int port, int maxPeers, int initialConnections, int numNodes, int quorumSize,
            int minimumTransaction, int debugLevel) {

        /* Configurations */
        USE = use;
        MIN_CONNECTIONS = initialConnections;
        MAX_PEERS = maxPeers;
        NUM_NODES = numNodes;
        QUORUM_SIZE = quorumSize;
        DEBUG_LEVEL = debugLevel;
        MINIMUM_TRANSACTIONS = minimumTransaction;

        /* Locks for Multithreading */
        lock = new Object();
        quorumLock = new Object();
        quorumReadyVotesLock = new Object();
        memPoolRoundsLock = new Object();
        sigRoundsLock = new Object();
        accountsLock = new Object();
        memPoolLock = new Object();
        blockLock = new Object();
        answerHashLock = new Object();
        minerDataLock = new Object();

        /* Multithreaded Counters for Stateful Servant */
        memPoolRounds = 0;
        quorumReadyVotes = 0;
        state = 0;

        InetAddress ip;

        try {
            ip = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        String host = ip.getHostAddress();

        /* Other Data for Stateful Servant */
        myAddress = new Address(port, host);
        localPeers = new ArrayList<>();
        mempool = new HashMap<>();
        accountsToAlert = new HashMap<>();

        /* Public-Private (DSA) Keys */
        KeyPair keys = generateDSAKeyPair();
        privateKey = keys.getPrivate();
        writePubKeyToRegistry(myAddress, keys.getPublic());

        /* Begin Server Socket */
        try {
            ss = new ServerSocket(port);
            Acceptor acceptor = new Acceptor(this);
            acceptor.start();
            System.out.println("Node up and running on port " + port + " " + InetAddress.getLocalHost());
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /* A collection of getters */
    public int getMaxPeers() {
        return this.MAX_PEERS;
    }

    public int getMinConnections() {
        return this.MIN_CONNECTIONS;
    }

    public Address getAddress() {
        return this.myAddress;
    }

    public ArrayList<Address> getLocalPeers() {
        return this.localPeers;
    }

    public HashMap<String, Transaction> getMempool() {
        return this.mempool;
    }

    public LinkedList<Block> getBlockchain() {
        return blockchain;
    }

    /**
     * Initializes blockchain
     */
    public void initializeBlockchain() {
        blockchain = new LinkedList<Block>();
        minerData = new HashMap<Address, MinerData>();

        if (USE.equals("Defi")) {
            accounts = new HashMap<>();
            // DefiTransaction genesisTransaction = new DefiTransaction("Bob", "Alice", 100,
            // "0");
            // HashMap<String, Transaction> genesisTransactions = new HashMap<String,
            // Transaction>();
            // String hashOfTransaction = "";
            // hashOfTransaction = getSHAString(genesisTransaction.toString());
            // genesisTransacUSEtions.put(hashOfTransaction, genesisTransaction);
            addBlock(new DefiBlock(new HashMap<String, Transaction>(), "000000", 0));
        } else if (USE.equals("HC")) {
            addBlock(new HCBlock(new HashMap<String, Transaction>(), "000000", 0));
        } else if (USE.equals("PRISM")) {

            for (Address address : globalPeers) {
                repData.put(address, new RepData());

            }
            if (DEBUG_LEVEL == 1) {
                for (Address address : repData.keySet()) {
                    System.out.println(repData.get(address).toString());

                }
            }
            addBlock(new PRISMBlock(new HashMap<String, Transaction>(), "000000", 0, minerData)); // Creating a blank
            for (Address address : globalPeers) {
                nodeStatsMap.put(address, new NodeStats());
            }

            // genesis block
        }
    }

    /**
     * Determines if a connection is eligible
     * 
     * @param address           Address to verify
     * @param connectIfEligible Connect to address if it is eligible
     * @return True if eligible, otherwise false
     */
    public boolean eligibleConnection(Address address, boolean connectIfEligible) {
        synchronized (lock) {
            if (localPeers.size() < MAX_PEERS - 1
                    && (!address.equals(this.getAddress()) && !containsAddress(localPeers, address))) {
                if (connectIfEligible) {
                    establishConnection(address);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Add a connection to our dynamic list of peers to speak with
     * 
     * @param address
     */
    public void establishConnection(Address address) {
        synchronized (lock) {
            if (!containsAddress(localPeers, address))
                localPeers.add(address);
        }
    }

    /**
     * Iterate through a list of peers and attempt to establish a mutual connection
     * with a specified amount of nodes
     * 
     * @param globalPeers
     */
    public void requestConnections(ArrayList<Address> globalPeers) {
        try {
            this.globalPeers = globalPeers;

            if (globalPeers.size() > 0) {
                /* Begin seeking connections */
                ClientConnection connect = new ClientConnection(this, globalPeers);
                connect.start();

                /* Begin heartbeat monitor */
                Thread.sleep(10000);
                HeartBeatMonitor heartBeatMonitor = new HeartBeatMonitor(this);
                heartBeatMonitor.start();

                /* Begin protocol */
                initializeBlockchain();
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Address removeAddress(Address address) {
        synchronized (lock) {
            for (Address existingAddress : localPeers) {
                if (existingAddress.equals(address)) {
                    localPeers.remove(address);
                    return address;
                }
            }
            return null;
        }
    }

    public void gossipTransaction(Transaction transaction) {
        synchronized (lock) {
            for (Address address : localPeers) {
                Messager.sendOneWayMessage(address, new Message(Message.Request.ADD_TRANSACTION, transaction),
                        myAddress);
            }
        }
    }

    public void addTransaction(Transaction transaction) {
        while (state != 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        verifyTransaction(transaction);
    }

    public void verifyTransaction(Transaction transaction) {
        synchronized (memPoolLock) {
            if (Utils.containsTransactionInMap(transaction, mempool)) {
                // System.out.println("Node " + myAddress.getPort() + ": verifyTransaction says
                // we seen this one: " + transaction.getUID() + ", blockchain size: " +
                // blockchain.size());
                return;
            }

            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": verifyTransaction: " + transaction.getUID()
                        + ", blockchain size: " + blockchain.size());
            }
            LinkedList<Block> clonedBlockchain = new LinkedList<>();

            clonedBlockchain.addAll(blockchain);
            for (Block block : clonedBlockchain) {
                System.out.println("Node " + myAddress.getPort() + ": verifyTransaction: " + transaction.getUID());
                if (block.getTxList().containsKey(getSHAString(transaction.getUID()))) {
                    // We have this transaction in a block
                    if (DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": trans :" + transaction.getUID()
                                + " found in prev block " + block.getBlockId());
                    }
                    return;
                }
            }

            PRISMTransactionValidator tv = null;
            Object[] validatorObjects = new Object[3];

            if (USE.equals("Defi")) {
                // tv = new DefiTransactionValidator();

                validatorObjects[0] = transaction;
                validatorObjects[1] = accounts;
                validatorObjects[2] = mempool;

            } else if (USE.equals("PRISM")) {
                tv = new PRISMTransactionValidator();
                // PRISM Validator needs the RecordType, and the Transaction
                // From there we should make sure the authors have enough reputation to allow
                // the transaction to be added to the mempool
                validatorObjects[0] = transaction;

            } else {
                tv = new PRISMTransactionValidator(); // To be changed to another use case in the future
            }

            if (!tv.validate(validatorObjects, repData)) {
                // if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + "Transaction not valid");
                //
                return;
            }

            mempool.put(getSHAString(transaction.getUID()), transaction);
            gossipTransaction(transaction);

            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": Added transaction. MP:" + mempool.values());
            }
        }
    }

    // Reconcile blocks
    public void sendQuorumReady() { // Start of Quorum Consensus
        // state = 1;
        stateChangeRequest(1);
        quorumSigs = new ArrayList<>();
        Block currentBlock = blockchain.getLast();
        ArrayList<Address> quorum = deriveQuorum(currentBlock, 0);

        if (DEBUG_LEVEL == 1)
            System.out.println("Node " + myAddress.getPort() + " sent quorum is ready for q: " + quorum);

        for (Address quorumAddress : quorum) {
            if (!myAddress.equals(quorumAddress)) {
                try {
                    Thread.sleep(2000);
                    MessagerPack mp = Messager.sendInterestingMessage(quorumAddress,
                            new Message(Message.Request.QUORUM_READY), myAddress);
                    Message messageReceived = mp.getMessage();
                    Message reply = new Message(Message.Request.PING);

                    if (messageReceived.getRequest().name().equals("RECONCILE_BLOCK")) {
                        Object[] blockData = (Object[]) messageReceived.getMetadata();
                        int blockId = (Integer) blockData[0];
                        String blockHash = (String) blockData[1];

                        if (blockId == currentBlock.getBlockId()) {

                        } else if (blockId < currentBlock.getBlockId()) {
                            // tell them they are behind
                            reply = new Message(Message.Request.RECONCILE_BLOCK, currentBlock.getBlockId());
                            if (DEBUG_LEVEL == 1) {
                                System.out.println("Node " + myAddress.getPort() + ": sendQuorumReady RECONCILE");
                            }
                        } else if (blockId > currentBlock.getBlockId()) {
                            // we are behind, quorum already happened / failed
                            reply = new Message(Message.Request.PING);
                            // blockCatchUp();

                        }
                        mp.getOout().writeObject(reply);
                        mp.getOout().flush();
                    }

                    mp.getSocket().close();
                } catch (IOException e) {
                    System.out.println("Node " + myAddress.getPort()
                            + ": sendQuorumReady Received IO Exception from node " + quorumAddress.getPort());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // Reconcile blocks
    public void receiveQuorumReady(ObjectOutputStream oout, ObjectInputStream oin) {
        synchronized (quorumReadyVotesLock) {
            while (state != 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Block currentBlock = blockchain.getLast();
            ArrayList<Address> quorum = deriveQuorum(currentBlock, 0);

            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": receiveQuorumReady invoked for " + quorum);

            try {

                if (!inQuorum()) {
                    if (DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": not in quorum? q: " + quorum
                                + " my addr: " + myAddress);
                    }
                    oout.writeObject(new Message(Message.Request.RECONCILE_BLOCK,
                            new Object[] { currentBlock.getBlockId(), getBlockHash(currentBlock, 0) }));
                    oout.flush();
                    Message reply = (Message) oin.readObject();

                    if (reply.getRequest().name().equals("RECONCILE_BLOCK")) {
                        // blockCatchUp();
                    }
                } else {
                    oout.writeObject(new Message(Message.Request.PING));
                    oout.flush();
                    quorumReadyVotes++;
                    // System.out.println("Node " + myAddress.getPort() + ": has " +
                    // quorumReadyVotes + " votes. Needs: "
                    // + (quorum.size() - 1));
                    if (quorumReadyVotes == quorum.size() - 1) {
                        quorumReadyVotes = 0;
                        delegateWork();
                    }

                }
            } catch (IOException e) {
                System.out.println("Node " + myAddress.getPort() + ": receiveQuorumReady EOF");
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void delegateWork() {
        Random rand = new Random();
        int myPick = rand.nextInt(10);
        SubWorkflow myWorkflow;
        if (myPick == 0) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_0.getSubWorkflow(); 
        } else if (myPick == 1) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_1.getSubWorkflow(); 
        } else if (myPick == 2) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_2.getSubWorkflow(); 
        } else if (myPick == 3) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_3.getSubWorkflow(); 
        } else if (myPick == 4) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_4.getSubWorkflow(); 
        } else if (myPick == 5) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_5.getSubWorkflow(); 
        } else if (myPick == 6) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_6.getSubWorkflow(); 
        } else if (myPick == 7) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_7.getSubWorkflow(); 
        } else if (myPick == 8) {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_8.getSubWorkflow(); 
        } else {
            myWorkflow = EnumSubWorkflow.SUB_WORKFLOW_9.getSubWorkflow(); 
        } 
        int iterator = myWorkflow.getNumSteps() - 1;

        synchronized (lock) {

            // System.out.println("Node " + myAddress.getPort() + ": delegating work");
            Float[][][] minerOutput = new Float[myWorkflow.getNumSteps()][][]; // minerOutput contains all the hashes and their
                                                                    // counts.

            // System.out.println("I am quorum members delegating work");
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);
            // System.out.println("Node " + myAddress.getPort() + ": lp: " + localPeers);

            for (Address address : localPeers) {

                if (!containsAddress(quorum, address)) {
                    // minerData.put(address, new MinerData(address, 0, "0"));
                    // System.out.println("Node " + myAddress + ": minerData as dekegating work: " +
                    // minerData.keySet());
                    float startTime = (float) System.currentTimeMillis();

                    // if my neighbour is a quorum member, returndoWork
                    // System.out.println("send work to " + address.toString());
                    Message reply = Messager.sendTwoWayMessage(address, new Message(Request.DELEGATE_WORK, myWorkflow), myAddress);

                    if (reply.getRequest().name().equals("COMPLETED_WORK")) {
                        float endTime = (float) System.currentTimeMillis();

                        //Feeding all miners the whole workflow and truncating for the purpose of same hash
                        Float[][] output = (Float[][])reply.getMetadata();
                        Float[][] shortenedOutput = Arrays.copyOf(output, iterator); 
                        // float time = (endTime - startTime);
                        float time = ThreadLocalRandom.current().nextFloat() * 5.0f;

                        // If the generated time is 0, generate another one
                        while (time == 0) {
                            time = ThreadLocalRandom.current().nextFloat() * 5.0f;
                        }
                        // Looks like we gotta hard code time
                        String hash = Hashing.getSHAString(output[myWorkflow.getNumSteps() - 1].toString());
                        MinerData singleMinerData = new MinerData(address, time, hash);
                        // System.out.println(myAddress + " Single Miner Data : " + singleMinerData);
                        minerData.put(address, singleMinerData);
                        // System.out.println("Node " + myAddress.getPort() + ": delegated work to and
                        // put " + address);

                        minerOutput[iterator-1]=shortenedOutput;
                    }
                }
                if(myWorkflow.getNumSteps() == 1) {
                    break;
                }
                iterator--;
            }

            Float[] popularHash = minerOutput[myWorkflow.getNumSteps()-1][myWorkflow.getNumSteps()-1];

            // System.out.println("Node " + myAddress + ": minerData after delegating work:
            // " + minerData.keySet());
            String hash = Hashing.getSHAString(popularHash.toString());
            sendOneWayMessageQuorum(new Message(Request.RECEIVE_ANSWER_HASH, hash));
            stateChangeRequest(2);
        }
    }

    private ArrayList<String> quorumAnswerHashes = new ArrayList<String>();

    public void recieveAnswerHashLocking(String hash) {
        while (state != 2) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        recieveAnswerHash(hash);
    }

    public void recieveAnswerHash(String hash) {
        synchronized (answerHashLock) {
            quorumAnswerHashes.add(hash);

            if (quorumAnswerHashes.size() == deriveQuorum(blockchain.getLast(), 0).size() - 1) {
                /* See which is most voted between Q member */
                HashMap<String, Integer> hashVotes = new HashMap<>();

                for (String hashAnswer : quorumAnswerHashes) {
                    if (hashVotes.get(hashAnswer) == null) {
                        hashVotes.put(hashAnswer, 1);
                    } else {
                        hashVotes.put(hashAnswer, hashVotes.get(hashAnswer) + 1);
                    }
                }
                
                String popularHash = "";

                for (String hashAnswer : hashVotes.keySet()) {
                    if (hashVotes.get(popularHash) == null)
                        popularHash = hashAnswer;

                    if (hashVotes.get(hashAnswer) > hashVotes.get(popularHash)) {
                        popularHash = hashAnswer;
                    }
                }
                HashSet<String> keys = new HashSet<String>(mempool.keySet());
                PRISMTransaction prismTransaction = null;
                for (String key : keys) {
                    prismTransaction = (PRISMTransaction) mempool.get(key); // ASSUMING 1 TX
                }

                ProvenanceRecord pr = (ProvenanceRecord) prismTransaction.getRecord();
                pr.setAnswerHash(popularHash);
                // System.out.println("Node " + myAddress.getPort() + ": about to send
                // mempool");
                quorumAnswerHashes = new ArrayList<>();

                // sendMempoolHashes();
                // System.out.println("sending miner datas");
                // System.out.println("Node " + myAddress + ": minerData after getting answers:
                // " + minerData.keySet());

                sendOneWayMessageQuorum(new Message(Message.Request.RECEIVE_MINER_DATA, minerData));
                stateChangeRequest(3);
            }
        }
    }

    int i = 0;

    HashMap<Address, MinerData> minerData; // all Miner datas fro all quorum members

    public void receiveMinerDataLocking(HashMap<Address, MinerData> otherMinerData) {
        while (state != 3) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        receiveMinerData(otherMinerData);
    }

    public void receiveMinerData(HashMap<Address, MinerData> otherMinerData) {
        synchronized (minerDataLock) {
            // System.out.println("Node " + myAddress.getPort() + ": oth: " +
            // otherMinerData.keySet());
            i++;

            for (Address address : otherMinerData.keySet()) {

                ArrayList<Address> ourAddresses = new ArrayList<Address>(minerData.keySet());

                if (containsAddress(ourAddresses, address)) { // I have work from the same miner- lets pick one

                    Address ourFoundAddress = null;
                    for (Address ourAddress : ourAddresses) {
                        if ((ourAddress).equals(address)) {
                            ourFoundAddress = ourAddress;
                        }
                    }

                    if (ourFoundAddress == null)
                        System.out.println("Node " + myAddress.getPort() + ": Our address is null");

                    if (!minerData.get(ourFoundAddress).equals(otherMinerData.get(address))) { // we DONT have the same

                        if (!minerData.get(ourFoundAddress).getOutputHash()
                                .equals(otherMinerData.get(address).getOutputHash())) {

                            if (minerData.get(ourFoundAddress).toString()
                                    .compareTo(otherMinerData.get(address).toString()) < 0) {
                                minerData.put(ourFoundAddress, otherMinerData.get(address));
                            }

                            // This miner gave two different, should we just disregard?
                        } else if (minerData.get(ourFoundAddress).getTimestamp() > otherMinerData.get(address)
                                .getTimestamp()) {

                            minerData.put(ourFoundAddress, otherMinerData.get(address));
                        }

                    }
                } else {
                    // I don have work from the same miner- lets add it
                    minerData.put(address, otherMinerData.get(address));

                }

            }
            // System.out.println(myAddress + ": MY miner data after aggregation" +
            // minerData.values());

            if (i == deriveQuorum(blockchain.getLast(), 0).size() - 1) {
                sendMempoolHashes();
                i = 0;
            }
        }
    }

    // System.out.println("sending mempool hashes ");

    public void doWork(SubWorkflow workflow, ObjectInputStream oin, ObjectOutputStream oout) {
        float[][] output = workflow.compute(workflow.getNumSteps(), workflow.getFirstInput(), (float)accuracyPercent);

        try {
            oout.writeObject(new Message(Request.COMPLETED_WORK, output));
            oout.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMempoolHashes() {
        synchronized (memPoolLock) {

            stateChangeRequest(4);

            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes invoked");

            HashSet<String> keys = new HashSet<String>(mempool.keySet());
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes sending to " + quorum);

            for (Address quorumAddress : quorum) {
                if (!myAddress.equals(quorumAddress)) {
                    try {
                        MessagerPack mp = Messager.sendInterestingMessage(quorumAddress,
                                new Message(Message.Request.RECEIVE_MEMPOOL, keys), myAddress);
                        ;
                        Message messageReceived = mp.getMessage();
                        if (messageReceived.getRequest().name().equals("REQUEST_TRANSACTION")) {
                            ArrayList<String> hashesRequested = (ArrayList<String>) messageReceived.getMetadata();
                            if (DEBUG_LEVEL == 1)
                                System.out.println("Node " + myAddress.getPort()
                                        + ": sendMempoolHashes: requested trans: " + hashesRequested);

                            ArrayList<Transaction> transactionsToSend = new ArrayList<>();
                            for (String hash : keys) {
                                if (mempool.containsKey(hash)) {
                                    transactionsToSend.add(mempool.get(hash));
                                } else {
                                    if (DEBUG_LEVEL == 1)
                                        System.out.println("Node " + myAddress.getPort()
                                                + ": sendMempoolHashes: requested trans not in mempool. MP: "
                                                + mempool);
                                }
                            }
                            mp.getOout().writeObject(new Message(Message.Request.RECEIVE_MEMPOOL, transactionsToSend));
                            if (DEBUG_LEVEL == 1)
                                System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes sent");
                        }
                        mp.getSocket().close();
                    } catch (IOException e) {
                        System.out.println(e);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }
            }
        }
    }

    public void receiveMempool(Set<String> keys, ObjectOutputStream oout, ObjectInputStream oin) {
        // System.out.println("Node " + myAddress.getPort() + ": Waiting for state 4.
        // State: " + state);
        while (state != 4) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        resolveMempool(keys, oout, oin);
    }

    public void resolveMempool(Set<String> keys, ObjectOutputStream oout, ObjectInputStream oin) {
        synchronized (memPoolRoundsLock) {
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

            if (DEBUG_LEVEL == 1)
                System.out.println(
                        "Node " + myAddress.getPort() + ": receiveMempool invoked. Looking for " + (quorum.size() - 1));
            ArrayList<String> keysAbsent = new ArrayList<>();
            for (String key : keys) {
                if (!mempool.containsKey(key)) {
                    keysAbsent.add(key);
                }
            }
            try {
                if (keysAbsent.isEmpty()) {
                    oout.writeObject(new Message(Message.Request.PING));
                    oout.flush();
                } else {
                    if (DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort()
                                + ": receiveMempool requesting transactions for: " + keysAbsent);
                    }
                    oout.writeObject(new Message(Message.Request.REQUEST_TRANSACTION, keysAbsent));
                    oout.flush();
                    Message message = (Message) oin.readObject();
                    ArrayList<Transaction> transactionsReturned = (ArrayList<Transaction>) message.getMetadata();

                    for (Transaction transaction : transactionsReturned) {
                        mempool.put(getSHAString(transaction.getUID()), transaction);
                        if (DEBUG_LEVEL == 1)
                            System.out
                                    .println("Node " + myAddress.getPort() + ": recieved transactions: " + keysAbsent);
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }

            memPoolRounds++;
            if (DEBUG_LEVEL == 1)
                System.out.println(
                        "Node " + myAddress.getPort() + ": receiveMempool invoked: mempoolRounds: " + memPoolRounds);
            if (memPoolRounds == quorum.size() - 1) {
                memPoolRounds = 0;
                // System.out.println("Node " + myAddress.getPort() + ": about to construct
                // Block");
                constructBlock();
            }
        }
    }

    public void constructBlock() {
        // System.out.println("Node " + myAddress.getPort() + ": constructBlock
        // locking");

        synchronized (memPoolLock) {
            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": constructBlock invoked");
            stateChangeRequest(5);

            /* Make sure compiled transactions don't conflict */
            HashMap<String, Transaction> blockTransactions = new HashMap<>();

            for (String key : mempool.keySet()) {
                Transaction transaction = mempool.get(key);
                Object[] validatorObjects = new Object[3];
                if (USE.equals("Defi")) {
                    validatorObjects[0] = transaction;
                    validatorObjects[1] = accounts;
                    validatorObjects[2] = blockTransactions;
                } else if (USE.equals("HC")) {
                    // Validator objects will change according to another use case
                } else if (USE.equals("PRISM")) {
                    validatorObjects[0] = transaction;
                }
                txValidator.validate(validatorObjects, repData);
                blockTransactions.put(key, transaction);
            }

            try {
                if (USE.equals("PRISM")) {
                    // How to do for multiple block types?

                    quorumBlock = new PRISMBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size(), minerData);

                }

            } catch (NoSuchAlgorithmException e) {

                throw new RuntimeException(e);
            }

            sendSigOfBlockHash();
        }
    }

    public void sendSigOfBlockHash() {
        String blockHash;
        byte[] sig;

        try {
            blockHash = getBlockHash(quorumBlock, 0);
            sig = signHash(blockHash, privateKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        BlockSignature blockSignature = new BlockSignature(sig, blockHash, myAddress);
        sendOneWayMessageQuorum(new Message(Message.Request.RECEIVE_SIGNATURE, blockSignature));

        if (DEBUG_LEVEL == 1) {
            System.out.println("Node " + myAddress.getPort() + ": sendSigOfBlockHash invoked for hash: "
                    + blockHash.substring(0, 4));
        }
    }

    public void receiveQuorumSignature(BlockSignature signature) {
        synchronized (sigRoundsLock) {
            if (DEBUG_LEVEL == 1) {
                System.out.println(
                        "Node " + myAddress.getPort() + ": 1st part receiveQuorumSignature invoked. state: " + state);
            }

            while (state != 5) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

            if (!containsAddress(quorum, signature.getAddress())) {
                if (DEBUG_LEVEL == 1)
                    System.out.println("Node " + myAddress.getPort() + ": false sig from " + signature.getAddress());
                return;
            }

            if (!inQuorum()) {
                if (DEBUG_LEVEL == 1)
                    System.out.println(
                            "Node " + myAddress.getPort() + ": not in quorum? q: " + quorum + " my addr: " + myAddress);
                return;
            }

            quorumSigs.add(signature);
            int blockId = blockchain.size() - 1;

            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": receiveQuorumSignature invoked from " +
                        signature.getAddress().toString() + " qSigs: " + quorumSigs + " quorum: " + quorum + " block "
                        + quorumBlock.getBlockId());
            }

            if (quorumSigs.size() == quorum.size() - 1) {
                if (!inQuorum()) {
                    if (DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": not in quorum? q: " + quorum
                                + " my addr: " + myAddress);
                    }
                    System.out.println("Node " + myAddress.getPort() + ": rQs: not in quorum? q: " + quorum
                            + " my addr: " + myAddress + " block: " + blockId);
                    return;
                }
                tallyQuorumSigs();
            }
        }
    }

    public void tallyQuorumSigs() { // End of Quorum Consensus
        synchronized (blockLock) {
            resetMempool();

            if (DEBUG_LEVEL == 1) {
                // System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs
                // invoked");
            }

            // state = 4;
            stateChangeRequest(6);
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

            if (!inQuorum()) {
                System.out.println("Node " + myAddress.getPort() + ": tQs: not in quorum? q: " + quorum + " my addr: "
                        + myAddress);
                return;
            }

            HashMap<String, Integer> hashVotes = new HashMap<>();
            String quorumBlockHash;
            int block = blockchain.size() - 1;
            try {
                if (quorumBlock == null) {
                    System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs quorum null");
                }

                quorumBlockHash = getBlockHash(quorumBlock, 0);
                hashVotes.put(quorumBlockHash, 1);
                ;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            for (BlockSignature sig : quorumSigs) {
                if (verifySignatureFromRegistry(sig.getHash(), sig.getSignature(), sig.getAddress())) {
                    if (hashVotes.containsKey(sig.getHash())) {
                        int votes = hashVotes.get(sig.getHash());
                        votes++;
                        hashVotes.put(sig.getHash(), votes);
                    } else {
                        hashVotes.put(sig.getHash(), 0);
                    }
                } else {
                    /* Signature has failed. Authenticity or integrity compromised */
                }

            }

            String winningHash = quorumSigs.get(0).getHash();

            for (BlockSignature blockSignature : quorumSigs) {
                String hash = blockSignature.getHash();
                if (hashVotes.get(hash) != null && (hashVotes.get(hash) > hashVotes.get(winningHash))) {
                    winningHash = hash;
                }
            }
            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs: Winning hash votes = "
                        + hashVotes.get(winningHash));
            }
            if (hashVotes.get(winningHash) == quorum.size()) { // This needs to be changed from quorum size to most
                                                               // popular quorum vote
                if (quorumBlockHash.equals(winningHash)) {

                    sendSkeleton();
                    // System.out.println("Quorum" + myAddress + minerData.values());
                    quorumBlock.setMinerData(minerData);
                    addBlock(quorumBlock); // HERE WHERE WE ADD QUORUM BLOCKS

                    if (quorumBlock == null) {
                        System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs quorum null");

                    }
                } else {
                    System.out.println("Node " + myAddress.getPort()
                            + ": tallyQuorumSigs: quorumBlockHash does not equals(winningHash)");
                }
            } else {
                System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs: failed vote. votes: " + hashVotes
                        + " my block " + quorumBlock.getBlockId() + " " + quorumBlockHash.substring(0, 4) +
                        " quorumSigs: " + quorumSigs);
            }
            hashVotes.clear();
            quorumSigs.clear();
        }
    }

    private void resetMempool() {
        synchronized (memPoolLock) {
            mempool = new HashMap<>();
        }
    }

    public void sendSkeleton() {
        synchronized (lock) {
            // state = 0;

            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": sendSkeleton invoked. qSigs " + quorumSigs);
            }
            BlockSkeleton skeleton = null;
            try {
                if (quorumBlock == null) {
                    System.out.println("Node " + myAddress.getPort() + ": sendSkeleton quorum null");

                }
                skeleton = new BlockSkeleton(quorumBlock.getBlockId(),
                        new ArrayList<String>(quorumBlock.getTxList().keySet()), quorumSigs,
                        getBlockHash(quorumBlock, 0), minerData); // Add miner data

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            for (Address address : localPeers) {
                Messager.sendOneWayMessage(address, new Message(Message.Request.RECEIVE_SKELETON, skeleton), myAddress);
            }

        }
    }

    public void sendSkeleton(BlockSkeleton skeleton) {
        synchronized (lock) {
            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": sendSkeleton(local) invoked: BlockID "
                        + skeleton.getBlockId());
            }
            for (Address address : localPeers) {
                if (!address.equals(myAddress)) {
                    Messager.sendOneWayMessage(address, new Message(Message.Request.RECEIVE_SKELETON, skeleton),
                            myAddress);
                }
            }
        }
    }

    public void receiveSkeleton(BlockSkeleton blockSkeleton) {
        while (state != 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        validateSkeleton(blockSkeleton);
    }

    public void validateSkeleton(BlockSkeleton blockSkeleton) {
        synchronized (blockLock) {
            Block currentBlock = blockchain.getLast();

            if (currentBlock.getBlockId() + 1 != blockSkeleton.getBlockId()) {
                // if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ":
                // receiveSkeleton(local) currentblock not synced with skeleton. current id: " +
                // currentBlock.getBlockId() + " new: " + blockSkeleton.getBlockId()); }
                return;
            } else {
                if (DEBUG_LEVEL == 1) {
                    System.out.println("Node " + myAddress.getPort() + ": receiveSkeleton(local) invoked. Hash: "
                            + blockSkeleton.getHash());
                }
            }

            ArrayList<Address> quorum = deriveQuorum(currentBlock, 0);
            int verifiedSignatures = 0;
            String hash = blockSkeleton.getHash();

            if (blockSkeleton.getSignatures().size() < 1) {
                if (DEBUG_LEVEL == 1) {
                    System.out.println("Node " + myAddress.getPort() + ": No signatures. blockskeletonID: "
                            + blockSkeleton.getBlockId() + ". CurrentBlockID: " + currentBlock.getBlockId()
                            + " quorum: " + quorum);
                }
            }

            for (BlockSignature blockSignature : blockSkeleton.getSignatures()) {
                Address address = blockSignature.getAddress();
                if (containsAddress(quorum, address)) {
                    if (verifySignatureFromRegistry(hash, blockSignature.getSignature(), address)) {
                        verifiedSignatures++;
                    } else {
                        if (DEBUG_LEVEL == 1) {
                            System.out.println("Node " + myAddress.getPort()
                                    + ": Failed to validate signature. blockskeletonID: " + blockSkeleton.getBlockId()
                                    + ". CurrentBlockID: " + currentBlock.getBlockId());
                        }
                        ;
                    }
                } else {
                    if (DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": blockskeletonID: "
                                + blockSkeleton.getBlockId() + ". CurrentBlockID: " + currentBlock.getBlockId()
                                + " quorum: " + quorum + ". Address: " + address);
                    }
                }
            }

            if (verifiedSignatures != quorum.size() - 1) {
                if (DEBUG_LEVEL == 1) {
                    System.out.println("Node " + myAddress.getPort() + ": sigs not verified for block "
                            + blockSkeleton.getBlockId() +
                            ". Verified sigs: " + verifiedSignatures + ". Needed: " + quorum.size() + " - 1.");
                }
                return;
            }

            Block newBlock = constructBlockWithSkeleton(blockSkeleton);
            PRISMBlock pBlock = (PRISMBlock) newBlock;
            pBlock.setMinerData(blockSkeleton.getMinerData());
            // System.out.println("Validate skeleton: " + pBlock.getMinerData().keySet());
            addBlock(pBlock);
            sendSkeleton(blockSkeleton);
            // minerData.clear();

        }
    }

    public Block constructBlockWithSkeleton(BlockSkeleton skeleton) {
        synchronized (memPoolLock) {
            if (DEBUG_LEVEL == 1) {
                // System.out.println("Node " + myAddress.getPort() + ":
                // constructBlockWithSkeleton(local) invoked");
            }
            ArrayList<String> keys = skeleton.getKeys();
            HashMap<String, Transaction> blockTransactions = new HashMap<>();
            for (String key : keys) {
                if (mempool.containsKey(key)) {
                    blockTransactions.put(key, mempool.get(key));
                    mempool.remove(key);
                } else {
                    // need to ask for trans
                }
            }

            Block newBlock;

            if (USE.equals("Defi")) {
                try {
                    newBlock = new DefiBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            } else if (USE.equals("HC")) {
                try {
                    newBlock = new HCBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            } else if (USE.equals("PRISM")) {
                try {

                    newBlock = new PRISMBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size(), skeleton.getMinerData()); // Miner data comes from skelton not global
                    // (skeleton.getMinderDatas())
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            } else {
                newBlock = null;
            }
            return newBlock;
        }
    }

    private Object stateLock = new Object();

    private void stateChangeRequest(int statetoChange) {
        synchronized (stateLock) {
            state = statetoChange;
            // System.out.println("Node " + myAddress.getPort() + ": Changed state to " +
            // state);
        }
    }

    HashMap<Address, NodeStats> nodeStatsMap = new HashMap<>();

    /**
     * Adds a block
     * 
     * @param block Block to add
     */
    public void addBlock(Block block) {
        stateChangeRequest(0);
        // state = 0;

        HashMap<String, Transaction> txMap = block.getTxList();
        HashSet<String> keys = new HashSet<>(txMap.keySet());
        ArrayList<Transaction> txList = new ArrayList<>();
        for (String hash : txMap.keySet()) {
            txList.add(txMap.get(hash));
        }

        MerkleTree mt = new MerkleTree(txList);
        if (mt.getRootNode() != null)
            block.setMerkleRootHash(mt.getRootNode().getHash());
        PRISMBlock pBlock = (PRISMBlock) block;
        blockchain.add(pBlock);

        // Maybe we need to set the miner data here instead?

        //// PRISM setting miner data of added block
        // System.out.println("Node " + myAddress.getPort() + ": " +
        //// chainString(blockchain) + " MP: " + mempool.values()
        // + " myMinerData: ");
        // pBlock.getMinerData().values()
        for (Address address : pBlock.getMinerData().keySet()) {
            MinerData printingMData = pBlock.getMinerData().get(address);
            // System.out.println("Miner: " + address + "- Time: " +
            // printingMData.getTimestamp() + " Output: "
            // + printingMData.getOutputHash());

        }

        if (USE.equals("Defi")) {
            HashMap<String, DefiTransaction> defiTxMap = new HashMap<>();

            for (String key : keys) {
                DefiTransaction transactionInList = (DefiTransaction) txMap.get(key);
                defiTxMap.put(key, transactionInList);
            }

            // DefiTransactionValidator.updateAccounts(defiTxMap, accounts);

            synchronized (accountsLock) {
                for (String account : accountsToAlert.keySet()) {
                    // System.out.println(account);
                    for (String transHash : txMap.keySet()) {
                        DefiTransaction dtx = (DefiTransaction) txMap.get(transHash);
                        // System.out.println(dtx.getFrom() + "---" + dtx.getTo());
                        if (dtx.getFrom().equals(account) ||
                                dtx.getTo().equals(account)) {
                            Messager.sendOneWayMessage(accountsToAlert.get(account),
                                    new Message(Message.Request.ALERT_WALLET, mt.getProof(txMap.get(transHash))),
                                    myAddress);
                            // System.out.println("sent update");
                        }
                    }
                }
            }
        } else {
            // PRISM

            txValidator.calculateReputationsData(pBlock, repData);

            // System.out.println(myAddress + " | " + repData.toString());
            // if (myAddress.getPort() == 8000) {
            // repData.entrySet().stream()
            // .sorted(Map.Entry.<Address, RepData>comparingByValue(
            // Comparator.comparingDouble(RepData::getCurrentReputation)).reversed())
            // .forEach(entry -> System.out
            // .println(entry.getKey().getPort() + "|" + entry.getValue().toString()));

            // }
            // Only perform these operations if myAddress.getPort() == 8000
            if (myAddress.getPort() == 8000) {

                System.out.println(myAddress.getPort() + "im 8000");
                File file = new File("quorumLocalFairness.csv");
                if (file.length() == 0) {
                    System.out.println("File length before writing: " + file.length());
                    try (FileWriter fw = new FileWriter("quorumLocalFairness.csv", false)) {
                        PrintWriter pw = new PrintWriter(fw);
                        pw.println("Node ID, EligibleCount, Times Selected");
                        pw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("File length after writing: " + file.length());
                } else {
                    // If file is not empty, append the new data
                    dataCollectionDeriveQuorum(pBlock, 0);
                    System.out.println("Getting data");
                    try (FileWriter fw = new FileWriter("quorumLocalFairness.csv", false)) {
                        PrintWriter pw = new PrintWriter(fw);
                        // Loop through the entries in nodeStatsMap
                        for (Map.Entry<Address, NodeStats> entry : nodeStatsMap.entrySet()) {
                            Address nodeAddress = entry.getKey();
                            NodeStats stats = entry.getValue();
                            System.out.println(stats);
                            // Write the node's stats to the file
                            pw.println(nodeAddress + "," + stats.getEligibleCount() + ","
                                    + stats.getQuorumCount());
                        }
                        pw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

        if (DEBUG_LEVEL == 0) {
            System.out.println(
                    "Node " + myAddress.getPort() + ": Added block " + block.getBlockId() + ". Next quorum: " + quorum);
        }

        if (inQuorum()) {
            while (mempool.size() < MINIMUM_TRANSACTIONS) { // PRISM, I think we only want one transaction per block.
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            sendQuorumReady();
        }
    }

    public void sendOneWayMessageQuorum(Message message) {
        ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);
        for (Address quorumAddress : quorum) {
            if (!myAddress.equals(quorumAddress)) {
                Messager.sendOneWayMessage(quorumAddress, message, myAddress);
            }
        }
    }

    public boolean inQuorum() {
        synchronized (quorumLock) {
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);
            Boolean quorumMember = false;
            for (Address quorumAddress : quorum) {
                if (myAddress.equals(quorumAddress)) {
                    quorumMember = true;
                }
            }
            return quorumMember;
        }
    }

    public boolean inQuorum(Block block) {
        synchronized (quorumLock) {
            if (block.getBlockId() - 1 != blockchain.getLast().getBlockId()) { //
                return false;
            }
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);
            Boolean quorumMember = false;
            for (Address quorumAddress : quorum) {
                if (myAddress.equals(quorumAddress)) {
                    quorumMember = true;
                }
            }
            return quorumMember;

        }
    }

    public ArrayList<Address> deriveQuorum(Block block, int nonce) {
        // System.out.println("Deriving quorum for block " + block.getBlockId());
        String blockHash;

        if (block != null) {
            try {
                ArrayList<Address> quorum = new ArrayList<>();
                blockHash = Hashing.getBlockHash(block, nonce);
                BigInteger bigInt = new BigInteger(blockHash, 16);
                bigInt = bigInt.mod(BigInteger.valueOf(globalPeers.size()));
                int seed = bigInt.intValue();
                Random random = new Random(seed);

                // Print repData for debugging

                // Sort the repData map by currentReputation values in descending order
                LinkedHashMap<Address, RepData> sortedMap = globalPeers.stream()
                        .sorted(Comparator
                                .comparing(a -> repData.containsKey(a) ? repData.get(a).getCurrentReputation() : 0)
                                .reversed())
                        .collect(Collectors.toMap(
                                Function.identity(),
                                a -> repData.containsKey(a) ? repData.get(a) : new RepData(),
                                (e1, e2) -> e1,
                                LinkedHashMap::new));

                // Calculate top 30% limit
                int eligibleSize = (int) Math.ceil(sortedMap.size() * 0.2);
                // Get the top 30% entries
                List<Map.Entry<Address, RepData>> eligibleNodes = sortedMap.entrySet()
                        .stream()
                        .limit(eligibleSize)
                        .collect(Collectors.toList());

                // Shuffle the list
                Collections.shuffle(eligibleNodes, random);

                // Calculate top 10% limit of eligible nodes
                int quorumSize = (int) Math.ceil(eligibleNodes.size() * 0.5);

                // Add these to the quorum
                for (int i = 0; i < quorumSize; i++) {
                    quorum.add(eligibleNodes.get(i).getKey());
                }
                // Only perform these operations if myAddress.getPort() == 8000

                // System.out.println("I'm node " + myAddress + " and I think the quorum
                // consists of " + quorum.toString());

                return quorum;

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public void dataCollectionDeriveQuorum(Block block, int nonce) {
        // System.out.println("Deriving quorum for block " + block.getBlockId());
        String blockHash;

        if (block != null) {

            ArrayList<Address> quorum = new ArrayList<>();
            try {
                blockHash = Hashing.getBlockHash(block, nonce);

                BigInteger bigInt = new BigInteger(blockHash, 16);
                bigInt = bigInt.mod(BigInteger.valueOf(globalPeers.size()));
                int seed = bigInt.intValue();
                Random random = new Random(seed);

                // Print repData for debugging

                // Sort the repData map by currentReputation values in descending order
                LinkedHashMap<Address, RepData> sortedMap = globalPeers.stream()
                        .sorted(Comparator
                                .comparing(a -> repData.containsKey(a) ? repData.get(a).getCurrentReputation() : 0)
                                .reversed())
                        .collect(Collectors.toMap(
                                Function.identity(),
                                a -> repData.containsKey(a) ? repData.get(a) : new RepData(),
                                (e1, e2) -> e1,
                                LinkedHashMap::new));

                // Calculate top 30% limit
                int eligibleSize = (int) Math.ceil(sortedMap.size() * 0.2);
                // Get the top 30% entries
                List<Map.Entry<Address, RepData>> eligibleNodes = sortedMap.entrySet()
                        .stream()
                        .limit(eligibleSize)
                        .collect(Collectors.toList());

                // Loop through eligible nodes
                for (Map.Entry<Address, RepData> nodeEntry : eligibleNodes) {
                    Address nodeAddress = nodeEntry.getKey();

                    // If the node isn't already in the map, add it
                    if (!nodeStatsMap.containsKey(nodeAddress)) {
                        nodeStatsMap.put(nodeAddress, new NodeStats());
                    }

                    // Increment the eligible count for this node
                    nodeStatsMap.get(nodeAddress).incrementEligibleCount();
                }

                // Shuffle the list
                Collections.shuffle(eligibleNodes, random);

                // Calculate top 10% limit of eligible nodes
                int quorumSize = (int) Math.ceil(eligibleNodes.size() * 0.5);

                // Add these to the quorum
                for (int i = 0; i < quorumSize; i++) {
                    quorum.add(eligibleNodes.get(i).getKey());
                }
                // Only perform these operations if myAddress.getPort() == 8000

                // System.out.println("I'm node " + myAddress + " and I think the quorum
                // consists of " + quorum.toString());
                // Only perform these operations if myAddress.getPort() == 8000

                // Loop through nodes in quorum
                for (Address nodeAddress : quorum) {
                    // If the node isn't already in the map, add it
                    if (!nodeStatsMap.containsKey(nodeAddress)) {
                        nodeStatsMap.put(nodeAddress, new NodeStats());
                    }

                    // Increment the quorum count for this node
                    nodeStatsMap.get(nodeAddress).incrementQuorumCount();
                }
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private HashMap<String, Address> accountsToAlert;

    public void alertWallet(String accountPubKey, Address address) {
        synchronized (accountsLock) {
            accountsToAlert.put(accountPubKey, address);
        }
    }

    /**
     * Acceptor is a thread responsible for maintaining the server socket by
     * accepting incoming connection requests, and starting a new ServerConnection
     * thread for each request. Requests terminate in a finite amount of steps, so
     * threads return upon completion.
     */
    class Acceptor extends Thread {
        Node node;

        Acceptor(Node node) {
            this.node = node;
        }

        public void run() {
            Socket client;
            while (true) {
                try {
                    client = ss.accept();
                    new ServerConnection(client, node).start();
                } catch (IOException e) {
                    System.out.println(e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * HeartBeatMonitor is a thread which will periodically 'ping' nodes which this
     * node is connected to.
     * It expects a 'ping' back. Upon receiving the expected reply the other node is
     * deemed healthy.
     *
     */
    class HeartBeatMonitor extends Thread {
        Node node;

        HeartBeatMonitor(Node node) {
            this.node = node;
        }

        public void run() {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {

                for (Address address : localPeers) {
                    try {
                        Thread.sleep(10000);
                        Message messageReceived = Messager.sendTwoWayMessage(address, new Message(Message.Request.PING),
                                myAddress);

                        /* Use heartbeat to also output the block chain of the node */

                    } catch (InterruptedException e) {
                        System.out.println("Received Interrupted Exception from node " + address.getPort());
                        throw new RuntimeException(e);
                    } catch (ConcurrentModificationException e) {
                        System.out.println(e);
                        break;
                    } catch (IndexOutOfBoundsException e) {
                        System.out.println(e);
                    }
                }
            }

        }
    }

    private final int MAX_PEERS, NUM_NODES, QUORUM_SIZE, MIN_CONNECTIONS, DEBUG_LEVEL, MINIMUM_TRANSACTIONS;
    private final Object lock, quorumLock, memPoolLock, quorumReadyVotesLock, memPoolRoundsLock, sigRoundsLock,
            blockLock, accountsLock, answerHashLock, minerDataLock;
    private int quorumReadyVotes, memPoolRounds;
    private ArrayList<Address> globalPeers;
    private ArrayList<Address> localPeers;
    private HashMap<String, Transaction> mempool;
    private HashMap<String, Integer> accounts;
    private ArrayList<BlockSignature> quorumSigs;
    private LinkedList<Block> blockchain;
    public final Address myAddress;
    private ServerSocket ss;
    private PRISMBlock quorumBlock;
    private PrivateKey privateKey;
    private int state;
    public final String USE;

    private PRISMTransactionValidator txValidator = new PRISMTransactionValidator();
    public HashMap<Address, RepData> repData = new HashMap<Address, RepData>();
    private final float minAcc = .7f;
    private final float maxAcc = 1f;
    private float accuracyPercent = minAcc + (float) Math.random()*(maxAcc - minAcc);// value between 0 and 1 that determines how likely this node
                                                          // is to get a
    // correct answer as a miner.
}
