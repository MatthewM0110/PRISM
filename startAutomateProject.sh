workflowID=(1 2 3 4 5 6 7 8 9 10)
inputData=(1 2 3 4)
task=(1 2 3)
for i in {1..19}
do
    echo "t"
    echo ${workflowID[$((RANDOM % 10))]}
    echo ${inputData[$((RANDOM % 4))]}
    echo ${task[$((RANDOM % 3))]}
done > inputs.txt
# Generate the last set of input without an extra newline at the end
echo "t" >> inputs.txt
echo ${workflowID[$((RANDOM % 10))]} >> inputs.txt
echo ${inputData[$((RANDOM % 4))]} >> inputs.txt
printf "${task[$((RANDOM % 3))]}" >> inputs.txt

# Use the generated inputs to call your Java program
java -cp target/network-1.0-SNAPSHOT.jar client.Client < inputs.txt
