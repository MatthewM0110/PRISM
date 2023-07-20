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

    public boolean[] compareMiners(float[][][] minerOutputs) {

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

        


        //perfect case
        int numArrays = minerOutputs.length;
        int maxLength = 0;
        for (float[][] arr : minerOutputs) {
            if (arr.length > maxLength) {
                maxLength = arr.length;
            }
        }

        boolean[] result = new boolean[maxLength];
        Arrays.fill(result, true);

        for (int j = 0; j < maxLength; j++) {
            float[][] elements = new float[numArrays][];
            int counter = 0;
            for (int k = 0; k < numArrays; k++) {
                if (j < minerOutputs[k].length) {
                    elements[counter] = minerOutputs[k][j];
                    counter++;
                }
            }

            if (counter > 1 && !areMatching(elements, counter)) {
                result[j] = false;
            }
        }

        return result;
    }

    private static boolean areMatching(float[][] elements, int length) {
        for (int i = 0; i < length - 1; i++) {
            if (elements[i].length != elements[i + 1].length) {
                return false; // Check if the array lengths match
            }
            for (int k = 0; k < elements[i].length; k++) {
                if (elements[i][k] != elements[i + 1][k]) {
                    return false; // Check if the values match
                }
            }
        }
        return true;
    }
}package node.blockchain.PRISM;
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

    public boolean[] compareMiners(float[][][] minerOutputs) {

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

        

        //perfect case
        int numArrays = minerOutputs.length;
        int maxLength = 0;
        for (float[][] arr : minerOutputs) {
            if (arr.length > maxLength) {
                maxLength = arr.length;
            }
        }

        boolean[] result = new boolean[maxLength];
        Arrays.fill(result, true);

        for (int j = 0; j < maxLength; j++) {
            float[][] elements = new float[numArrays][];
            int counter = 0;
            for (int k = 0; k < numArrays; k++) {
                if (j < minerOutputs[k].length) {
                    elements[counter] = minerOutputs[k][j];
                    counter++;
                }
            }

            if (counter > 1 && !areMatching(elements, counter)) {
                result[j] = false;
            }
        }

        return result;
    }

    private static boolean areMatching(float[][] elements, int length) {
        for (int i = 0; i < length - 1; i++) {
            if (elements[i].length != elements[i + 1].length) {
                return false; // Check if the array lengths match
            }
            for (int k = 0; k < elements[i].length; k++) {
                if (elements[i][k] != elements[i + 1][k]) {
                    return false; // Check if the values match
                }
            }
        }
        return true;
    }
}
