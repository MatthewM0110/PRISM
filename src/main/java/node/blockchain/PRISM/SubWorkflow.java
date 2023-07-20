package node.blockchain.PRISM;
import java.util.Arrays;
import java.util.Random;

public class SubWorkflow {

    private int numSteps;
    private float[][] inputs;
    private float[][] outputs;

    /**
     * Initializer for a linear subworkflow
     * @param numSteps
     * @param inputs
     * @param outputs
     */
    public SubWorkflow(int numSteps, float[][] inputs, float[][] outputs) {
        if (inputs.length != outputs.length || inputs.length != numSteps) {
            throw new IllegalArgumentException("Must provide one input and output per step.");
        }

        if (numSteps < 0) {
            throw new IllegalArgumentException("Must provide a positive number of steps.");
        }

        this.numSteps = numSteps;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public SubWorkflow subSubWorkflow(int newNumSteps) {
        if (numSteps < newNumSteps || newNumSteps > 0) {
            throw new IllegalArgumentException("Must provide a length less than existing length and greater than zero.");
        }
        float[][] newInputs = Arrays.copyOf(inputs, inputs.length - (numSteps - newNumSteps));
        float[][] newOutputs = Arrays.copyOf(outputs, outputs.length - (numSteps - newNumSteps));
        return new SubWorkflow(newNumSteps, newInputs, newOutputs);
    }
    
    /**
     * Compute a subworkflow task.
     *
     * @param step   The step to compute
     * @param input  Initial subworkflow input. Recursively will find the input associated with this step
     * @param chance The likelihood of getting this workflow step correct.
     * @return The computed output for the given step and input.
     */
    public float[][] compute(int step, float[] input, float chance) {
        if (step < 0 || step >= numSteps) {
            throw new IllegalArgumentException("Provide a valid step between [0-numSteps).");
        } else if (chance > 1 || chance < 0) {
            throw new IllegalArgumentException("Please ensure chance is a double between 0-1");
        }

        return computeDeeper(step, input, chance, step, new float[step+1][]);
    }

    private float[][] computeDeeper(int step, float input[], float chance, int intendedDepth, float[][] outputs) {
        Random rand = new Random();
        if (step > 0) {
            return computeDeeper(step - 1, input, chance, intendedDepth, outputs);
        } else {
            float[] currIn = input;

            for (int i = 0; i <= intendedDepth; i++) {
                float[] output = {rand.nextFloat()};

                if (currIn.equals(inputs[i]) && rand.nextFloat() <= chance) {
                    output = this.outputs[i];
                }
                currIn = output;
                outputs[i] = output;
            }

            return outputs;
        }
    }

    /** Get the correct first input for this subworkflow */
    public float[] getFirstInput() {
        return inputs[0];
    }

    /** Get the number of steps in the workflow  */
    public int getNumSteps() {
        return numSteps;
    }

    public float[] compareMiners(float[][][] minerOutputs) {
        //check for good formatting!
        for (int i = 0; i < minerOutputs.length; i++) {
            if (minerOutputs[i].length != i + 1){
                throw new IllegalArgumentException("Please ensure array is formatted like so: " +
                "Miner 1 outputs [[1]]"+
                "Miner 2 outputs [[1,2]]"+
                "Miner 3 outputs [[1,2,3]]"+
                "etc");
            }
        }

        int numArrays = minerOutputs.length;
        int maxLength = 0;
        for (float[][] arr : minerOutputs) {
            if (arr.length > maxLength) {
                maxLength = arr.length;
            }
        }

        float[] confidenceLevels = new float[numArrays];
        //For every array in the miners answers
        for (int j = 0; j < numArrays; j++) {
            float[] minerAnswer = minerOutputs[j][j]; 

            int count = 0;
            int numCommon = 0;
            //For every array of greater length
            for (int i = numArrays-1; i >= j; i--) {
                if (minerOutputs[i][j].equals(minerAnswer)) {
                    numCommon ++;
                }
                count++;
            }
            //Add proportional confidence for your unique answer
            confidenceLevels[j] = ((float)numCommon/count)/(j+1);

            //add proportional confidence for matching other answers.
            int smallercount = 0;
            int smallernumCommon = 0;
            int countdown = j-1;
            while (countdown >=0) {
                for(int k = j-1; k >= 0; k--) {
                    if(countdown <= k) {
                        if (minerOutputs[j][countdown].equals(minerOutputs[k][countdown])) {
                            smallernumCommon ++;
                        }  
                        smallercount++;
                    }
                }
                for (int k = numArrays - 1; k > j; k--) {
                    if(countdown <= k) {
                        if (minerOutputs[j][countdown].equals(minerOutputs[k][countdown])) {
                            smallernumCommon ++;
                        }  
                        smallercount++;
                    }
                }
                confidenceLevels[j] += ((float)(smallernumCommon+1)/(smallercount+1))/(j+1); 
                smallercount = 0;
                smallernumCommon = 0;
                
                countdown--;
            }
        }

        return confidenceLevels;
    }

    /** Returns the index of the miner with the worst confidence.
     *  Returns -1 only if all miners have confidence of 100%
     */
    public int findWorst(float[][][] minerOutputs){
        float[] confidence = compareMiners(minerOutputs);
        float minConfidence = 1;
        int iterator = 0;
        int minConfidenceSpot = -1;
        for (float c : confidence) {
            if (c < minConfidence) {
                minConfidence = c;
                minConfidenceSpot = iterator;
            }
            iterator++;
        }
        return minConfidenceSpot;
    }

    /** Returns true if the last miner has the worst confidence. Otherwise, returns false.
     * Note: returns false if all confidence of 100%. */
    public boolean isLastWorst(float[][][] minerOutputs){
        float[] confidence = compareMiners(minerOutputs);
        float minConfidence = 1;
        int iterator = 0;
        int minConfidenceSpot = -1;
        for (float c : confidence) {
            if (c < minConfidence) {
                minConfidence = c;
                minConfidenceSpot = iterator;
            }
            iterator++;
        }
        if (confidence[confidence.length - 1] == minConfidence && minConfidenceSpot != -1) {
            return true;
        }
        return false;
    }

    public float overallConfidence(float[][][] minerOutputs){
        float[] confidence = compareMiners(minerOutputs);
        float sum = 0;
        for (float c : confidence) {
            sum += c;
        }
        return sum / numSteps;
    }
}
