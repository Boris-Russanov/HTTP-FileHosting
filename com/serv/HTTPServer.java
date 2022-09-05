package com.serv;

import com.serv.FNode;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class HTTPServer implements Runnable { 
	static final File ROOT = new File(".");
	static final File WEB_ROOT = new File("./pages");
	static final File DATA_ROOT = new File("./data");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	// port to listen connection
	static final int PORT = 3000;
	public int fileCount = 0;
	public int counter = 0;
	static FNode fileList = null;
	
	// verbose mode
	static final boolean verbose = true;
	
	// Client Connection via Socket Class
	private Socket connect;
	
	public HTTPServer(Socket c) {
		connect = c;
	}
	
	public static void main(String[] args) {
		try {
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port: " + PORT);
			
			//listen until user halts server execution
			while (true) {
				HTTPServer myServer = new HTTPServer(serverConnect.accept());
				/*
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					System.out.println("interrupted...?");
					Thread.currentThread().interrupt();
				}
				*/
				
				if (verbose) {
					System.out.println("Connecton opened. (" + new Date() + ")");
				}
				
				// create dedicated thread to manage the client connection
				Thread thread = new Thread(myServer);
				thread.start();
			}
			
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	@Override
	public void run() {
		// we manage our particular client connection
		BufferedReader in = null; PrintWriter out = null; PrintWriter jsonOut = null; BufferedOutputStream dataOut = null;
		String fileRequested = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		InputStream is1 = null;
		
		try {
			inputStream = connect.getInputStream();
			outputStream = connect.getOutputStream();
			connect.setSoTimeout(10000);	//10 sec timeout on reads
			//read characters from the client via input stream on the socket (changed in favor of scanner)
			//in = new BufferedReader(new InputStreamReader(inputStream));
			//get character output stream to client (for headers)
			out = new PrintWriter(outputStream);
			// get binary output stream to client (for requested data)
			dataOut = new BufferedOutputStream(outputStream);
			//json out
			jsonOut = new PrintWriter(outputStream);
			
			//copying stream...
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			/*wip
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			inputStream.transferTo(baos);
			System.out.println("still reading?");
			//copied stream:
			is1 = new ByteArrayInputStream(baos.toByteArray());
			*/
			
			//get header and body reading all.
			String s = read(inputStream, baos);
			Scanner scanner = new Scanner(s);
			System.out.println("got: " + s);
			//scanner.reset();
			//scanner = new Scanner(s);
			
			
			//get first line of the request from the client
			String input = scanner.nextLine();
			//we parse the request with a string tokenizer
			StringTokenizer parse = new StringTokenizer(input);
			System.out.println("Input was: " + input);
			String method = parse.nextToken().toUpperCase();
			System.out.println("method was: " + method);
			// we get file requested
			fileRequested = parse.nextToken().toLowerCase();
			System.out.println("req/file was: " + fileRequested);
			
			
			if (!method.equals("POST") && !method.equals("GET") && !method.equals("HEAD")) {
				if (verbose) {
					System.out.println("501 Not Implemented : " + method + " method.");
				}
				
				// we return the not supported file to the client
				File file = new File(WEB_ROOT, METHOD_NOT_SUPPORTED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				//read content to return to client
				byte[] fileData = readFileData(file, fileLength);
					
				// we send HTTP Headers with data to client
				out.println("HTTP/1.1 501 Not Implemented");
				out.println("Server: JserveMe : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println();
				out.flush();
				// file
				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				
			} else {
				// GET or HEAD method
				//System.out.println(fileRequested);
				if (fileRequested.equals("/reqval")) {	//sudo xhr request and sent json response.
					System.out.println(fileRequested);
					if (method.equals("GET")) { // GET method so we return content
						// send HTTP Headers
						out.println("HTTP/1.1 200 OK");
						out.println("Server: JserveMe : 1.0");
						out.println("Date: " + new Date());
						out.println();
						out.flush();
						
						//send json.
						jsonOut.println("{\"name\":\"xyz\",\"age\":\"20\"}");
						jsonOut.println();
						jsonOut.flush();
					}
				} else if (fileRequested.startsWith("/getfilelist:")) {
					if (method.equals("POST")) { // GET method so we return content
						// send HTTP Headers
						out.println("HTTP/1.1 200 OK");
						out.println("Server: JserveMe : 1.0");
						out.println("Date: " + new Date());
						out.println();
						out.flush();
						String fod = fileRequested.substring(13, fileRequested.length());
						//System.out.println("reading folder: " + fod);
						this.fileCount = 0;
						this.fileList = ReadFilesInDir(fod);
						//printFNode(fileList);
						this.counter = 0;
						//printFNode(this.fileList);
						String JsonParsedTree = "";
						JsonParsedTree += FNodetoJson(this.fileList);
						//System.out.println(JsonParsedTree);
						jsonOut.println(JsonParsedTree);
						jsonOut.println();
						jsonOut.flush();
					}
				} else if (fileRequested.contains("/getfile:")) {	//if this matches, it sends any file via path
					System.out.println("in req for file.....");
					String f = fileRequested.substring(9, fileRequested.length());
					//System.out.println("here " + f);
					String dir = ROOT + "/data";
					File file = new File(dir, f);
					int fileLength = (int) file.length();
					//String content = getContentType(fileRequested);
					if (method.equals("POST") || method.equals("GET")) { // GET method so we return content
						byte[] fileData = readFileData(file, fileLength);
						// send HTTP Headers
						out.println("HTTP/1.1 200 OK");
						out.println("Server: JserveMe : 1.0");
						out.println("Date: " + new Date());
						out.println();
						out.flush();
						
						//send file.
						dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
					}
				} else if (fileRequested.contains("/getfileindex:")) {	//if this matches, it sends any file for download
					//System.out.println("in req for file index!!");
					String f = fileRequested.substring(14, fileRequested.length());
					int searchIndex = Integer.parseInt(f);
					String path = searchFNodes(this.fileList, searchIndex);
					//System.out.println("here " + path);
					//if null throw no file err.
					if (path == null) {
						System.out.println("nulled");
						throw new FileNotFoundException();
					}
					File file = new File(path);
					//System.out.println("trying to send " + file.getName());
					//System.out.println("including path " + file.getAbsolutePath());
					int fileLength = (int) file.length();
					//String content = getContentType(fileRequested);
					if (method.equals("POST") || method.equals("GET")) { // GET method so we return content
						//System.out.println("if folder, readFileData fails");
						byte[] fileData = readFileData(file, fileLength);
						// send HTTP Headers
						out.println("HTTP/1.1 200 OK");
						out.println("Server: JserveMe : 1.0");
						out.println("Date: " + new Date());
						out.println();
						out.flush();
						
						//send file.
						dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
					}
				} else if (fileRequested.contains("/savefile:")) {
					String f = fileRequested.substring(10, fileRequested.length());
					int searchIndex = Integer.parseInt(f);
					String path = searchFNodes(this.fileList, searchIndex);
					if (path == null) {
						System.out.println("nulled");
						throw new FileNotFoundException();
					}
					File file = new File(path);
					if (!file.isDirectory()) {
						throw new FileNotFoundException();	//not correct exception but close enough.
					}
					int fileLength = (int) file.length();
					//String content = getContentType(fileRequested);
					if (method.equals("POST") || method.equals("GET")) { // GET method so we return content
						readBodyToFile(s, baos, inputStream);
						//System.out.println("if folder, readFileData fails");
						//byte[] fileData = readFileData(file, fileLength);
						// send HTTP Headers
						out.println("HTTP/1.1 200 OK");
						out.println("Server: JserveMe : 1.0");
						out.println("Date: " + new Date());
						out.println();
						out.flush();
						
						//nothing to send back.
						//dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
						System.out.println("DONE!!!!!!!!!!!!!!!!!!!!!!!");
					}
				} else {
					if (fileRequested.endsWith("/")) {
						fileRequested += DEFAULT_FILE;
					} else {
						fileRequested = fileRequested.substring(1, fileRequested.length());
					}
					try {
						File file = new File(WEB_ROOT, fileRequested);
						int fileLength = (int) file.length();
						String content = getContentType(fileRequested);
						
						if (method.equals("GET")) { // GET method so we return content
							byte[] fileData = readFileData(file, fileLength);
							
							// send HTTP Headers
							out.println("HTTP/1.1 200 OK");
							out.println("Server: JserveMe : 1.0");
							out.println("Date: " + new Date());
							out.println("Content-type: " + content);
							out.println("Content-length: " + fileLength);
							out.println(); // newline between headers and content.
							out.flush(); // flush character output stream buffer
							
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
						}
						
						if (verbose) {
							System.out.println("File " + fileRequested + " of type " + content + " returned");
						}
					} catch (FileNotFoundException fnfe) {
						try {
							fileNotFound(out, dataOut, fileRequested);
						} catch (IOException ioe) {
							System.err.println("Error with file not found exception : " + ioe.getMessage());
						}
					}
				}
			}
		} catch (FileNotFoundException fnfe) {
			try {
				fileNotFound(out, dataOut, fileRequested);
			} catch (IOException ioe) {
				System.err.println("Error with file not found exception : " + ioe.getMessage());
			}
		} catch (SocketTimeoutException s) {
			System.err.println("Socket timed out for more than 10 seconds!");
		} catch (IOException ioe) {
			System.err.println("Server error : " + ioe);
		} finally {
			try {
				//in.close();
				//is1.close();
				out.close();
				jsonOut.close();
				dataOut.close();
				connect.close(); //close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
		
		
	}
	
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) 
				fileIn.close();
		}
		
		return fileData;
	}
	
	//notes since the crazy why doesn't my whole file get sent/not read? still unsure... It might be it doesn't read from inputstream fully.
	//it shouldn't be that the file partically sent to the server. But I'm so lost. on localhost, i can send files but any other computer, file sends partially, or not at all.
	//9/4 finally figured it out!!!!!!! It is bc the inputstream has not finished, need to get inputstream again until completely done.
	private void readBodyToFile(String strBlk, ByteArrayOutputStream baos, InputStream inpS) throws IOException {	//ByteArrayInputStream baos
		//InputStream is = new ByteArrayInputStream(baos.toByteArray());
		//String s = is.readLine();
		Scanner scanner = new Scanner(strBlk);
		boolean breakpoint = false;
		String body = "";
		/* //maybe b4 but not now, we send file raw no webkit
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.contains("------WebKitFormBoundary") && breakpoint == false) {
				//System.out.println("Found break!");
				breakpoint = true;
				if (scanner.hasNextLine()) {
					//scanner.nextLine();
					scanner.nextLine();
					scanner.nextLine();
				}
				continue;
			} else if (line.contains("------WebKitFormBoundary") && breakpoint == true) {
				break;
			}
			if (breakpoint == true) {
				body += line;
			}
		}
		*/
		//System.out.println("\n\n\n\n\n\n\nbody text: " + body);
		//int newLineIndex = body.indexOf("\n");
		//String name = body.substring(0, newLineIndex);	//first line
		//body = body.substring(newLineIndex + 1);
		//issue is most likely because I put the stream into a string so data was lost or not encoded correctly...need to solve l8r
		
		InputStream is = new ByteArrayInputStream(baos.toByteArray()); //new ByteArrayInputStream(strBlk.getBytes());
		///*
		StringBuilder result = new StringBuilder();
		StringBuilder result2 = new StringBuilder();
		long index1 = 0;
		boolean newline = false;
		String fileName = "tmp";
		String fileType = "";
		int lengthBody = 0;
		while (is.available() > 0) {
			byte[] b = new byte[1];
			//b = (byte) is.read();
			int didRead = is.read(b, 0, 1);
			if (didRead == 0) {
				continue;
			}
			result.append((char) b[0]);
			result2.append((char) b[0]);
			if (!newline) {
				if (((char) b[0]) == '\n' && result.toString().isBlank()) {	//checks if we broke from header to body. result.toString().isBlank()	result.toString().contains("------WebKitFormBoundary")
					System.out.println("bp point!!!!!!");
					result.setLength(0);
					newline = true;
					System.out.println("\n\n\n\n\nread until: " + result2 + "\n\n\n\n\n");
					result2.setLength(0);
					break;
				} else if ((char) b[0] == '\n') {
					System.out.println("checking " + result.toString());
					if (result.toString().contains("File-Name:")) {
						int indexSpace = result.toString().indexOf(' ');
						String str = result.toString();
						fileName = str.substring(indexSpace+1, result.length()-2);	//-2 for newline we appended...
					}//*/
					if (result.toString().contains("Content-Length:")) {
						int indexSpace = result.toString().indexOf(' ');
						String str = result.toString();
						lengthBody = Integer.parseInt(str.substring(indexSpace+1, result.length()-2));	//-2 for newline we appended...
					}
					result.setLength(0);
				}
			} else {
				/*
				if ((char) b[0] == '\n' && result.toString().contains("NAME:")) {	//result.toString().contains("filename=")
					int indexSpace = result.toString().indexOf(':');
					String str = result.toString();
					fileName = str.substring(indexSpace+1, result.length()-1);	//-2 for newline we appended
					break;
				}*/
				if ((char) b[0] == '\n'){
					result.setLength(0);
				}
			}
		}

		System.out.println("name: " + fileName + "|");
		int lastdot = fileName.lastIndexOf('.');
		//String name = fileName.substring(0, lastdot);
		//fileType = fileName.substring(lastdot, fileName.length());
		//System.out.println(fileType);
		//*/
		System.out.println("here");
		File file = new File(DATA_ROOT, fileName);	//DATA_ROOT.getAbsolutePath() + '/' + name + fileType);
		System.out.println("alive?");
		//System.out.println("is it : " + istrue);
		System.out.println("path is: " + file.getAbsolutePath());
        try {
			boolean istrue = file.createNewFile();
		} catch(Exception e) {
			System.out.println(e);
		}

		///*
		long totalRead = 0;
		try (OutputStream output = new FileOutputStream(file, false)) {
			byte[] data = new byte[16384];
			int nRead = 0;
			while ((nRead = is.read(data, 0, data.length)) != -1) {
				output.write(data, 0, nRead);
				totalRead += nRead;
			}
            //is.transferTo(output);
			if (totalRead < lengthBody) {	//might still have data being written to it, read rest.
				System.out.println("still have some stuff to read || read " + totalRead + " out of " + lengthBody + ".");
				while (totalRead < lengthBody) {
					//data = new byte[16384];
					//int nRead = 0;
					while (((nRead = is.read(data, 0, data.length)) != -1)) {	//|| totalRead < lengthBody	//irrelevant to add as it is dictated by is.read...
						output.write(data, 0, nRead);
						totalRead += nRead;
						System.out.println("read " +  nRead + " and total read is now " + totalRead);
						if ((totalRead >= lengthBody)) {	//required as I don't send a eof or end of stream "is.read() == -1", ergo it waits forever regardless of while loop. (connect.setSoTimeout helps by breaking if 10 sec of nothing sent)
							break;	//done reading.
						}
					}
					//System.out.println("if false we done. " + (totalRead < lengthBody));
					if ((totalRead >= lengthBody) ) {
						is.close();
						break;
					}
					is = connect.getInputStream();
				}
				//System.out.println("are we done??????????");
				//inpS.transferTo(output);
			}
        } catch(Exception e) {
			System.out.println(e);
			return;	//unsure what else to do.
		}
		//*/
	}
	
	private String getContentType(String fileRequested) {
		if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html")) {
			return "text/html";
		}
		if (fileRequested.endsWith(".css")) {
			return "text/css";
		}
		return "text/plain";
	}
	
	private void fileNotFound(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
		File file = new File(WEB_ROOT, FILE_NOT_FOUND);
		int fileLength = (int) file.length();
		String content = "text/html";
		byte[] fileData = readFileData(file, fileLength);
		
		out.println("HTTP/1.1 404 File Not Found");
		out.println("Server: JserveMe : 1.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println(); // blank line between headers and content, very important !
		out.flush(); // flush character output stream buffer
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
		
		if (verbose) {
			System.out.println("File " + fileRequested + " not found");
		}
	}
	
	private FNode ReadFilesInDir(String path) {	//set fileCount to 0 on first run to index all files
		File folder = null;
		if (path.equals(".") || path.equals("/")) {
			folder = DATA_ROOT;
		} else {
			folder = new File(path);
		}
		boolean isDir = false;
		if (folder.isDirectory()) {
			isDir = true;
		}
		//System.out.println(path + " is " + isDir);
		FNode root = new FNode(folder.getName(), this.fileCount++, folder.getAbsolutePath(), isDir);
		//List<String> FileList = new ArrayList<String>();
		File[] listOfFiles = folder.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				//System.out.println("File " + listOfFiles[i].getName());
				//FileList.add(listOfFiles[i].getName());
				root.appendStr(listOfFiles[i].getName(), this.fileCount++, listOfFiles[i].getAbsolutePath(), false);
			} else if (listOfFiles[i].isDirectory()) {
				//System.out.println("Directory " + listOfFiles[i].getName());
				//System.out.println("Directory path: " + listOfFiles[i].getAbsolutePath());
				//this case we need to recurse call since we need to go into file.
				root.appendFNode(ReadFilesInDir(listOfFiles[i].getAbsolutePath()));
			}
		}
		return root;
	}
	
	private void printFNode(FNode root) {
		System.out.println(root.name + " is it a dir? " + root.isDir);
		for (int i = 0; i < root.children.size(); i++) {
			printFNode(root.children.get(i));
		}
	}
	
	private String searchFNodes(FNode root, int index) {
		//System.out.println("searching in " + root.path + " it has index " + root.index);
		//System.out.println("want the index " + index);
		if (root.index == index) {
			System.out.println("FOUND " + index + " with path of " + root.path +"/"+ root.name);
			return root.path;
		}
		for (int i = 0; i < root.children.size(); i++) {
			String tmp = searchFNodes(root.children.get(i), index);
			if(tmp != null) {
				return tmp;
			}
		}
		return null;
	}
	
	private String FNodetoJson(FNode root) {
		String listed = "";
		listed += "{\"name\":\"" + root.name + "\"," + "\"isDir\":\"" + root.isDir + "\"," + "\"val\":\"" + (this.counter++) + "\"," + "\"list\":[";
		if (root.children.size() != 0) {
			for (int i = 0; i < root.children.size()-1; i++) {
				FNode Node = root.children.get(i);
				if (Node.hasChildren) {
					listed += FNodetoJson(Node) + ", ";
				} else {
					listed += "{\"name\":" + "\"" + Node.name + "\"," + "\"isDir\":\"" + Node.isDir + "\"," + "\"val\":\"" + (this.counter++) + "\"" + "},";
				}
			}
			//last one
			FNode lastNode = root.children.get(root.children.size()-1);
			if (lastNode.hasChildren) {
				listed += FNodetoJson(lastNode);
			} else {
				listed += "{\"name\":" + "\"" + lastNode.name + "\"," + "\"isDir\":\"" + lastNode.isDir + "\"," + "\"val\":\"" + (this.counter++) + "\"}";
			}
			listed += "]}";
		}
		return listed;
	}
	
	private String read(InputStream inputStream, ByteArrayOutputStream baos) throws IOException {
		StringBuilder result = new StringBuilder();
		do {
			byte[] b = new byte[1];
			int didRead = inputStream.read(b, 0, 1);
			result.append((char) b[0]);
			baos.write(b, 0, 1);
		} while (inputStream.available() > 0);
		return result.toString();
	}
	
}
