package node.blockchain.PRISM;

import node.communication.Address;
import java.lang.*;

public class RepData {

    float currentReputation;
    int blocksParticipated;
    float timeSummation;
    float accurarySummation;
    int accuracyCount;

    public RepData() {
   
    }

    public void addTimeSummation(float time) {

        float value = (float)1f/( (float)(Math.pow(2f, time)));
      
        this.timeSummation += value;
    }

    public void addAccuracySummation(float accuracy) {
        this.accurarySummation += accuracy;
    }

    public void addBlocksParticipated() {
        this.blocksParticipated++;
    }

    public void addAccuracyCount() {
        this.accuracyCount++;
    }

    public float getCurrentReputation() {
        return currentReputation;
    }

    public void setCurrentReputation(float setCurrentReputation) {
        this.currentReputation = setCurrentReputation;
    }

    public int getBlocksParticipated() {
        return blocksParticipated;
    }

    

    public float getTimeSummation() {
        return timeSummation;
    }

   

    public float getAccurarySummation() {
        return accurarySummation;
    }

    

    public int getAccuracyCount() {
        return accuracyCount;
    }

   

    public String toString() {

        return String.format(
            "Reputation: %-20.2f blocksParticipated: %-20d accuracySummation: %-20.2f TimeSummation: %-20.2f accuracyCount: %-20d",
            getCurrentReputation(), getBlocksParticipated(), getAccurarySummation(), getTimeSummation(), getAccuracyCount()
        );
        
        

    }
}
