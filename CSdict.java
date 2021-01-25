
import java.lang.System;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;
import java.io.BufferedReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CSdict {
    static final int MAX_LEN = 255;
    static Boolean debugOn = false;
    
    private static final int PERMITTED_ARGUMENT_COUNT = 1;
    private static String command;
	private static String[] arguments;
	private static String dict = "*";

	// Print if incorrect number of arguments.
	private static void argNumberError() {
		System.err.println("901 Incorrect number of arguments.");
	}
	// Print if invlid argument type(s).
	private static void invalidArgError() {
		System.err.println("902 Invalid argument.");
	}
	// Print if unexpected command at time.
	private static void unexpectedCommandError() {
		System.err.println("903 Supplied command not expected at this time.");
	}

	// Resets dictionary to default.
	private static void resetDict(){
		CSdict.dict = "*";
	}
	// Sets dictionary to arg.
	private static void setDict(String arg){
		CSdict.dict = arg;
	}

	// Combine arguments into single string to support multiple words.
	private static String combineWords() {
		if (arguments.length == 1) {
			return arguments[0];
		} else {
			StringBuilder sb = new StringBuilder();
			boolean firstWord = true;
			for (String s: arguments) {
				if (firstWord) {
					firstWord = false;
				} else {
					sb.append(" ");
				}
				sb.append(s);
			}
			return sb.toString();
		}
	}
	
	// Print command if in debug mode.
	private static void printIfDebug(String command) {
		if (debugOn) {
			System.out.println("> " + command);
		}
	}

	// Print statuses if in debug mode.
	private static void printStatusIfDebug(String status) {
		if (debugOn) {
			System.out.println("<-- " + status);
		}
	}

    public static void main(String [] args) {
        byte cmdString[] = new byte[MAX_LEN];
		int len;
		// Verify command line arguments

        if (args.length == PERMITTED_ARGUMENT_COUNT) {
            debugOn = args[0].equals("-d");
            if (debugOn) {
                System.out.println("Debugging output enabled");
            } else {
                System.out.println("997 Invalid command line option - Only -d is allowed");
                return;
            }
        } else if (args.length > PERMITTED_ARGUMENT_COUNT) {
            System.out.println("996 Too many command line options - Only -d is allowed");
            return;
        }

		Scanner scanner = new Scanner(System.in);
		Socket socket = new Socket();
		PrintWriter out = null;
		BufferedReader in = null;

		while(true) {
			try {
			System.out.print("csdict> ");
			// Take in user input.
			String inputString = scanner.nextLine();
			
			// Split the string into words
			String[] inputs = inputString.trim().split("( |\t)+");
			// Set the command
			command = inputs[0].toLowerCase().trim();
			// Remainder of the inputs is the arguments. 
			arguments = Arrays.copyOfRange(inputs, 1, inputs.length);
			len = arguments.length;
			
			try {
				// Ignore commands that start with "#".
				if (command.startsWith("#")) {
					continue;
				}
				switch(command) {
					// Ignore empty commands.
					case "":
						break;
					case "quit":
					// Attempt to close socket and exit program.
						if(len != 0) {
							argNumberError();
						} else {
							if (socket.isConnected() && !socket.isClosed()) {
								try {
									out.println("QUIT");
									printIfDebug("QUIT");
									socket.close();
								} catch (IOException e) {
								}
							}
							scanner.close();
							System.exit(0);
						}
						break;
					case "dict":
					// Show all current available dictionaries.
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else if(len != 0) {
							argNumberError();
						} else {
							out.println("SHOW DB");
							printIfDebug("SHOW DB");
							String result = in.readLine();
							printStatusIfDebug(result);
							if (!result.startsWith("110")) {
								throw new Exception("No databases present");
							}
							result = in.readLine();
							// Prints all dictionaries available.
							while (!result.startsWith("250")) {
								System.out.println(result);
								result = in.readLine();
							}
							printStatusIfDebug(result);
						}
						break;
					case "open":
					// Open a connection.
						if(socket.isConnected() && !socket.isClosed()) {
							unexpectedCommandError();
						} else if(len != 2) {
							argNumberError();
						} else {
							try {
								// Will timeout if socket does not connect in 30 seconds.
								socket.connect(new InetSocketAddress(arguments[0], Integer.parseInt(arguments[1])), 30*1000);
								socket.setSoTimeout(30*1000);
								resetDict();

								out = new PrintWriter(socket.getOutputStream(), true);
								in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
								String status = in.readLine();
								printStatusIfDebug(status);
								// Throw exception if not successful connection to DICT server.
								if (!status.startsWith("220")) {
									throw new Exception();
								}
							} catch (NumberFormatException e) {
								invalidArgError();
							} catch (Exception e) {
								socket = new Socket();
								System.out.println(String.format("920 Control connection to %s on port %s failed to open.", arguments[0], arguments[1]));
							}
						}
						break;
					case "close":
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else {
							// Closes the current connection.
							out.println("QUIT");
							printIfDebug("QUIT");
							resetDict();
							socket.close();
							socket = new Socket();
						}
						break;
					case "set":
					// Set the current dictionary.
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else if (len != 1) {
							argNumberError();
						} else {
							setDict(arguments[0]);
						}
						break;
					case "match":
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else if (len < 1) {
							argNumberError();
						} else {
							String word = combineWords();
							String command = String.format("MATCH %s exact \"%s\"", dict, word);
							out.println(command);
							printIfDebug(command);
							String result = in.readLine();
							printStatusIfDebug(result);
							if (result.startsWith("152")) {
								// Print results if there are matches.
								result = in.readLine();
								while (!result.startsWith("250")) {
									System.out.println(result);
									result = in.readLine();
								}
								printStatusIfDebug(result);
							} else if (result.startsWith("552")) {
								// Print if no matches.
								System.out.println("*****No matching word(s) found*****");
							} else {
								throw new Exception("Invalid database, use 'dict' for list of databases");
							}
						}
						break;
					case "define":
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else if (len < 1) {
							argNumberError();
						} else {
							String word = combineWords();
							String command = String.format("DEFINE %s \"%s\"", dict, word);
							out.println(command);
							printIfDebug(command);
							String result = in.readLine();
							printStatusIfDebug(result);
							// Prints definitions for a given word if any are found.
							if (result.startsWith("150")) {
								result = in.readLine();
								// Prints lines one at a time until given a line with status message 250, inidicating end of data stream.
								while (!result.startsWith("250")) {
									// Format definition response to desired format.
									if (result.startsWith("151")) {
										printStatusIfDebug(result);
										Pattern regex = Pattern.compile("151(.*)?\"(.*)?\"(.*)?\"(.*)\"");
										Matcher match = regex.matcher(result);
										if (match.find()) {
											result = "@ \"" + match.group(4) + "\"";
										}
									}
									System.out.println(result);
									result = in.readLine();
								}
								printStatusIfDebug(result);
							// 552: No definitions were found and will try to match using server default matching strategy.
							} else if (result.startsWith("552")) {
								System.out.println("***No definition found***");
								String match = String.format("MATCH %s . \"%s\"", dict, word);
								out.println(match);
								printIfDebug(match);
								result = in.readLine();
								printStatusIfDebug(result);
								if (result.startsWith("152")) {
									// Print matches if they are found.
									result = in.readLine();
									while (!result.startsWith("250")) {
										System.out.println(result);
										result = in.readLine();
									}
									printStatusIfDebug(result);
								} else if (result.startsWith("552")) {
									System.out.println("****No matches found****");
								}
							} else {
								throw new Exception("Invalid database, use 'dict' for list of databases");
							}
						}
						break;
					case "prefixmatch":
						if(!socket.isConnected() || socket.isClosed()) {
							unexpectedCommandError();
						} else if (len < 1) {
							argNumberError();
						} else {
							String word = combineWords();
							String command = String.format("MATCH %s prefix \"%s\"", dict, word);
							out.println(command);
							printIfDebug(command);
							String result = in.readLine();
							printStatusIfDebug(result);
							if (result.startsWith("152")) {
								// Print results if there are matches.
								result = in.readLine();
								while (!result.startsWith("250")) {
									System.out.println(result);
									result = in.readLine();
								}
								printStatusIfDebug(result);
							} else if (result.startsWith("552")) {
								// Print if no matches.
								System.out.println("***No matching word(s) found****");
							} else {
								throw new Exception("Invalid database, use 'dict' for list of databases");
							}
						}
						break;
					default:
					System.err.println("900 Invalid command.");
				}
			} catch (IOException e) {
				if (!socket.isClosed()) {
					System.err.println("925 Control connection I/O error, closing control connection.");
					// Attempt to close and reset socket.
					try {
						socket.close();
					} catch (IOException ioe) {
					// Do not want to throw another IOException, just create destroy the old socket.
					}
					socket = new Socket();
				} else {
					System.err.println(String.format("999 Processing error. %s.", e.getMessage()));
				}
			}
		} catch (NoSuchElementException e) {
			// Exit if end-of-file in stdin.
			if (socket.isConnected() && !socket.isClosed()) {
				try {
					out.println("QUIT");
					printIfDebug("QUIT");
					socket.close();
				} catch (IOException ioe) {
				// Do not want to throw another IOException, would just want to exit here.
				}
			}
			System.exit(0);
		} catch (IOException exception) {
			System.err.println("998 Input error while reading commands, terminating.");
			System.exit(-1);
		} catch (Exception e) {
			System.err.println(String.format("999 Processing error. %s.", e.getMessage()));
		}
		
		}
    }
}
    
    
