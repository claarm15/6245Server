import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * The server class is for my SE6245 class.
 * The main purpose is to hold accept chat messages and typing
 * messages and return them. 
 * 
 * The server uses generics for keeping track of each of the sessions.
 * Each connection starts a new Handler saving the writer in a HashMap
 * of PrintWriters.  Where it is such a small file (120ish lines without
 * comments and imports), I chose not to break it up into multiple classes.
 * The overhead of extra classes, files, and making sure elements are 
 * synchronized across varied elements loses to one tight file in this case.
 *   
 *  NOTE: JUnit Test Cases are in the Client, not the server.   
 *   
 * Please also note:  I did a lot of research on different methods people used
 * to create a chat clients.  I used the data types and the naming conventions
 * from one on http://cs.lmu.edu/~ray/notes/javanetexamples/ named A Multi-User
 * Chat Application.  It wouldn't take much comparision to find out they are not
 * overly similar still, but it is only appropriate to share my research sources
 * when they are used. 
 *   
 * @author Michael Claar
 *
 */
public class server {
	//Chose an unpopular port number that was semi-rememberable.
	private static final int PORT = 9876;
	
	private static HashSet<String> names = new HashSet<String>();
	private static HashMap<String, Long> typingNames = new HashMap<String, Long>();
	private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
	/**
	 * Main method fires up the server.  I didn't see any need to put a GUI
	 * on my server because its most likely to run on a headless server somewhere.
	 * 
	 * Handler method and Run are only accessible through the main class limiting access
	 * even though the run class is public (part of a private static class).    
	 * 
	 * Preconditions: None, (except maybe an environment that run its)
	 * Invariants: Each handler is a socket that opens and closes, very little can be immutable here.
	 * Post Condition: A terminated program which isn't intended and has no exit function built in.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("Chat Server Started.");
		ServerSocket listener = new ServerSocket(PORT);
		try {
			while (true) {
				new Handler(listener.accept()).start();
			}
		} finally {
			listener.close();
		}
	}

	private static class Handler extends Thread {
		private String name;
		private Socket socket;
		private BufferedReader in;
		private PrintWriter out;
		private ScheduledExecutorService executor;
		public Handler(Socket socket) {
			this.socket = socket;
			startScheduler();
		}

		public void run() {
			try {
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				while (true) {
					out.println("SUBMITNAME");
					name = in.readLine();
					if (name == null || name.equals("")) {
						continue;
					}
					synchronized (names) {
						if (!names.contains(name)) {
							names.add(name);
							break;
						}
					}
				}
				System.out.println("User added: " + name);
				out.println("NAMEACCEPTED");
				writers.add(out);
				while (true) {
					String input = in.readLine();
					if (input == null || input.equals("")) {
						return;
					}
					if (input.contains("MESSAGE_")) {
						for (PrintWriter writer : writers) {
							writer.println("MESSAGE " + name + ": " + input.substring(8));
						}
					} else if (input.contains("TYPING_")) {
						for (PrintWriter writer : writers) {
							synchronized(typingNames) {
								if (!typingNames.containsKey(name)) {
									//Storing seconds from epoch in there...
									typingNames.put(name, (System.currentTimeMillis() + 5000));
								}
							} 
						}
					}
				}
			} catch (IOException e) {
				System.out.println("IOexception: " + e);
			} finally {
				if (name != null) {
					names.remove(name);
				}
				if (out != null) {
					writers.remove(out);
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void startScheduler() {
			Runnable sendTypingNames = new Runnable() {
			    public void run() {
			        synchronized (typingNames) {
			        	
			        	long comparisonTime = System.currentTimeMillis();
			        	String typingNameList = "";
			        	
			        	for (String key : typingNames.keySet()) {
			        		if (typingNames.get(key) - comparisonTime > 0) {
			        			typingNameList = typingNameList + key + " ";
			        		} else {
			        			typingNames.remove(key);
			        		}
			        	}
			        	for (PrintWriter writer : writers) {
			        		writer.println("TYPING_" + typingNameList);
			        	}
			        }
			    }
			};
			if (executor == null) {
				executor = Executors.newScheduledThreadPool(1);
			}
			executor.scheduleAtFixedRate(sendTypingNames, 1, 1, TimeUnit.SECONDS);
		}
	}
}