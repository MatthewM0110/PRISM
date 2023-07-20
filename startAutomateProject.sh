workflowID=(1 2 3 4 5 6 7 8 9 10)
inputData=(1 2 3 4)
task=(1 2 3)
for i in {1..3}
do
    echo "t"
    echo ${workflowID[$((RANDOM % 10))]}
    echo ${inputData[$((RANDOM % 4))]}
    echo ${task[$((RANDOM % 3))]}
done > inputs.txt
# Generate the last line as "e" without a trailing newline
printf "exit" >> inputs.txt

# Use the generated inputs to call your Java program
java -cp target/network-1.0-SNAPSHOT.jar client.Client < inputs.txt
