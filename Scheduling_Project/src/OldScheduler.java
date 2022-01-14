import java.util.*;
import java.io.*;

public class OldScheduler {
	
	private static PriorityQueue<MyProcess> arrivalQueue = new PriorityQueue<>(); //universal priority queue for the processes
	private static Queue<MyProcess> blockedQueue = new LinkedList<>();
	private static Queue<MyProcess> exitedQueue = new LinkedList<>(); //used to print processes in order
	private static int clock = 0; //universal clock
	
	private enum Event { ARRIVE, BLOCK, EXIT, UNBLOCK, TIMEOUT }
	
	private static class MyProcess implements Comparable<MyProcess> {
		//statistics to be printed at the end to summarize the process' activity
		private int processID;
		private String instructionList = null;
		private int arrivalTime, serviceTime, startTime = -1, finishTime, turnaroundTime;
		private float normalizedTurnaroundTime, averageResponseTime;
		private int timesWaited, startWaitTime, endWaitTime, totalTimeWaited; //used purely for averageResponseTime
		
		private Scanner listScanner = null; //used to read the instructions for the process
		
		private int timeWhenUnblocked;
		private int remainingServiceTime = 0; //used by the schedulers that use time quantums
		
		private boolean arrived = false;
		private float serviceComparable; //used by SPN and depends on the service_given value
		
		private float expectedServiceTime; //used by HRRN to store the expected service time separately for calculation
		
		private int feedbackQueue = 0; //used by FEEDBACK to put it into the correct ready queue
		
		public MyProcess(int id, String list) {
			processID = id;
			instructionList = list;
			listScanner = new Scanner(instructionList);
			arrivalTime = listScanner.nextInt();
		}
		
		public int getID() { return processID; }
		public void setUnblockTime(int t) { timeWhenUnblocked = t; }
		public int getUnblockTime() { return timeWhenUnblocked; }
		public void setRemainingServiceTime(int t) { remainingServiceTime = t; }
		public int getRemainingServiceTime() { return remainingServiceTime; }
		public int getArrival() { return arrivalTime; }
		public int getStart() { return startTime; }
		public int getTurnaroundTime() { return turnaroundTime; }
		public float getNormalizedTurnaroundTime() { return normalizedTurnaroundTime; }
		public float getAverageResponseTime() { return averageResponseTime; }
		public void setServiceComparable(float t) { serviceComparable = t; }
		public float getServiceComparable() { return serviceComparable; }
		public int getTimeWaited() { return totalTimeWaited; }
		public void setExpectedService(float t) { expectedServiceTime = t; }
		public float getExpectedService() { return expectedServiceTime; }
		public void updateFeedbackQueue() { feedbackQueue++; }
		public int getFeedbackQueue() { return feedbackQueue; }
		public void updateServiceTime(int t) { serviceTime += t; }
		public void setStartTime(int t) { startTime = t; }
		public void startWait(int t) {
			timesWaited++;
			startWaitTime = t;
		}
		public void endWait(int t) {
			endWaitTime = t;
			totalTimeWaited += endWaitTime - startWaitTime;
			if(endWaitTime == startWaitTime) { //don't count the immediate transition from ready to running
				timesWaited--;
			}
		}
		public void setFinishTime(int t) {
			finishTime = t;
			turnaroundTime = finishTime - arrivalTime;
			normalizedTurnaroundTime = (float)turnaroundTime / serviceTime;
			averageResponseTime = (float)totalTimeWaited / timesWaited;
		}
		public void processEvent(Event e) {
			if(e == Event.ARRIVE) {
				//this.setStartTime(clock);
				arrived = true;
																						//IDK
			} else if(e == Event.BLOCK) {
				//this.startWait(clock);
				blockedQueue.add(this);
			} else if(e == Event.EXIT) {
				listScanner.close();
				this.setFinishTime(clock);
				this.printProcess();
			} else if(e == Event.UNBLOCK) {
				//this.endWait(clock);
				blockedQueue.remove(this);
			} else if(e == Event.TIMEOUT) {
																						//IDK
			}
		}
		public boolean hasNextInstruction() { return listScanner.hasNext(); }
		public String nextString() { return listScanner.next(); }
		public int nextInt() { return listScanner.nextInt(); }
		public void resetScanner() {
			listScanner.close();
			listScanner = new Scanner(instructionList);
			listScanner.nextInt(); //gets rid of arrival time
		}
		public int wordCount() {
			StringTokenizer words = new StringTokenizer(instructionList);
			return words.countTokens();
		}
		public void exit() {
			listScanner.close();
			this.setFinishTime(clock);
			exitedQueue.add(this);
		}
		public void printProcess() {
			System.out.println("Process " + processID + ":");
			System.out.println("	Arrival = " + arrivalTime);
			System.out.println("	Service = " + serviceTime);
			System.out.println("	Start = " + startTime);
			System.out.println("	Finish = " + finishTime);
			System.out.println("	Turnaround = " + turnaroundTime);
			System.out.println("	Normalized Turnaround = " + normalizedTurnaroundTime);
			System.out.println("	Average Response Time = " + averageResponseTime);
		}
		
		//used by the priority queue to sort the processes by their arrival time
		public int compareTo(MyProcess p) {
			//use arrival time for arrival priority queue
			if(!arrived) {
				if(this.arrivalTime < p.getArrival())
		            return -1;
		        else if(this.arrivalTime > p.getArrival())
		            return 1;
		        else
		            return 0;
			//use service comparable for priority queue after the process has arrived
			} else {
				if(this.serviceTime < p.getServiceComparable())
		            return -1;
		        else if(this.serviceTime > p.getServiceComparable())
		            return 1;
		        else
		            return 0;
			}
		}
	}
	
	//checks if there are more instructions, exits if not and returns false
	public boolean MoreInstructions(MyProcess process) {
		if(!process.hasNextInstruction()) {
			process.exit();
			return false;
		}
		return true;
	}
	//checks if there are processes that have arrived and moves them to readyQueue
	public void NewArrivals(Queue<MyProcess> readyQueue) {
		MyProcess currentProcess = arrivalQueue.peek();
		while(currentProcess != null && currentProcess.getArrival() <= clock) {
			currentProcess = arrivalQueue.poll();
			readyQueue.add(currentProcess);
			currentProcess.processEvent(Event.ARRIVE); //event Arrives
			currentProcess.startWait(currentProcess.getArrival());
			currentProcess = arrivalQueue.peek();
		}
	}
	//checks if there are processes that have unblocked and moves them to readyQueue
	public void NewUnblocks(Queue<MyProcess> readyQueue) {
		if(!blockedQueue.isEmpty()) {
			//uses an iterator to avoid co-modification errors
			for (Iterator<MyProcess> iterator = blockedQueue.iterator(); iterator.hasNext(); ) {
			    MyProcess process = iterator.next();
			    if(process.getUnblockTime() <= clock) {
					//process.processEvent(Event.UNBLOCK);									//bruh
			    	iterator.remove();
			    	readyQueue.add(process);
					process.startWait(process.getUnblockTime());
				}
			}
		}
	}
	//begins running the process by checking if it's its first time running and ending its wait
	public void BeginRunning(MyProcess process) {
		if(process.getStart() == -1) { //check if this is the first time the process has ran
			if(process.getArrival() > clock) { //checks if the arrival is later than the time
				clock = process.getArrival();
			}
			process.setStartTime(clock);
		}
		process.endWait(clock);
	}
	//since all algorithms read the IO instruction the same way, it can all be done here
	public void ReadIO(MyProcess process) {
		if(MoreInstructions(process)) {
			String instruction = process.nextString();
			if(instruction.equals("IO")) {
				int blockTime = process.nextInt();
				process.setUnblockTime(clock + blockTime);
				process.processEvent(Event.BLOCK);
			} else {
				System.out.println("instruction read: " + instruction + ", IO expected");
			}
		}
	}
	//moves clock forward to unblock the process closest to being unblocked for when the ready queue is empty
	public void UnblockNext() {
		if(!blockedQueue.isEmpty()) {
			int newClock = blockedQueue.peek().getUnblockTime();
			for (MyProcess p : blockedQueue) {
				if(p.getUnblockTime() < newClock) {
					newClock = p.getUnblockTime();
				}
			}
			clock = newClock;
		}
	}
	//takes all processes in a regular queue and moves them to a priority queue, probably the temp queue to ready queue
	public void QueueToPriorityQueue(Queue<MyProcess> queue, PriorityQueue<MyProcess> priority) {
		for (Iterator<MyProcess> iterator = queue.iterator(); iterator.hasNext(); ) {
		    MyProcess process = iterator.next();
		    priority.add(process);
		    iterator.remove();
		}
	}
	//calculates the total service time ahead of running the process for SPN and HRRN
	public int CalcServiceTime(MyProcess process) {
		String inst;
		int val;
		int total = 0;
		while(process.hasNextInstruction()) {
			inst = process.nextString();
			val = process.nextInt();
			if(inst.equals("CPU")) {
				total += val;
			}
		}
		process.resetScanner();
		return total;
	}
	//calculates the estimated service time with exponential averaging for SPN and HRRN
	public float CalcExponentialAverage(MyProcess process) {
		float alpha = 0.8f;
		int[] T = new int[process.wordCount()/2];
		float[] S = new float[process.wordCount()/2];
		process.nextString(); //requires at least one instruction
		int val = process.nextInt();
		S[0] = (float)val; //sets first CPU service time as S0
		T[1] = val;
		S[1] = alpha*T[0] + (1-alpha)*S[0];
		int n = 2;
		while(process.hasNextInstruction()) {
			String inst = process.nextString();
			val = process.nextInt();
			if(inst.equals("CPU")) {
				T[n] = val;
				S[n] = alpha*T[n-1] + (1-alpha*S[n-1]);
				n++;
			}
		}
		process.resetScanner();
		return S[n-1];
	}
	//prints the processes and other statistics once the scheduler is done
	public void PrintProcesses() {
		int numProcesses = exitedQueue.size();
		MyProcess minID;
		while(!exitedQueue.isEmpty()) {
			minID = exitedQueue.poll();
			for(MyProcess p : exitedQueue) {
				if(p.getID() < minID.getID()) {
					minID = p;
				}
			}
			minID.printProcess();
		}
		int meanTurnaround = 0;
		int meanNormalizedTurnaround = 0;
		int meanAverageResponse = 0;
		for(MyProcess p : exitedQueue) {
			meanTurnaround += p.getTurnaroundTime();
			meanNormalizedTurnaround += p.getNormalizedTurnaroundTime();
			meanAverageResponse += p.getAverageResponseTime();
		}
		meanTurnaround /= numProcesses;
		meanNormalizedTurnaround /= numProcesses;
		meanAverageResponse /= numProcesses;
		System.out.println("Mean Turnaround: " + meanTurnaround);
		System.out.println("Mean Normalized Turnaround: " + meanNormalizedTurnaround);
		System.out.println("Mean Average Response Time: " + meanAverageResponse);
	}
	
	//First Come First Serve
	public void FCFS() {
		MyProcess currentProcess;
		Queue<MyProcess> readyQueue = new LinkedList<>();	
		while (!arrivalQueue.isEmpty() || !readyQueue.isEmpty() || !blockedQueue.isEmpty()) {
			//move all processes whose arrival times have come to the ready queue in the order they arrived
			NewArrivals(readyQueue);
			
			//unblocks all processes who can be unblocked
			NewUnblocks(readyQueue);
			
			//run the process at beginning of ready queue
			currentProcess = readyQueue.poll();
			if(currentProcess != null) {
				//do preliminary checking and end the waiting for the now running process
				BeginRunning(currentProcess);
				
				//read CPU instruction
				if(MoreInstructions(currentProcess)) {
					String instruction = currentProcess.nextString();
					if(instruction.equals("CPU")) {
						int serviceTime = currentProcess.nextInt();
						clock += serviceTime;
						currentProcess.updateServiceTime(serviceTime);
					} else {
						System.out.println("instruction read: " + instruction + ", CPU expected");
					}
				}
				
				//read IO instruction
				ReadIO(currentProcess);
				
			} else {
				//move clock forward for next process to be unblocked since ready queue is empty
				UnblockNext();
			}
		}
		PrintProcesses();
	}
	
	//Round Robin
	public void RR(int quantum) {
		MyProcess currentProcess;
		Queue<MyProcess> readyQueue = new LinkedList<>();
		while (!arrivalQueue.isEmpty() || !readyQueue.isEmpty() || !blockedQueue.isEmpty()) {
			//move all processes whose arrival times have come to the ready queue in the order they arrived
			NewArrivals(readyQueue);
			
			//unblocks all processes who can be unblocked
			NewUnblocks(readyQueue);
			
			//run the process at beginning of ready queue
			currentProcess = readyQueue.poll();
			if(currentProcess != null) {
				//do preliminary checking and end the waiting for the now running process
				BeginRunning(currentProcess);
				
				//deal with the new CPU service time or any remaining service time
				if(currentProcess.getRemainingServiceTime() != 0) { //check if it has leftover service time
					//run the remaining service time
					if(currentProcess.getRemainingServiceTime() > quantum) {
						clock += quantum;
						currentProcess.setRemainingServiceTime(currentProcess.getRemainingServiceTime() - quantum);
						currentProcess.processEvent(Event.TIMEOUT); //process times out after quantum time
						NewArrivals(readyQueue); //makes sure that every process is put into the queue in the correct order
						readyQueue.add(currentProcess);
					} else {
						clock += currentProcess.getRemainingServiceTime();
						currentProcess.setRemainingServiceTime(0);
					}
				} else {
					//read CPU instruction since there's no leftover service time
					if(MoreInstructions(currentProcess)) {
						String instruction = currentProcess.nextString();
						if(instruction.equals("CPU")) {
							int serviceTime = currentProcess.nextInt();
							if(serviceTime > quantum) {
								clock += quantum;
								currentProcess.setRemainingServiceTime(serviceTime - quantum);
								currentProcess.processEvent(Event.TIMEOUT); //process times out after quantum time
								NewArrivals(readyQueue); //makes sure that every process is put into the queue in the correct order
								readyQueue.add(currentProcess);
							} else {
								clock += serviceTime;
							}
							currentProcess.updateServiceTime(serviceTime);
						} else {
							System.out.println("instruction read: " + instruction + ", CPU expected");
						}
					}
				}
				
				if(currentProcess.getRemainingServiceTime() == 0) {
					//read IO instruction since there is no leftover service time
					ReadIO(currentProcess);
				}
			} else {
				//move clock forward for next process to be unblocked since ready queue is empty
				UnblockNext();
			}
		}
		PrintProcesses();
	}
	
	//Shortest Process Next
	public void SPN(boolean serviceGiven) {
		MyProcess currentProcess;
		Queue<MyProcess> tempQueue = new LinkedList<>();
		PriorityQueue<MyProcess> readyQueue = new PriorityQueue<>(Collections.reverseOrder()); //reversed to get highest first
		
		if(serviceGiven) {
			//calculate the total service time
			for (MyProcess p : arrivalQueue) {
				p.setServiceComparable(CalcServiceTime(p));
			}
		} else {
			//calculate the exponential average
			for(MyProcess p : arrivalQueue) {
				p.setServiceComparable(CalcExponentialAverage(p));
			}
		}
		
		while (!arrivalQueue.isEmpty() || !readyQueue.isEmpty() || !blockedQueue.isEmpty()) {
			//move all processes whose arrival times have come to the ready queue in the order they arrived
			NewArrivals(tempQueue);
			QueueToPriorityQueue(tempQueue, readyQueue); //moves all processes from tempQueue to readyQueue in order
			
			//unblocks all processes who can be unblocked
			NewUnblocks(tempQueue);
			QueueToPriorityQueue(tempQueue, readyQueue);
			
			currentProcess = readyQueue.poll();
			if(currentProcess != null) {
				//do preliminary checking and end the waiting for the now running process
				BeginRunning(currentProcess);
				
				//read CPU instruction
				if(MoreInstructions(currentProcess)) {
					String instruction = currentProcess.nextString();
					if(instruction.equals("CPU")) {
						int serviceTime = currentProcess.nextInt();
						clock += serviceTime;
						currentProcess.updateServiceTime(serviceTime);
					} else {
						System.out.println("instruction read: " + instruction + ", CPU expected");
					}
				}
				
				//read IO instruction
				ReadIO(currentProcess);
				
			} else {
				//move clock forward for next process to be unblocked since ready queue is empty
				UnblockNext();
			}
		}
		PrintProcesses();
	}
	
	//Highest Response Ratio Next
	public void HRRN(boolean serviceGiven) {
		MyProcess currentProcess;
		Queue<MyProcess> tempQueue = new LinkedList<>();
		PriorityQueue<MyProcess> readyQueue = new PriorityQueue<>();
		
		if(serviceGiven) {
			//calculate the total service time
			for (MyProcess p : arrivalQueue) {
				p.setExpectedService(CalcServiceTime(p));
				p.setServiceComparable(1.0f);
			}
		} else {
			//calculate the exponential average
			for(MyProcess p : arrivalQueue) {
				p.setExpectedService(CalcExponentialAverage(p));
				p.setServiceComparable(1.0f);
			}
		}
		
		
		while (!arrivalQueue.isEmpty() || !readyQueue.isEmpty() || !blockedQueue.isEmpty()) {
			//move all processes whose arrival times have come to the ready queue in the order they arrived
			NewArrivals(tempQueue);
			QueueToPriorityQueue(tempQueue, readyQueue);
			
			//unblocks all processes who can be unblocked
			NewUnblocks(tempQueue);
			QueueToPriorityQueue(tempQueue, readyQueue);
			
			currentProcess = readyQueue.poll();
			if(currentProcess != null) {
				//do preliminary checking and end the waiting for the now running process
				BeginRunning(currentProcess);
				
				//read CPU instruction
				if(MoreInstructions(currentProcess)) {
					String instruction = currentProcess.nextString();
					if(instruction.equals("CPU")) {
						int serviceTime = currentProcess.nextInt();
						clock += serviceTime;
						currentProcess.updateServiceTime(serviceTime);
					} else {
						System.out.println("instruction read: " + instruction + ", CPU expected");
					}
				}
				
				//updates the response ratio before it gets blocked and moved back to the ready queue
				int w = currentProcess.getTimeWaited();
				float s = currentProcess.getExpectedService();
				currentProcess.setServiceComparable((w+s)/s);
				
				//read IO instruction
				ReadIO(currentProcess);
				
			} else {
				//move clock forward for next process to be unblocked since ready queue is empty
				UnblockNext();
			}
		}
		PrintProcesses();
	}
	
	//Feedback
	public void FEEDBACK(int quantum, int numPriorities) {
		MyProcess currentProcess;
		@SuppressWarnings("unchecked")
		Queue<MyProcess>[] queues = new Queue[numPriorities];
		for(int i=0; i<queues.length; i++) {
			queues[i] = new LinkedList<>();
		}
		boolean queuesEmpty = false;
		while (!arrivalQueue.isEmpty() || !queuesEmpty || !blockedQueue.isEmpty()) {
			//move all processes whose arrival times have come to the ready queue in the order they arrived
			NewArrivals(queues[0]);
			
			//unblocks all processes who can be unblocked
			if(!blockedQueue.isEmpty()) {
				for (Iterator<MyProcess> iterator = blockedQueue.iterator(); iterator.hasNext(); ) {
				    MyProcess process = iterator.next();
				    if(process.getUnblockTime() <= clock) {
						//process.processEvent(Event.UNBLOCK);									//bruh
				    	iterator.remove();
				    	queues[process.getFeedbackQueue()].add(process); //adds process to its next ready queue down
						process.startWait(process.getUnblockTime());
					}
				}
			}
			
			//run the process at beginning of the highest non-empty ready queue
			currentProcess = null;
			for(Queue<MyProcess> q : queues) {
				if(!q.isEmpty()) {
					currentProcess = q.poll();
					break;
				}
			}
			
			if(currentProcess != null) {
				//do preliminary checking and end the waiting for the now running process
				BeginRunning(currentProcess);
				
				//move the process' next queue down one if it's not at the bottom
				if(currentProcess.getFeedbackQueue() < numPriorities-1)
					currentProcess.updateFeedbackQueue();
				
				//deal with the new CPU service time or any remaining service time
				if(currentProcess.getRemainingServiceTime() != 0) { //check if it has leftover service time
					//run the remaining service time
					if(currentProcess.getRemainingServiceTime() > quantum) {
						clock += quantum;
						currentProcess.setRemainingServiceTime(currentProcess.getRemainingServiceTime() - quantum);
						currentProcess.processEvent(Event.TIMEOUT); //process times out after quantum time
						queues[currentProcess.getFeedbackQueue()].add(currentProcess);
					} else {
						clock += currentProcess.getRemainingServiceTime();
						currentProcess.setRemainingServiceTime(0);
					}
				} else {
					//read CPU instruction since there's no leftover service time
					if(MoreInstructions(currentProcess)) {
						String instruction = currentProcess.nextString();
						if(instruction.equals("CPU")) {
							int serviceTime = currentProcess.nextInt();
							if(serviceTime > quantum) {
								clock += quantum;
								currentProcess.setRemainingServiceTime(serviceTime - quantum);
								currentProcess.processEvent(Event.TIMEOUT); //process times out after quantum time
								queues[currentProcess.getFeedbackQueue()].add(currentProcess);
							} else {
								clock += serviceTime;
							}
							currentProcess.updateServiceTime(serviceTime);
						} else {
							System.out.println("instruction read: " + instruction + ", CPU expected");
						}
					}
				}
				
				if(currentProcess.getRemainingServiceTime() == 0) {
					//read IO instruction since there is no leftover service time
					ReadIO(currentProcess);
				}
			} else {
				//move clock forward for next process to be unblocked since ready queue is empty
				UnblockNext();
			}
			//check if the ready queues are empty
			queuesEmpty = true;
			for(Queue<MyProcess> q : queues) {
				if(!q.isEmpty()) {
					queuesEmpty = false;
					break;
				}
			}														
		}
		PrintProcesses();
	}
	
	static public void main(String[] args) {
		//assigns files from command line
		File algorithmFile = new File(args[0]);
		File processFile = new File(args[1]);
		
		//read the lines from process file and add the processes to the arrival queue
		int i = 0; //used to id processes
		try {
		Scanner processScanner = new Scanner(processFile);
		while (processScanner.hasNextLine()) {
			String line = processScanner.nextLine();
			arrivalQueue.add(new MyProcess(i, line));
			i++;
		}
		processScanner.close();	
		} catch (FileNotFoundException e) {
			System.out.println("Unable to open processFile: " + e);
		}
		
		//read the algorithm file and run the appropriate function to simulate the scheduler
		try {
		Scanner algorithmScanner = new Scanner (algorithmFile);
		String algorithm = algorithmScanner.nextLine();
		OldScheduler scheduler = new OldScheduler();
		
		//run First Come First Serve
		if(algorithm.equals("FCFS")) {
			scheduler.FCFS();
			
		//run Round Robin
		} else if(algorithm.equals("RR")) {
			String line = algorithmScanner.nextLine();
			int quantum = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			scheduler.RR(quantum);
			
		//run Shortest Process Next
		} else if(algorithm.equals("SPN")) {
			String line = algorithmScanner.nextLine();
			String serviceGiven = line.substring(line.lastIndexOf('=')+1);
			if(serviceGiven.equals("true") || serviceGiven.equals("false")) {
				boolean serviceGivenB = Boolean.parseBoolean(serviceGiven);
				scheduler.SPN(serviceGivenB);
			} else {
				System.out.println("Invalid service_given value in algorithmFile: " + serviceGiven);
			}
			
		//run Highest Response Ratio Next
		} else if(algorithm.equals("HRRN")) {
			String line = algorithmScanner.nextLine();
			String serviceGiven = line.substring(line.lastIndexOf('=')+1);
			if(serviceGiven.equals("true") || serviceGiven.equals("false")) {
				boolean serviceGivenB = Boolean.parseBoolean(serviceGiven);
				scheduler.HRRN(serviceGivenB);
			} else {
				System.out.println("Invalid service_given value in algorithmFile: " + serviceGiven);
			}
			
		//run Feedback
		} else if(algorithm.equals("FEEDBACK")) {
			String line = algorithmScanner.nextLine();
			int quantum = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			line = algorithmScanner.nextLine();
			int numPriorities = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			scheduler.FEEDBACK(quantum, numPriorities);
			
		//default: algorithm name is invalid
		} else {
			System.out.println("Invalid algorithm name in algorithmFile: " + algorithm);
		}
		
		algorithmScanner.close();
		} catch (FileNotFoundException e) {
			System.out.println("Unable to open algorithmFile: " + e);
		}
	}
}