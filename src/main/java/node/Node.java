package node;

import node.blockchain.*;
import node.blockchain.PRISM.MinerData;
import node.blockchain.PRISM.PRISMTransaction;
import node.blockchain.PRISM.PRISMTransactionValidator;
import node.blockchain.PRISM.RepData;
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

    HashMap<Address, MinerData> minerDatas;

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
        minerDatas = new HashMap<Address, MinerData>();

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
            for (Address address : repData.keySet()) {
                System.out.println(repData.get(address).toString());

            }
            addBlock(new PRISMBlock(new HashMap<String, Transaction>(), "000000", 0, minerDatas)); // Creating a blank
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
                    System.out.println("Node " + myAddress.getPort() + ": has " + quorumReadyVotes + " votes. Needs: "
                            + (quorum.size() - 1));
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
        synchronized (lock) {
            minerDatas = new HashMap<Address, MinerData>();
            System.out.println("Node " + myAddress.getPort() + ": delegating work");
            HashMap<String, Integer> minerOutput = new HashMap<>(); // minerOutput contains all the hashes and their
                                                                    // counts.
            // System.out.println("I am quorum members delegating work");
            for (Address address : localPeers) {
                if (!deriveQuorum(blockchain.getLast(), 0).contains(address)) {
                    minerDatas.put(address, new MinerData(address, 0, "0"));
                    long startTime = System.currentTimeMillis();
                    // if my neighbour is a quorum member, returndoWork
                    // System.out.println("send work to " + address.toString());
                    Message reply = Messager.sendTwoWayMessage(address, new Message(Request.DELEGATE_WORK, mempool),
                            myAddress);
                    String hash = null;

                    if (reply.getRequest().name().equals("COMPLETED_WORK")) {
                        hash = Hashing.getSHAString((String) reply.getMetadata());
                        minerDatas.get(address).setOutputHash(hash);
                        // System.out.println("got work back from " + address.toString());
                        long endTime = System.currentTimeMillis();
                        // Check if the hash is already in the map. If it is, increment its count.
                        // Otherwise, add it to the map with a count of 1.
                        minerDatas.get(address).setTimestamp(endTime - startTime);
                        if (minerOutput.containsKey(hash)) {
                            minerOutput.put(hash, minerOutput.get(hash) + 1);
                        } else {
                            minerOutput.put(hash, 1);
                        }
                    }
                }
            }

            String popularHash = "";

            for (String hash : minerOutput.keySet()) {
                if (minerOutput.get(popularHash) == null)
                    popularHash = hash;

                if (minerOutput.get(hash) > minerOutput.get(popularHash)) {
                    popularHash = hash;
                }
            }
            // System.out.println("send message in quorum. hash:" + popularHash);

            sendOneWayMessageQuorum(new Message(Request.RECEIVE_ANSWER_HASH, popularHash));
        }
    }

    private ArrayList<String> quorumAnswerHashes = new ArrayList<String>();

    public void recieveAnswerHash(String hash) {

        // System.out.println("Message recieved in quorum. hash:" + hash);

        synchronized (answerHashLock) {
            // System.out.println(myAddress + "added to quorumAnsHash---" + hash);
            quorumAnswerHashes.add(hash);
            System.out.println("QuorumAnswerHashesSize: " + quorumAnswerHashes.size() + " QuorumSize: "
                    + deriveQuorum(blockchain.getLast(), 0).size());
            /* If we have all the answer hashes */
            if (quorumAnswerHashes.size() == deriveQuorum(blockchain.getLast(), 0).size() - 1) {
                /* See which is most voted between Q member */
                HashMap<String, Integer> hashVotes = new HashMap<>();

                for (String hashAnswer : quorumAnswerHashes) {
                    if (hashVotes.get(hashAnswer) == null) {
                        hashVotes.put(hashAnswer, 1);
                    } else {
                        hashVotes.put(hash, hashVotes.get(hash) + 1);
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
                System.out.println("Node " + myAddress.getPort() + ": about to send mempool");
                quorumAnswerHashes = new ArrayList<>();

                sendMempoolHashes();

                sendMinerData();
            }
        }
    }

    public void sendMinerData() {

    }

    public void receiveMinerData(MinerData md) {
        synchronized (new Object()) {
            // Do something with mier datas
            // Now we have a consistent miner datas
        }
    }

    public void doWork(HashMap<String, Transaction> txList, ObjectInputStream oin, ObjectOutputStream oout) {
        PRISMTransaction PRISMtx = null;

        for (String txHash : txList.keySet()) { // For each transaction in that block (there should only be one
            // transaction per block) - maybe
            PRISMtx = (PRISMTransaction) txList.get(txHash);
        }

        // Percentage (from 0 to 1) that controls whether to use PRISMtx.getUID hash or
        // a random hash
        // example value, adjust as needed

        // Based on a percentage (0 to 1), this should set hash to the hash of
        // PRISMtx.getUID. Otherwise, it returns a random hash
        String hash = null;

        if (Math.random() < accuracyPercent && PRISMtx != null) {
            hash = Hashing.getSHAString(PRISMtx.getUID()); // This is in place of a true answer's hash
        } else {

        }
        hash = "aaa"; // assuming you have a method to generate random hashes
        // Do work

        try {
            oout.writeObject(new Message(Request.COMPLETED_WORK, hash));
            oout.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendMempoolHashes() {
        synchronized (memPoolLock) {

            stateChangeRequest(2);

            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes invoked");

            HashSet<String> keys = new HashSet<String>(mempool.keySet());
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

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
        while (state != 2) {
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
            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": receiveMempool invoked");
            ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);
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
                constructBlock();
            }
        }
    }

    public void constructBlock() {
        synchronized (memPoolLock) {
            if (DEBUG_LEVEL == 1)
                System.out.println("Node " + myAddress.getPort() + ": constructBlock invoked");
            stateChangeRequest(3);

            /* Make sure compiled transactions don't conflict */
            HashMap<String, Transaction> blockTransactions = new HashMap<>();

            PRISMTransactionValidator tv = null;

            tv = new PRISMTransactionValidator();

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
                tv.validate(validatorObjects, repData);
                blockTransactions.put(key, transaction);
            }

            try {
                if (USE.equals("Defi")) {
                    quorumBlock = new DefiBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size());
                } else if (USE.equals("HC")) {

                    // Room to enable another use case
                    quorumBlock = new HCBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size());
                } else if (USE.equals("PRISM")) {
                    // How to do for multiple block types?
                    quorumBlock = new PRISMBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size(), minerDatas);
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

            while (state != 3) {
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
                System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs invoked");
            }

            // state = 4;
            stateChangeRequest(4);
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
                    addBlock(quorumBlock);
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
                        getBlockHash(quorumBlock, 0)); // Add miner data
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
            addBlock(newBlock);
            sendSkeleton(blockSkeleton);

        }
    }

    public Block constructBlockWithSkeleton(BlockSkeleton skeleton) {
        synchronized (memPoolLock) {
            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": constructBlockWithSkeleton(local) invoked");
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
                            blockchain.size(), minerDatas); // Miner data comes from skelton not global
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
        }
    }

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

        blockchain.add(block);
        PRISMBlock pBlock = (PRISMBlock) block;
        System.out.println("Node " + myAddress.getPort() + ": " + chainString(blockchain) + " MP: " + mempool.values()
                + " minerDatas: " + pBlock.getMinerData().keySet());

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
            PRISMTransactionValidator txValidator = new PRISMTransactionValidator();
            txValidator.calculateReputationsData(block, repData);
        }

        ArrayList<Address> quorum = deriveQuorum(blockchain.getLast(), 0);

        if (DEBUG_LEVEL == 1) {
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

    // public ArrayList<Address> deriveQuorum(Block block, int nonce) {
    // // System.out.println("Deriving quorum for block " + block.getBlockId());
    // String blockHash;

    // System.out.println(
    // "Node " + myAddress.getPort() + ": repData " + repData.keySet() +
    // repData.values());

    // if (block != null) {
    // try {
    // ArrayList<Address> quorum = new ArrayList<>();
    // blockHash = Hashing.getBlockHash(block, nonce);
    // BigInteger bigInt = new BigInteger(blockHash, 16);
    // bigInt = bigInt.mod(BigInteger.valueOf(NUM_NODES));
    // int seed = bigInt.intValue();
    // Random random = new Random(seed);

    // // // Sort the repData map by currentReputation values in descending order
    // // LinkedHashMap<Address, RepData> sortedMap = repData.entrySet()
    // // .stream()
    // // .sorted(Map.Entry
    // // .<Address,
    // //
    // RepData>comparingByValue(Comparator.comparing(RepData::getCurrentReputation))
    // // .reversed())
    // // .collect(Collectors.toMap(
    // // Map.Entry::getKey,
    // // Map.Entry::getValue,
    // // (e1, e2) -> e1,
    // // LinkedHashMap::new));

    // // // Calculate top 20% limit
    // // int topTwentyLimit = (int) Math.ceil(sortedMap.size() * 1);
    // // // Get the top 20% entries
    // // LinkedHashMap<Address, RepData> topTwentyPercent = sortedMap.entrySet()
    // // .stream()
    // // .limit(topTwentyLimit)
    // // .collect(Collectors.toMap(
    // // Map.Entry::getKey,
    // // Map.Entry::getValue,
    // // (e1, e2) -> e1,
    // // LinkedHashMap::new));

    // // // Convert map entries to a list
    // // List<Map.Entry<Address, RepData>> entries = new
    // // ArrayList<>(topTwentyPercent.entrySet());
    // // // Shuffle the list
    // // Collections.shuffle(entries, random);

    // // // Create a new LinkedHashMap and insert the shuffled entries
    // // LinkedHashMap<Address, RepData> shuffledMap = entries.stream()
    // // .collect(Collectors.toMap(
    // // Map.Entry::getKey,
    // // Map.Entry::getValue,
    // // (e1, e2) -> e1,
    // // LinkedHashMap::new));

    // // // Calculate top 5% limit
    // // int topFiveLimit = (int) Math.ceil(shuffledMap.size() * 0.5);
    // // // Get the top 5% entries
    // // LinkedHashMap<Address, RepData> topFivePercent = shuffledMap.entrySet()
    // // .stream()
    // // .limit(topFiveLimit)
    // // .collect(Collectors.toMap(
    // // Map.Entry::getKey,
    // // Map.Entry::getValue,
    // // (e1, e2) -> e1,
    // // LinkedHashMap::new));
    // // Sort the repData map by currentReputation values in descending order
    // LinkedHashMap<Address, RepData> sortedMap = repData.entrySet()
    // .stream()
    // .sorted(Map.Entry
    // .<Address,
    // RepData>comparingByValue(Comparator.comparing(RepData::getCurrentReputation))
    // .reversed())
    // .collect(Collectors.toMap(
    // Map.Entry::getKey,
    // Map.Entry::getValue,
    // (e1, e2) -> e1,
    // LinkedHashMap::new));

    // // Calculate top 50% limit
    // int topFiftyLimit = (int) Math.ceil(sortedMap.size() * 0.5);

    // // Get the top 50% entries
    // LinkedHashMap<Address, RepData> topFiftyPercent = sortedMap.entrySet()
    // .stream()
    // .limit(topFiftyLimit)
    // .collect(Collectors.toMap(
    // Map.Entry::getKey,
    // Map.Entry::getValue,
    // (e1, e2) -> e1,
    // LinkedHashMap::new));

    // // Add these to the quorum
    // quorum.addAll(topFiftyPercent.keySet());
    // System.out.println("I'm node " + myAddress + " and I think the quorum
    // consists of " + quorum.toString());

    // return quorum;

    // } catch (NoSuchAlgorithmException e) {
    // throw new RuntimeException(e);
    // }
    // }
    // return null;
    // }
    public ArrayList<Address> deriveQuorum(Block block, int nonce) {
        ArrayList<Address> quorum = new ArrayList<>();
        String blockHash;

        if (block != null) {
            try {
                blockHash = Hashing.getBlockHash(block, nonce);
                BigInteger bigInt = new BigInteger(blockHash, 16);
                bigInt = bigInt.mod(BigInteger.valueOf(NUM_NODES));
                int seed = bigInt.intValue();
                Random random = new Random(seed);

                // Get the list of addresses from the reputation data
                List<Address> addresses = new ArrayList<>(repData.keySet());
                // Shuffle the list using the generated seed
                Collections.shuffle(addresses, random);

                // Select the top 50% addresses as the quorum
                int quorumSize = addresses.size() / 3;
                for (int i = 0; i < quorumSize; i++) {
                    quorum.add(addresses.get(i));
                }

                // .println("I'm node " + myAddress + " and I think the quorum consists of " +
                // quorum.toString());

                return quorum;

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
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
            blockLock, accountsLock, answerHashLock;
    private int quorumReadyVotes, memPoolRounds;
    private ArrayList<Address> globalPeers;
    private ArrayList<Address> localPeers;
    private HashMap<String, Transaction> mempool;
    private HashMap<String, Integer> accounts;
    private ArrayList<BlockSignature> quorumSigs;
    private LinkedList<Block> blockchain;
    public final Address myAddress;
    private ServerSocket ss;
    private Block quorumBlock;
    private PrivateKey privateKey;
    private int state;
    public final String USE;

    public HashMap<Address, RepData> repData = new HashMap<Address, RepData>();
    private float accuracyPercent; // value between 0 and 1 that determines how likely this node is to get a
                                   // correct answer as a miner.

}
