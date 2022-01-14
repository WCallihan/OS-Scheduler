included source files:
Scheduler.java

To execute the program, compile the Scheduler.java file and then run it with 2 command line arguments, an algorithm file and a processes file.
For example, in any Linux environment, execute the following:
javac Scheduler.java
java Scheduler <algorithm_file> <process_file>

or simply run the program in any Java IDE with the same command line arguments.

The processes are IDed in the order that they appear in the process file, regardless of their arrival time and are printed in ID order.
If, for any reason, a process never has to wait to use the CPU, then it will return an average response time of 0.

The average response time is calculated by average the times that a processes waits to enter the CPU after it has been unblocked or after it has arrived.
This calculation is different than the example's calculation which included the time it was sitting in the IO block state.
However, my calculation is the one described in the project description and the one described in the text book.