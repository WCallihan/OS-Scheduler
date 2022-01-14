import java.util.*;
import java.io.*;

public class Scheduler {	
	private enum EventType { ARRIVE, BLOCK, EXIT, UNBLOCK, TIMEOUT }
	
	private static class Event implements Comparable<Event> {
		private EventType type;
		private Process process;
		private int time;
		
		public Event(EventType e, Process p, int t) {
			type = e;
			process = p;
			time = t;
		}
		
		public EventType getType() { return type; }
		public Process getProcess() { return process; }
		public int getTime() { return time; }
		
		//used by the event queues to poll the events in order
		public int compareTo(Event e) {
			int result = 0;
			//uses the service comparable as the primary sorter for SPN and HRRN
			//this does not apply if this is an unblock event because it is currently blocked and therefore cannot be selected
			//	so it is not competing with anything else based on its service comparable
			if(this.getProcess().getServiceComparable() != -1 && this.getType() != EventType.UNBLOCK) {
				if(this.getProcess().getServiceComparable() < e.getProcess().getServiceComparable())
					result = -1;
				else if(this.getProcess().getServiceComparable() > e.getProcess().getServiceComparable())
					result = 1;
				else
					result = 0;
			}
			//uses the time as the comparable for FCFS, RR, and FB and as a tie breaker for SPN and HRRN or used for
			//	unblocking events in SPN and HRRN
			if(result == 0) {
				if(this.time < e.getTime())
					result = -1;
				else if(this.time > e.getTime())
					result = 1;
				else
					result = 0;
			}
			return result;
		}
	}
	
	private static class Process implements Comparable<Process> {
		//statistics to be printed at the end to summarize the process' activity
		private int processID;
		private int arrivalTime, serviceTime, startTime = -1, finishTime, turnaroundTime;
		private float normalizedTurnaroundTime, averageResponseTime;
		private int timesWaited, totalTimeWaited; //used purely for averageResponseTime
	
		private String line; //stored so that the scanner can be reset
		private Scanner lineScanner = null; //used to go through the instructions
		private int serviceRemaining = 0; //used by RR and FB when the process is timed out
		private float serviceComparable = -1; //used by SPN and HRRN, set to -1 otherwise
		private float expectedServiceTime; //used by HRRN in the service comparable calculation
		private int feedbackQueue = 0; //used by FB to put it into the correct priority queue
		
		public Process(int i, String l) {
			processID = i;
			line = l;
			lineScanner = new Scanner(line);
		}
		
		//getters and setters used for the basic statistics of the process
		public int getID() { return processID; }
		public void setArrival(int t) { arrivalTime = t; }
		public void updateService(int t) { serviceTime += t; }
		public void setStart(int t) { startTime = t; }
		public void updateWait(int t) {
			totalTimeWaited += t;
			if(t != 0)
				timesWaited++;
		}
		public int getWaited() { return timesWaited; }
		public void setFinishTime(int t) {
			finishTime = t;
			turnaroundTime = finishTime - arrivalTime;
			normalizedTurnaroundTime = (float)turnaroundTime / serviceTime;
			if(timesWaited > 0)
				averageResponseTime = (float)totalTimeWaited / timesWaited;
			else if(timesWaited == 0)
				averageResponseTime = 0;
			else
				System.out.println("negative timesWaited detected for process " + processID);
		}
		public int getTurnaround() { return turnaroundTime; }
		public float getNormalizedTurnaround() { return normalizedTurnaroundTime; }
		public float getAverageResponse() { return averageResponseTime; }
		
		//getters and setters used for other "helper" variables of the process
		public void setServiceRemaining(int t) { serviceRemaining = t; }
		public int getServiceRemaining() { return serviceRemaining; }
		public void setServiceComparable(float c) { serviceComparable = c; }
		public float getServiceComparable() { return serviceComparable; }
		public void setExpectedService(float t) { expectedServiceTime = t; }
		public float getExpectedService() { return expectedServiceTime; }
		public int updateFeedbackQueue(int max) {
			if(feedbackQueue < max-1) {
				feedbackQueue++;
			}
			return feedbackQueue;
		}
		public int getFeedbackQueue() { return feedbackQueue; }
		
		//functions to help with the reading of the process' instruction list
		public boolean hasNext() { return lineScanner.hasNext(); }
		public String nextString() { return lineScanner.next(); }
		public int nextInt() { return lineScanner.nextInt(); }
		public void resetScanner() {
			lineScanner.close();
			lineScanner = new Scanner(line);
			lineScanner.nextInt(); //gets rid of arrival time
		}
		public int wordCount() {
			StringTokenizer words = new StringTokenizer(line);
			return words.countTokens();
		}
		
		//functions used when the process has completed and is wrapping up its execution
		public void exit(int t) {
			lineScanner.close();
			this.setFinishTime(t);
		}
		public void print() {
			System.out.println("Process " + processID + ":");
			System.out.println("	Arrival = " + arrivalTime);
			System.out.println("	Service = " + serviceTime);
			System.out.println("	Start = " + startTime);
			System.out.println("	Finish = " + finishTime);
			System.out.println("	Turnaround = " + turnaroundTime);
			System.out.println("	Normalized Turnaround = " + normalizedTurnaroundTime);
			System.out.println("	Average Response Time = " + averageResponseTime);
		}
		
		//used by the exit queue to print the processes in order
		public int compareTo(Process p) {
			if(this.processID < p.getID())
				return -1;
			else if(this.processID > p.getID())
				return 1;
			else {
				return 0;
			}
		}
	}
	
	//opens a file scanner
	public Scanner OpenScanner(File file) {
		try {
		Scanner scan = new Scanner(file);
		return scan;
		} catch (FileNotFoundException e) {
			System.out.println("Unable to open file: " + e);
			return null;
		}
	}
	//builds the event queue with the arrival events
	public void QueueArrivals(PriorityQueue<Event> eventQueue, File processFile) {
		Scanner processScanner = OpenScanner(processFile);
		int i = 0; //used to id processes
		int instructionTime;
		while(processScanner.hasNextLine()) {
			String line = processScanner.nextLine();
			Process process = new Process(i, line); //makes process with ID i
			instructionTime = process.nextInt(); //takes first number from line to be arrival time
			process.setArrival(instructionTime); //sets processes arrival time
			eventQueue.add(new Event(EventType.ARRIVE, process, instructionTime)); //creates arrival event and adds it to the queue
			i++;
		}
		processScanner.close();
	}
	//adds an event of given type to the given queue if it is not the last event that will be passed
	//if it is the last event, it will add an event of type exit no matter what the given type is
	//this is only used for block and unblock events since an arrival, exit, and timeout can never produce an exit event
	public void AddEvent(PriorityQueue<Event> queue, Event event, EventType type, int time) {
		if(event.getProcess().hasNext()) {
			queue.add(new Event(type, event.getProcess(), time));
		} else { //adds an exit event if there is nothing after this in the process
			queue.add(new Event(EventType.EXIT, event.getProcess(), time));
		}
	}
	//calculates the total service time ahead of running the process for SPN and HRRN
	public int CalcServiceTime(Process process) {
		String inst;
		int val;
		int total = 0;
		while(process.hasNext()) {
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
	public float CalcExponentialAverage(Process process) {
		float alpha = 0.8f;
		int[] T = new int[process.wordCount()/2];
		float[] S = new float[process.wordCount()/2];
		process.nextString(); //requires at least one instruction
		int val = process.nextInt();
		S[0] = (float)val; //sets first CPU service time as S0
		int n = 1;
		while(process.hasNext()) {
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
	//used by FB to check if any queues in the array are non-empty
	public boolean QueuesEmpty(PriorityQueue<Event>[] array) {
		for(PriorityQueue<Event> q : array) {
			if(!q.isEmpty()) {
				return false;
			}
		}
		return true;
	}
	//prints the processes and final statistics
	public void PrintProcesses(PriorityQueue<Process> exitQueue) {
		int numProcesses = 0;
		float meanTurnaround = 0;
		float meanNormalizedTurnaround = 0;
		float meanAverageResponse = 0;
		Process p;
		while(!exitQueue.isEmpty()) {
			p = exitQueue.poll();
			p.print();
			meanTurnaround += p.getTurnaround();
			meanNormalizedTurnaround += p.getNormalizedTurnaround();
			meanAverageResponse += p.getAverageResponse();
			numProcesses++;
		}
		meanTurnaround /= numProcesses;
		meanNormalizedTurnaround /= numProcesses;
		meanAverageResponse /= numProcesses;
		System.out.println("Mean Turnaround: " + meanTurnaround);
		System.out.println("Mean Normalized Turnaround: " + meanNormalizedTurnaround);
		System.out.println("Mean Average Response Time: " + meanAverageResponse);
	}
	
	//First Come First Serve
	public void FCFS(File processFile) {
		PriorityQueue<Event> eventQueue = new PriorityQueue<>(); //used to execute events
		PriorityQueue<Process> exitQueue = new PriorityQueue<>(); //used to print exited processes in id order
		
		//initialize event queue and processes with arrival events
		QueueArrivals(eventQueue, processFile);
		
		int clock = 0;
		Event currentEvent;
		String instruction;
		int instructionTime;
		while(!eventQueue.isEmpty()) {
			currentEvent = eventQueue.poll(); //grab next event
			
			//move clock up if the next event's time is later than the clock
			if(currentEvent.getTime() > clock) {
				clock = currentEvent.getTime();
			}
			
			//exits for an exit event since there is nothing to read
			if(currentEvent.getType() == EventType.EXIT) {
				currentEvent.getProcess().exit(currentEvent.getTime());
				exitQueue.add(currentEvent.getProcess());
				continue;
			}
			
			//get the next instruction and time value
			instruction = currentEvent.getProcess().nextString();
			instructionTime = currentEvent.getProcess().nextInt();
			
			//reads in a CPU instruction after an ARRIVE or UNBLOCK
			if(currentEvent.getType() == EventType.ARRIVE || currentEvent.getType() == EventType.UNBLOCK) {
				if(instruction.equals("CPU")) {
					//sets the process start time if it's an arrival event
					if(currentEvent.getType() == EventType.ARRIVE) {
						currentEvent.getProcess().setStart(clock);
					}
					
					currentEvent.getProcess().updateService(instructionTime);
					currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
					clock += instructionTime;
					
					//add event of type block unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.BLOCK, clock);
				} else {
					System.out.println("instruction read: " + instruction + ", CPU expected");
				}
				
			//reads in an IO instruction after a BLOCK
			} else if(currentEvent.getType() == EventType.BLOCK) {
				if(instruction.equals("IO")) {
					//add event of type unblock unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.UNBLOCK, currentEvent.getTime() + instructionTime);
				} else {
					System.out.println("instruction read: " + instruction + ", IO expected");
				}
			}
		}
		
		//print processes and overall statistics
		PrintProcesses(exitQueue);
	}
	
	//Round Robin
	public void RR(int quantum, File processFile) {
		PriorityQueue<Event> eventQueue = new PriorityQueue<>(); //used to execute events
		PriorityQueue<Process> exitQueue = new PriorityQueue<>(); //used to print exited processes in id order
		
		//initialize event queue and processes with arrival events
		QueueArrivals(eventQueue, processFile);
		
		int clock = 0;
		Event currentEvent;
		String instruction;
		int instructionTime;
		while(!eventQueue.isEmpty()) {
			currentEvent = eventQueue.poll(); //grab next event
			
			//move clock up if the next event's time is later than the clock
			if(currentEvent.getTime() > clock) {
				clock = currentEvent.getTime();
			}
			
			//exits for an exit event since there is nothing to read
			if(currentEvent.getType() == EventType.EXIT) {
				currentEvent.getProcess().exit(currentEvent.getTime());
				exitQueue.add(currentEvent.getProcess());
				continue;
			}
			
			//check if the process has been timed out, deal with that and don't read the next instruction
			if(currentEvent.getType() == EventType.TIMEOUT) {
				//deals with the time remaining since there was a timeout
				int serviceRemaining = currentEvent.getProcess().getServiceRemaining();
				currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
				if(serviceRemaining > quantum) {
					//needs another timeout
					currentEvent.getProcess().updateService(quantum);
					clock += quantum;
					currentEvent.getProcess().setServiceRemaining(serviceRemaining - quantum);
					
					eventQueue.add(new Event(EventType.TIMEOUT, currentEvent.getProcess(), clock));
				} else {
					//this is its last timeout and acts like a it received an unblock
					currentEvent.getProcess().updateService(serviceRemaining);
					clock += serviceRemaining;
					currentEvent.getProcess().setServiceRemaining(0);
					
					//add event of type block unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.BLOCK, clock);
				}
				continue;
			} else {
				//get the next instruction and time value if there is no remaining time
				instruction = currentEvent.getProcess().nextString();
				instructionTime = currentEvent.getProcess().nextInt();
			}
			
			//reads in a CPU instruction after an ARRIVE or UNBLOCK
			if(currentEvent.getType() == EventType.ARRIVE || currentEvent.getType() == EventType.UNBLOCK) {
				if(instruction.equals("CPU")) {
					//sets the process start time if it's an arrival event
					if(currentEvent.getType() == EventType.ARRIVE) {
						currentEvent.getProcess().setStart(clock);
					}
					
					currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
					if(instructionTime > quantum) {
						//starts the timeout chain
						currentEvent.getProcess().updateService(quantum);
						clock += quantum;
						currentEvent.getProcess().setServiceRemaining(instructionTime - quantum);
						
						eventQueue.add(new Event(EventType.TIMEOUT, currentEvent.getProcess(), clock));
					} else {
						//initial instruction time was not greater than the quantum, so no timeout required
						currentEvent.getProcess().updateService(instructionTime);
						clock += instructionTime;
						currentEvent.getProcess().setServiceRemaining(0);
						
						//add event of type block unless it is the last event of the process
						AddEvent(eventQueue, currentEvent, EventType.BLOCK, clock);
					}
				} else {
					System.out.println("instruction read: " + instruction + ", CPU expected");
				}
				
			//reads in an IO instruction after a BLOCK
			} else if(currentEvent.getType() == EventType.BLOCK) {
				if(instruction.equals("IO")) {
					//add event of type unblock unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.UNBLOCK, currentEvent.getTime() + instructionTime);
				} else {
					System.out.println("instruction read: " + instruction + ", IO expected");
				}
			}
		}
		
		//print processes and overall statistics
		PrintProcesses(exitQueue);
	}
	
	//Shortest Process Next
	public void SPN(boolean serviceGiven, File processFile) {
		PriorityQueue<Event> eventQueue = new PriorityQueue<>(); //used to execute events
		PriorityQueue<Process> exitQueue = new PriorityQueue<>(); //used to print exited processes in id order
		
		//initialize event queue and processes with arrival events
		QueueArrivals(eventQueue, processFile);
		
		if(serviceGiven) {
			//calculate the total service time
			for(Event e : eventQueue) {
				e.getProcess().setServiceComparable(CalcServiceTime(e.getProcess()));
			}
		} else {
			//calculate the exponential average
			for(Event e : eventQueue) {
				e.getProcess().setServiceComparable(CalcExponentialAverage(e.getProcess()));
			}
		}
		
		int clock = 0;
		Event currentEvent;
		String instruction;
		int instructionTime;
		while(!eventQueue.isEmpty()) {
			currentEvent = eventQueue.poll(); //grab next event
			
			//move clock up if the next event's time is later than the clock
			if(currentEvent.getTime() > clock) {
				clock = currentEvent.getTime();
			}
			
			//exits for an exit event since there is nothing to read
			if(currentEvent.getType() == EventType.EXIT) {
				currentEvent.getProcess().exit(currentEvent.getTime());
				exitQueue.add(currentEvent.getProcess());
				continue;
			}
			
			//get the next instruction and time value
			instruction = currentEvent.getProcess().nextString();
			instructionTime = currentEvent.getProcess().nextInt();
			
			//reads in a CPU instruction after an ARRIVE or UNBLOCK
			if(currentEvent.getType() == EventType.ARRIVE || currentEvent.getType() == EventType.UNBLOCK) {
				if(instruction.equals("CPU")) {
					//sets the process start time if it's an arrival event
					if(currentEvent.getType() == EventType.ARRIVE) {
						currentEvent.getProcess().setStart(clock);
					}
					
					currentEvent.getProcess().updateService(instructionTime);
					currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
					clock += instructionTime;
					
					//add event of type block unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.BLOCK, clock);
				} else {
					System.out.println("instruction read: " + instruction + ", CPU expected");
				}
				
			//reads in an IO instruction after a BLOCK
			} else if(currentEvent.getType() == EventType.BLOCK) {
				if(instruction.equals("IO")) {
					//add event of type unblock unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.UNBLOCK, currentEvent.getTime() + instructionTime);
				} else {
					System.out.println("instruction read: " + instruction + ", IO expected");
				}
			}
		}
		
		//print processes and overall statistics
		PrintProcesses(exitQueue);
	}
	
	//Highest Response Ratio Next
	public void HRRN(boolean serviceGiven, File processFile) {
		PriorityQueue<Event> eventQueue = new PriorityQueue<>(); //used to execute events
		PriorityQueue<Process> exitQueue = new PriorityQueue<>(); //used to print exited processes in id order
		
		//initialize event queue and processes with arrival events
		QueueArrivals(eventQueue, processFile);
		
		if(serviceGiven) {
			//calculate the total service time
			for(Event e : eventQueue) {
				e.getProcess().setExpectedService(CalcServiceTime(e.getProcess()));
				e.getProcess().setServiceComparable(1.0f);
			}
		} else {
			//calculate the exponential average
			for(Event e : eventQueue) {
				e.getProcess().setExpectedService(CalcExponentialAverage(e.getProcess()));
				e.getProcess().setServiceComparable(1.0f);
			}
		}
		
		int clock = 0;
		Event currentEvent;
		String instruction;
		int instructionTime;
		while(!eventQueue.isEmpty()) {
			currentEvent = eventQueue.poll(); //grab next event
			
			//move clock up if the next event's time is later than the clock
			if(currentEvent.getTime() > clock) {
				clock = currentEvent.getTime();
			}
			
			//exits for an exit event since there is nothing to read
			if(currentEvent.getType() == EventType.EXIT) {
				currentEvent.getProcess().exit(currentEvent.getTime());
				exitQueue.add(currentEvent.getProcess());
				continue;
			}
			
			//get the next instruction and time value
			instruction = currentEvent.getProcess().nextString();
			instructionTime = currentEvent.getProcess().nextInt();
			
			//reads in a CPU instruction after an ARRIVE or UNBLOCK
			if(currentEvent.getType() == EventType.ARRIVE || currentEvent.getType() == EventType.UNBLOCK) {
				if(instruction.equals("CPU")) {
					//sets the process start time if it's an arrival event
					if(currentEvent.getType() == EventType.ARRIVE) {
						currentEvent.getProcess().setStart(clock);
					}
					
					currentEvent.getProcess().updateService(instructionTime);
					currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
					clock += instructionTime;
					
					//updates the response ratio before it gets blocked and moved back to the ready queue
					int w = currentEvent.getProcess().getWaited();
					float s = currentEvent.getProcess().getExpectedService();
					currentEvent.getProcess().setServiceComparable((w+s)/s);
					
					//add event of type block unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.BLOCK, clock);
				} else {
					System.out.println("instruction read: " + instruction + ", CPU expected");
				}
				
			//reads in an IO instruction after a BLOCK
			} else if(currentEvent.getType() == EventType.BLOCK) {
				if(instruction.equals("IO")) {
					//add event of type unblock unless it is the last event of the process
					AddEvent(eventQueue, currentEvent, EventType.UNBLOCK, currentEvent.getTime() + instructionTime);
				} else {
					System.out.println("instruction read: " + instruction + ", IO expected");
				}
			}
		}
		
		//print processes and overall statistics
		PrintProcesses(exitQueue);
	}
	
	//Feedback
	public void FB(int quantum, int numPriorities, File processFile) {
		@SuppressWarnings("unchecked")
		PriorityQueue<Event>[] queues = new PriorityQueue[numPriorities];
		for(int i=0; i<queues.length; i++) {
			queues[i] = new PriorityQueue<>();
		}
		PriorityQueue<Process> exitQueue = new PriorityQueue<>(); //used to print exited processes in id order
		
		//initialize event queue and processes with arrival events
		QueueArrivals(queues[0], processFile);
		
		int clock = 0;
		Event currentEvent;
		String instruction;
		int instructionTime;
		while(!QueuesEmpty(queues)) {
			//grab next event from the highest non-empty queue
			currentEvent = null;
			for(PriorityQueue<Event> q : queues) {
				if(!q.isEmpty()) {
					currentEvent = q.poll();
					break;
				}
			}
			//set the queue that the process is tied to
			//gets updated when a timeout or unblock event is added
			//since that is when a process would go into the ready queue
			int feedbackQueue = currentEvent.getProcess().getFeedbackQueue();
			
			//move clock up if the next event's time is later than the clock
			if(currentEvent.getTime() > clock) {
				clock = currentEvent.getTime();
			}
			
			//exits for an exit event since there is nothing to read
			if(currentEvent.getType() == EventType.EXIT) {
				currentEvent.getProcess().exit(currentEvent.getTime());
				exitQueue.add(currentEvent.getProcess());
				continue;
			}
			
			//check if the process has been timed out, deal with that and don't read the next instruction
			if(currentEvent.getType() == EventType.TIMEOUT) {
				//deals with the time remaining since there was a timeout
				int serviceRemaining = currentEvent.getProcess().getServiceRemaining();
				currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
				if(serviceRemaining > quantum) {
					//needs another timeout
					currentEvent.getProcess().updateService(quantum);
					clock += quantum;
					currentEvent.getProcess().setServiceRemaining(serviceRemaining - quantum);
					
					feedbackQueue = currentEvent.getProcess().updateFeedbackQueue(numPriorities);
					queues[feedbackQueue].add(new Event(EventType.TIMEOUT, currentEvent.getProcess(), clock));
				} else {
					//this is its last timeout and acts like a it received an unblock
					currentEvent.getProcess().updateService(serviceRemaining);
					clock += serviceRemaining;
					currentEvent.getProcess().setServiceRemaining(0);
					
					//add event of type block unless it is the last event of the process
					AddEvent(queues[feedbackQueue], currentEvent, EventType.BLOCK, clock);
				}
				continue;
			} else {
				//get the next instruction and time value if there is no remaining time
				instruction = currentEvent.getProcess().nextString();
				instructionTime = currentEvent.getProcess().nextInt();
			}
			
			//reads in a CPU instruction after an ARRIVE or UNBLOCK
			if(currentEvent.getType() == EventType.ARRIVE || currentEvent.getType() == EventType.UNBLOCK) {
				if(instruction.equals("CPU")) {
					//sets the process start time if it's an arrival event
					if(currentEvent.getType() == EventType.ARRIVE) {
						currentEvent.getProcess().setStart(clock);
					}
					
					currentEvent.getProcess().updateWait(clock - currentEvent.getTime());
					if(instructionTime > quantum) {
						//starts the timeout chain
						currentEvent.getProcess().updateService(quantum);
						clock += quantum;
						currentEvent.getProcess().setServiceRemaining(instructionTime - quantum);
						
						feedbackQueue = currentEvent.getProcess().updateFeedbackQueue(numPriorities);
						queues[feedbackQueue].add(new Event(EventType.TIMEOUT, currentEvent.getProcess(), clock));
					} else {
						//initial instruction time was not greater than the quantum, so no timeout required
						currentEvent.getProcess().updateService(instructionTime);
						clock += instructionTime;
						currentEvent.getProcess().setServiceRemaining(0);
						
						//add event of type block unless it is the last event of the process
						AddEvent(queues[feedbackQueue], currentEvent, EventType.BLOCK, clock);
					}
				} else {
					System.out.println("instruction read: " + instruction + ", CPU expected");
				}
				
			//reads in an IO instruction after a BLOCK
			} else if(currentEvent.getType() == EventType.BLOCK) {
				if(instruction.equals("IO")) {
					if(currentEvent.getProcess().hasNext()) {
						//this moving down in the queues prevents this from being replaced by the AddEvent function
						feedbackQueue = currentEvent.getProcess().updateFeedbackQueue(numPriorities);
						queues[feedbackQueue].add(new Event(EventType.UNBLOCK, currentEvent.getProcess(), currentEvent.getTime()+instructionTime));
					} else { //adds an exit event if there is nothing after this in the process
						queues[feedbackQueue].add(new Event(EventType.EXIT, currentEvent.getProcess(), currentEvent.getTime()+instructionTime));
					}
				} else {
					System.out.println("instruction read: " + instruction + ", IO expected");
				}
			}
		}
		
		//print processes and overall statistics
		PrintProcesses(exitQueue);
	}
	
	static public void main(String[] args) {
		//assigns files from command line
		File algorithmFile = new File(args[0]);
		File processFile = new File(args[1]);
		
		//read the algorithm file and run the appropriate function to simulate the scheduler
		try {
		Scanner algorithmScanner = new Scanner (algorithmFile);
		String algorithm = algorithmScanner.nextLine();
		Scheduler scheduler = new Scheduler();
		
		//run First Come First Serve
		if(algorithm.equals("FCFS")) {
			scheduler.FCFS(processFile);
				
		//run Round Robin
		} else if(algorithm.equals("RR")) {
			String line = algorithmScanner.nextLine();
			int quantum = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			scheduler.RR(quantum, processFile);
				
		//run Shortest Process Next
		} else if(algorithm.equals("SPN")) {
			String line = algorithmScanner.nextLine();
			String serviceGiven = line.substring(line.lastIndexOf('=')+1);
			if(serviceGiven.equals("true") || serviceGiven.equals("false")) {
				boolean serviceGivenB = Boolean.parseBoolean(serviceGiven);
				scheduler.SPN(serviceGivenB, processFile);
			} else {
				System.out.println("Invalid service_given value in algorithmFile: " + serviceGiven);
			}
			
		//run Highest Response Ratio Next
		} else if(algorithm.equals("HRRN")) {
			String line = algorithmScanner.nextLine();
			String serviceGiven = line.substring(line.lastIndexOf('=')+1);
			if(serviceGiven.equals("true") || serviceGiven.equals("false")) {
				boolean serviceGivenB = Boolean.parseBoolean(serviceGiven);
				scheduler.HRRN(serviceGivenB, processFile);
			} else {
				System.out.println("Invalid service_given value in algorithmFile: " + serviceGiven);
			}
			
		//run Feedback
		} else if(algorithm.equals("FEEDBACK")) {
			String line = algorithmScanner.nextLine();
			int quantum = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			line = algorithmScanner.nextLine();
			int numPriorities = Integer.parseInt(line.substring(line.lastIndexOf('=')+1));
			scheduler.FB(quantum, numPriorities, processFile);
			
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