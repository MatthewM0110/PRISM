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
        this.currentReputation = 0;
    }
    
    public void addTimeSummation(float time){
        float value = (float) (1f/Math.pow(2,time));
        this.timeSummation += value;
    }
    public void addAccuracySummation(float accuracy){
        this.accurarySummation += accuracy;
    }
    public void addBlocksParticipated(){
        this.blocksParticipated++;
    }
    public void addAccuracyCount(){
        this.accuracyCount++;
    }
    public float getCurrentReputation() {
        return currentReputation;
    }
    public void setCurrentReputation(float getCurrentReputation) {
        this.currentReputation = getCurrentReputation;
    }
    public int getBlocksParticipated() {
        return blocksParticipated;
    }
    public void setBlocksParticipated(int blocksParticipated) {
        this.blocksParticipated = blocksParticipated;
    }
    public float getTimeSummation() {
        return timeSummation;
    }
    public void setTimeSummation(float timeSummation) {
        this.timeSummation = timeSummation;
    }
    public float getAccurarySummation() {
        return accurarySummation;
    }
    public void setAccurarySummation(float accurarySummation) {
        this.accurarySummation = accurarySummation;
    }
    public int getAccuracyCount() {
        return accuracyCount;
    }
    public void setAccuracyCount(int accuracyCount) {
        this.accuracyCount = accuracyCount;
    }  

    public String toString() {
        return "-- Current Reputation | " + getCurrentReputation(); 
    }
}
