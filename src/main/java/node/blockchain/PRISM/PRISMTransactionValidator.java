package node.blockchain.PRISM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import node.blockchain.Block;
import node.blockchain.Transaction;
import node.blockchain.TransactionValidator;
import node.blockchain.PRISM.RecordTypes.Project;
import node.blockchain.PRISM.RecordTypes.ProvenanceRecord;
import node.blockchain.PRISM.RecordTypes.Record.RecordType;
import node.communication.Address;

public class PRISMTransactionValidator extends TransactionValidator {

    private float alpha = 0.5f;
    private float beta = 0.5f;
    private float gamma = 1;

    /**
     * Called everytime a block is added
     * 
     * @param repData
     * @param globalPeers
     * @param alpha
     * @param beta
     * @param gamma
     * @return
     */
    public HashMap<Address, RepData> calculateReputationsData(Block block, HashMap<Address, RepData> repData) {

        PRISMBlock pBlock = (PRISMBlock) block;
        float minimumTime = Float.MAX_VALUE;

        // for(Address address : pBlock.getMinerData().keySet()){
        // MinerData printingMData = pBlock.getMinerData().get(address);
        // // System.out.println("Miner: " + address + "- Time: " +
        // printingMData.getTimestamp() + " Output: " + printingMData.getOutputHash());

        // }
        // System.out.println("The correct output was " + pBlock.correctOutput);

        for (Address address : pBlock.getMinerData().keySet()) {
            minimumTime = Math.min(minimumTime, pBlock.getMinerData().get(address).getTimestamp());
        }
        // System.out.println("We found the min time: " + minimumTime);
        for (Address address : pBlock.getMinerData().keySet()) { // Get the miner data in that P rovenanceRecord
            // System.out.println(address.toString());
            // System.out.println(repData.keySet());
            // System.out.println(repData.values());

            Address addressFound = null;

            for (Address address2 : repData.keySet()) {
                if (address.equals(address2)) {
                    // System.out.println(address + " is equal to " + address2);
                    addressFound = address2;
                } else {
                    // System.out.println(address + " is not equal to " + address2);
                }
            }

            RepData rData = repData.get(addressFound);

            MinerData mData = pBlock.getMinerData().get(address);

            // System.out.println("Address" + addressFound + " ####################");

            rData.addBlocksParticipated();

            if (mData.getOutputHash().equals(pBlock.getCorrectOutput())) {

                rData.addAccuracySummation(1);
                rData.addAccuracyCount();
            } else {
                rData.addAccuracySummation(-1);
            }

            rData.addTimeSummation(mData.getTimestamp() - minimumTime);

            rData.setCurrentReputation(calculateReputation(rData)); // Calculate current
                                                                    // reputation

            System.out.println("Address " + addressFound + ": " + rData.getCurrentReputation());

            //System.out.println(addressFound + ": outputs:  " + (mData.getOutputHash() + " = " + (pBlock.getCorrectOutput()));


            repData.put(addressFound, rData); // Update the reputation data for the miner
            // System.out.println("END Address" + addressFound + " ####################");

        }




        return repData; // Return the modified reputation data
    }

    public RepData calculateReputationData(Block block, Address targetAddress, HashMap<Address, RepData> repData) {
        PRISMBlock pBlock = (PRISMBlock) block;
        float minimumTime = Float.MAX_VALUE;

        // calculate minimum time
        for (Address address : pBlock.getMinerData().keySet()) {
            minimumTime = Math.min(minimumTime, pBlock.getMinerData().get(address).getTimestamp());
        }

        // Check if the targetAddress is present in the minerData
        if (pBlock.getMinerData().containsKey(targetAddress)) {
            RepData rData = repData.get(targetAddress);
            MinerData mData = pBlock.getMinerData().get(targetAddress);

            rData.addBlocksParticipated();

            if (mData.getOutputHash() == pBlock.getCorrectOutput()) {
                rData.addAccuracySummation(1);
                rData.addAccuracyCount();
            } else {
                rData.addAccuracySummation(-1);
            }

            rData.addTimeSummation(minimumTime - mData.getTimestamp());

            rData.setCurrentReputation(calculateReputation(rData)); // Calculate current reputation

            repData.put(targetAddress, rData); // Update the reputation data for the targetAddress
        }

        return repData.get(targetAddress); // Return the modified reputation data for targetAddress
    }

    public float calculateReputation(RepData repData) {
        return (((alpha * repData.getAccurarySummation())
                + (beta * repData.getTimeSummation())) *

                (gamma * ((float) repData.getAccuracyCount() / repData.blocksParticipated)
                        / repData.blocksParticipated)); // Calculate the reputation score and return it
    }

    public boolean validate(Object[] objects, HashMap<Address, RepData> repData) {
        // TODO Auto-generated method stub
        // Here we can check what the RecordType is and validate it this way.
        PRISMTransaction transaction = (PRISMTransaction) objects[0];
        if (transaction.getRecord().getRecordType().equals(RecordType.ProvenanceRecord)) {

            return true;
        }

        return false;
    }

    @Override
    public boolean validate(Object[] objects) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validate'");
    }

}

/*
 * for(MinerData md : minerData){
 * Float minTime = Float.MAX_VALUE;
 * if(md.getAccuracy() == 1 && md.getTimestamp() < minTime){
 * this.minimumCorrectTime = minTime;
 * }
 * 
 * }
 */