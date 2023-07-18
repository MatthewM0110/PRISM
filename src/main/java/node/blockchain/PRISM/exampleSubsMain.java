package node.blockchain.PRISM;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;

public class exampleSubsMain {
    
    public static void main(String[] args) throws Exception {

    //Workflow 1
        Random rand = new Random();
        int w1Len = 4;
        float[][] w1in = new float[w1Len][];
        float[][] w1out = new float[w1Len][];
        //first input independent of all else
        w1in[0] = new float[]{rand.nextFloat()};
        for (int i = 1; i < w1Len; i++) {
            float[] layeredInOut = {rand.nextFloat()};
            w1in[i] = layeredInOut;
            w1out[i - 1] = layeredInOut;
        }
        //last output independent of all else
        w1out[w1Len - 1] = new float[]{rand.nextFloat()};

        //Testing w1
        SubWorkflow w1 = new SubWorkflow(w1Len, w1in, w1out);
        int testLength = w1Len - 1;

        float[][] m1 = w1.compute((0), w1.getFirstInput(), (float).95);
        float[][] m2 = w1.compute((1), w1.getFirstInput(), (float).95);
        float[][] m3 = w1.compute((2), w1.getFirstInput(), (float).95);
        float[][] m4 = w1.compute((3), w1.getFirstInput(), (float).95);

        System.out.println("___________________");
        for(float[] w : m1) {
            for(float e : w) {
            System.out.println(e);
            }
        }
        System.out.println("___________________");
        for(float[] w : m2) {
            for(float e : w) {
            System.out.println(e);
            }
        }
        System.out.println("___________________");
        for(float[] w : m3) {
            for(float e : w) {
            System.out.println(e);
            }
        }
        System.out.println("___________________");
        for(float[] w : m4) {
            for(float e : w) {
            System.out.println(e);
            }
        }
        System.out.println("___________________");

        float[][][] miners = new float[][][]{m1, m2, m3, m4};
        System.out.println(Arrays.toString(w1.compareMiners(miners)));


    }
}
