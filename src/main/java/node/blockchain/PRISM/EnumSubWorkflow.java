package node.blockchain.PRISM;

public enum EnumSubWorkflow {
    SUB_WORKFLOW_0(new SubWorkflow(6, new float[][]{{1.23f}, {2.34f}, {3.45f}, {4.56f}, {5.67f}, {6.78f}}, new float[][]{{2.34f}, {3.45f}, {4.56f}, {5.67f}, {6.78f}, {9.87f}})),
    SUB_WORKFLOW_1(new SubWorkflow(2, new float[][]{{1.0f}, {5.0f}}, new float[][]{{5.0f}, {4.0f}})),
    SUB_WORKFLOW_2(new SubWorkflow(3, new float[][]{{0.1f}, {0.2f}, {0.3f}}, new float[][]{{0.2f}, {0.3f}, {0.1f}})),
    SUB_WORKFLOW_3(new SubWorkflow(3, new float[][]{{10.0f}, {20.0f}, {30.0f}}, new float[][]{{20.0f}, {30.0f}, {20.0f}})),
    SUB_WORKFLOW_4(new SubWorkflow(4, new float[][]{{1.2f}, {2.3f}, {3.4f}, {4.5f}}, new float[][]{{2.3f}, {3.4f}, {4.5f}, {1.9f}})),
    SUB_WORKFLOW_5(new SubWorkflow(4, new float[][]{{0.1f}, {0.2f}, {0.3f}, {0.1f}}, new float[][]{{0.2f}, {0.3f}, {0.1f}, {5.1f}})),
    SUB_WORKFLOW_6(new SubWorkflow(4, new float[][]{{10.0f}, {20.0f}, {30.0f}, {40.0f}}, new float[][]{{20.0f}, {30.0f}, {40.0f}, {10.0f}})),
    SUB_WORKFLOW_7(new SubWorkflow(4, new float[][]{{11.0f}, {22.0f}, {33.0f}, {44.0f}}, new float[][]{{22.0f}, {33.0f}, {44.0f}, {55.0f}})),
    SUB_WORKFLOW_8(new SubWorkflow(4, new float[][]{{0.4f}, {0.3f}, {0.2f}, {0.1f}}, new float[][]{{0.3f}, {0.2f}, {0.1f}, {12.3f}})),
    SUB_WORKFLOW_9(new SubWorkflow(5, new float[][]{{1.0f}, {2.0f}, {3.0f}, {4.0f}, {5.0f}}, new float[][]{{2.0f}, {3.0f}, {4.0f}, {5.0f}, {6.0f}})),

    ;

    private final SubWorkflow subWorkflow;

    EnumSubWorkflow(SubWorkflow subWorkflow) {
        this.subWorkflow = subWorkflow;
    }

    public SubWorkflow getSubWorkflow() {
        return subWorkflow;
    }
}
