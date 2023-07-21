package node.blockchain;

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
import node.communication.BlockSignature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class BlockSkeleton implements Serializable{
    private final int blockId;
    private String hash;
    private final ArrayList<String> keys;
    private ArrayList<BlockSignature> signatures;
    private HashMap<Address,MinerData> minerData;

    public BlockSkeleton (int blockId, ArrayList<String> keys, ArrayList<BlockSignature> signatures, String hash, HashMap<Address,MinerData> minerData){
        this.keys = keys;
        this.blockId = blockId;
        this.signatures = signatures;
        this.hash = hash;
        this.minerData = minerData;
    }

    public ArrayList<BlockSignature> getSignatures() {return signatures;}
    public int getBlockId(){return blockId;}
    public ArrayList<String> getKeys() {return keys;}
    public String getHash(){return hash;}
    public HashMap<Address,MinerData> getMinerData(){return minerData;}

}

