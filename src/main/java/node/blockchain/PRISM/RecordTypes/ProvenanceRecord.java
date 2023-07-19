    package node.blockchain.PRISM.RecordTypes;

    import java.util.List;

    import node.blockchain.Transaction;
    import node.blockchain.PRISM.MinerData;
    import node.blockchain.PRISM.PRISMTransaction;
    import node.blockchain.PRISM.RecordTypes.Record.RecordType;
    import node.communication.utils.Hashing;

    public class ProvenanceRecord extends Record {

        /*
        * A provenance record will be contained in a workflowTaskBlock
        */
        protected String inputData; // Public key string of reciever
        protected String task; // Public key string of sender
        protected List<MinerData> minerData;
        protected float minimumCorrectTime;
        protected String answerHash;

        public String getAnswerHash() {
            return answerHash;
        }

        public void setAnswerHash(String answerHash) {
            this.answerHash = answerHash;
        }

        public float getMinimumCorrectTime() {
            return minimumCorrectTime;
        }

        public void setMinimumCorrectTime(float minimumCorrectTime) {
            this.minimumCorrectTime = minimumCorrectTime;
        }

        public String getInputData() {
            return inputData;
        }

        public void setInputData(String inputData) {
            this.inputData = inputData;
        }

        public String getTask() {
            return task;
        }
        public void setTask(String task) {
            this.task = task;
        }

        public List<MinerData> getMinerData() {
            return minerData;
        }

        public void setMinerData(List<MinerData> minerData) {
            this.minerData = minerData;
        }

        public ProvenanceRecord(String inputData, String task, String workflowID) {
            super(RecordType.ProvenanceRecord, workflowID);
            this.inputData = inputData;
            this.task = task;
        }

    }
