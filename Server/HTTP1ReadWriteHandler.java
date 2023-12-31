package Server;

import java.nio.*;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.net.URLConnection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;

public class HTTP1ReadWriteHandler implements IReadWriteHandler {
	
	// Sockets
	private ByteBuffer inBuffer;
	private ByteBuffer outBuffer;
	private ByteBuffer fileBuffer;

	// connections
	private AtomicInteger numConnections;
	
	// CGI Handling
	Process cgi;

	// Buffer Handlers
	private HTTP1ReadHandler readHandler;
	private HTTP1WriteHandler writeHandler;

	private boolean channelClosed;

	private ServerConfig config;

	// private State state;

	public HTTP1ReadWriteHandler(ServerConfig c, AtomicInteger nC) {
		inBuffer = ByteBuffer.allocate(8192);
		outBuffer = ByteBuffer.allocate(8192);
		fileBuffer = null;

		readHandler = new HTTP1ReadHandler();
		writeHandler = new HTTP1WriteHandler();

		// initial state
		channelClosed = false;
		config = c;
		numConnections = nC;
	}

	public void handleException() {

	}
	private void close_socket(SelectionKey key){
		try{
			key.channel().close();
			key.cancel();
			channelClosed = true;
			numConnections.decrementAndGet();
			return;
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void reset_handler() {
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);
		fileBuffer = null;

		readHandler.resetHandler();
		writeHandler.resetHandler();

		// initial state
		channelClosed = false;
	}

	public int getInitOps() {
		return SelectionKey.OP_READ;
	}

	public void handleRead(SelectionKey key) throws IOException {

		if (readHandler.isRequestComplete()) {
			return;
		}
		// process data
		processInBuffer(key);

		// update state
		updateConnectionState(key);

	} // end of handleRead

	private void updateConnectionState(SelectionKey key) throws IOException {

		if (channelClosed) return;
		

		// Keep-Connection Alive or not.
		if (writeHandler.isResponseSent()) {
			if (readHandler.willKeepAlive()) {
				Debug.DEBUG("***Response sent; keep-connection alive");
				key.interestOps(getInitOps());
				reset_handler();
				return;
			} else {
				Debug.DEBUG("***Response sent; shutdown connection");
				close_socket(key);
				return;
			}
		}

		// Process Read ops
		int nextState = key.interestOps();
		if (readHandler.isRequestComplete()) {
			nextState = nextState & ~SelectionKey.OP_READ;
			Debug.DEBUG("New state: -Read since request parsed complete");
		} else {
			nextState = nextState | SelectionKey.OP_READ;
			Debug.DEBUG("New state: +Read to continue to read");
		}

		// Process Write Ops
		if (readHandler.isRequestComplete()) {
			// If Response is sending, keep write selection key open.
			if (writeHandler.isSending()) {
				nextState = nextState | SelectionKey.OP_WRITE;
				Debug.DEBUG("New state: +Write since response ready but not done sent");
			} else {
				nextState = nextState & ~SelectionKey.OP_WRITE;
				Debug.DEBUG("New state: -Write since response ready and sent");
			}
		}

		key.interestOps(nextState);

	}

	public void handleWrite(SelectionKey key) throws IOException {

		if (writeHandler.isResponseSent())
			return;
		if (writeHandler.isResponseReady()) {
			writeHandler.generateResponseHeaders(outBuffer, readHandler.protocol);
			writeHandler.setSendingHeaders();
		}

		Debug.DEBUG("->handleWrite");
		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer);

		// Send Headers
		if (writeHandler.isSendingHeaders()) {
			writeHandler.sendHeaders(client, outBuffer);
		}

		// Send the body
		if (writeHandler.isSendingBody()) {
			writeHandler.sendBody(client, fileBuffer);
		}

		if (writeHandler.needsCGIchunk()) {
			InputStream scriptOutput = cgi.getInputStream();
			int bytesToRead = scriptOutput.available();
			Debug.DEBUG("Bytes Predicted: " + bytesToRead);
			Debug.DEBUG("Is process running:" + cgi.isAlive());

			// If nothing left in the stream and the process has terminated.
			if (!cgi.isAlive() && bytesToRead == 0) {
				outBuffer.clear();
				writeHandler.generateCGIChunk(outBuffer, new byte[0], 0);
				writeHandler.sendCGITerminator(client, outBuffer);
			} else {
				if (bytesToRead == 0) {
					Debug.DEBUG("No Bytes ready from output");
					return;
				}
				byte b[] = new byte[bytesToRead];
				int bytesRead = scriptOutput.read(b);
				Debug.DEBUG("CGI Chunk length: " + bytesRead);
				outBuffer.clear();
				writeHandler.generateCGIChunk(outBuffer, b, bytesRead);
				writeHandler.setSendingCGI();
			}
		}

		if (writeHandler.isSendingCGI()) {
			writeHandler.sendCGI(client, outBuffer);
		}

		if (writeHandler.isSendingCGITerminator()) {
			writeHandler.sendCGITerminator(client, outBuffer);
		}

		// update state
		updateConnectionState(key);

		// try {Thread.sleep(5000);} catch (InterruptedException e) {}
		Debug.DEBUG("handleWrite->\n\n");
	} // end of handleWrite

	private void processInBuffer(SelectionKey key) throws IOException {

		// Read from Socket
		SocketChannel client = (SocketChannel) key.channel();
		int readBytes = client.read(inBuffer);

		Debug.DEBUG("handleRead: Read data from connection " + client + " for " + readBytes + " byte(s); to buffer "
				+ inBuffer);

		// -1 for an end of stream
		if (readBytes == -1) { 
			close_socket(key);
			return;
		}
		inBuffer.flip(); // read input

		readHandler.readBuffer(inBuffer);

		if (readHandler.isRequestError()) {
			writeHandler.setStatusLine("400", "Bad Request");
		}

		inBuffer.clear(); // we do not keep things in the inBuffer

		if (readHandler.isRequestComplete()) {
			generateResponse(client);
		}
	} // end of process input

	private byte[] file_to_bytearr(File f) {
		byte[] arr = new byte[(int) f.length()];
		try {
			FileInputStream fl = new FileInputStream(f);
			fl.read(arr);
			fl.close();
		} catch (Exception e) {
			return null;
		}

		return arr;
	}

	private void validateRequest() {

	}

	private void generateResponse(SocketChannel client) {
		// If Response has already been created, don't run
		if (!writeHandler.isCreatingResponse())
			return;

		String target = readHandler.target;
		HashMap<String, String> headers = readHandler.headers;

		//By Default all error messages have no content. Eventually put into a handle exception call.
		writeHandler.addOutputHeader("Content-Length","0");
		if (readHandler.willKeepAlive()){
			writeHandler.addOutputHeader("Connection", "Keep-Alive");
		}
		else{  
			writeHandler.addOutputHeader("Connection", "close");
		}

		// Verification Methods
		if (target.contains("../")) {
			writeHandler.setStatusLine("400", "Bad Request");
			return;
		}

		//Verify Method
		if (!readHandler.method.equals("GET") && !readHandler.method.equals("POST")) {
			writeHandler.setStatusLine("405", "Method \"" + readHandler.method + "\" not supported");
			return;
		}

		// Root Selection
		String host = headers.get("Host");
		String Root;

		if (host != null && config.getVHostRoots().get(host) != null){
			Root = config.getVHostRoots().get(host);
		}
		else {
			Root = config.getDefaultRoot();
		}
		
		
		//"/Users/whyalex/Desktop/Code Projects/CPSC434-HTTP-SERVER/www/example1";


		// File Selection
		File resource = new File(Root + target);
		// Mobile Content Check
		if (target.equals("/")) {
			String device = readHandler.headers.get("User-Agent");
			if (device != null && device.matches("(.*)(iPhone|Mobile)(.*)")) {
				Debug.DEBUG("is_mobile");
				resource = new File(Root + "/index_m.html");
				if (!resource.exists()){
					resource = new File(Root + "/index.html");
				}
			} else {
				resource = new File(Root + "/index.html");
			}
		}
		// Directory Check and change to index.html
		else if (resource.isDirectory()) {
			Debug.DEBUG("Looking for Index.html");
			resource = new File(Root + target + "/index.html");
		}

		if (resource.getName().equals("load")) {
			if (numConnections.get() > 1) {
				writeHandler.setStatusLine("503", "OVERLOADED");
			}
			else {
				writeHandler.setStatusLine("200", "OK");
			}
			Debug.DEBUG("numConnections: " + numConnections.get());
			Debug.DEBUG("Sent Load!");
			return;
		}

		// Check if URL exists.
		if (!resource.exists()) {
			writeHandler.setStatusLine("404", "Not Found");
			return;
		}

		// Check for Authorization
		Path htacessPath = resource.getParentFile().toPath().resolve(".htaccess");
		File access = htacessPath.toFile();
		Htaccess auth = null;
		if (access.exists()) {
			// Confirm Authorization Token.
			if (headers.get("Authorization") != null) {
				auth = new Htaccess(access);
				if (!auth.authenticateToken(headers.get("Authorization"))) {
					Debug.DEBUG("Wrong Token");
					writeHandler.addOutputHeader("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
					writeHandler.setStatusLine("401", "Invalid Credentials");
					return;
				}
			}
			// No Token is sent, send back authenticate request.
			else {
				Debug.DEBUG("UNAUTHORIZED");
				writeHandler.addOutputHeader("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
				writeHandler.setStatusLine("401", "Unauthorized");
				return;
			}
		}

		// File Specific Headers
		LocalDateTime lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(resource.lastModified()), ZoneId.of("Etc/UTC"));
		ZonedDateTime lastModifiedZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(resource.lastModified()), ZoneId.of("Etc/UTC")); 
		OffsetDateTime odt_last_modified = lastModified.atOffset(ZoneOffset.UTC);
		writeHandler.addOutputHeader("Last-Modified", odt_last_modified.format(DateTimeFormatter.RFC_1123_DATE_TIME));

		if(headers.get("If-Modified-Since") != null){
			Debug.DEBUG("Modfied-Since:" + headers.get("If-Modified-Since"));
			// ZonedDateTime since = ZonedDateTime.parse(headers.get("If-Modified-Since"), DateTimeFormatter.RFC_1123_DATE_TIME);
			LocalDateTime since = LocalDateTime.parse(headers.get("If-Modified-Since"), DateTimeFormatter.RFC_1123_DATE_TIME);
			if (lastModified.isBefore(since) || lastModified.isEqual(since)){
				writeHandler.setStatusLine("304", "No Change");
				return;
			}
		}

		// Executable Check
		if (target.endsWith(".cgi")) {
			if (!resource.canExecute()) {
				writeHandler.setStatusLine("403", "Can't Execute Resource");
				return;
			}
			Debug.DEBUG("Execeuting CGI");
			ProcessBuilder pb = new ProcessBuilder(resource.getAbsolutePath());
			Map<String, String> env = pb.environment();
			// env.put("AUTH_TYPE", headers.get("Authorization"));
			env.put("QUERY_STRING", readHandler.query_string);
			env.put("REQUEST_METHOD", readHandler.method);
			// env.put("CONTENT_TYPE",headers.get("Content-Type"));
			// env.put("CONTENT_LENGTH",headers.get("Content-Length"));
			env.put("REMOTE_ADDR", client.socket().getInetAddress().toString());
			env.put("REMOTE_HOST", client.socket().getInetAddress().getHostName());
			env.put("SERVER_PORT", Integer.toString(client.socket().getLocalPort()));
			env.put("SERVER_NAME", client.socket().getLocalSocketAddress().toString());
			env.put("SERVER_PROTOCOL", readHandler.protocol);
			env.put("SERVER_SOFTWARE", "Yague/1.0");
			if (readHandler.body != null) {
				String length = readHandler.headers.get("Content-Length");
				if (length == null){
					env.put("CONTENT_LENGTH", Integer.toString(readHandler.body.length()));
				}
				else{
					env.put("CONTENT_LENGTH", readHandler.headers.get("Content-Length"));
				}
				env.put("CONTENT_TYPE", readHandler.headers.get("Content-Type"));
			}
			if (auth != null) {
				env.put("REMOTE_USER", auth.getUser());
				env.put("AUTH_TYPE", auth.getAuthType());
			}

			try {
				cgi = pb.start();
				writeHandler.setStatusLine("200", "File Found");
				writeHandler.sendcgi = true;
				writeHandler.removeOutputHeader("Content-Length");
				writeHandler.addOutputHeader("Transfer-Encoding", "chunked");
				if (readHandler.body != null) {
					cgi.getOutputStream().write(readHandler.body.getBytes());
					cgi.getOutputStream().flush();

				}
				cgi.getOutputStream().close();

				// writeHandler.addOutputHeader("Content-Type","text/plain");
				return;
			} catch (Exception e) {
				writeHandler.setStatusLine("500", "Server Error");
				return;
			}

		}

		// Read Check
		fileBuffer = ByteBuffer.wrap(file_to_bytearr(resource));
		if (fileBuffer == null) {
			writeHandler.setStatusLine("500", "File couldn't be read.");
			return;
		}
		// Check if URL is readable
		if (!resource.canRead()) {
			writeHandler.setStatusLine("403", "Can't read resource");
			return;
		}

		try{
			String mimeType= URLConnection.guessContentTypeFromName(resource.getName());
			if (mimeType != null){
				writeHandler.addOutputHeader("Content-Type", mimeType);
			}
		}
		catch (Exception e){
			writeHandler.setStatusLine("500", "Server Error");
			return;
		}

		
		writeHandler.addOutputHeader("Content-Length",Long.toString(resource.length()));
		writeHandler.setStatusLine("200", "File Found");
		writeHandler.sendbody = true;
		return;
	} // end of generate response

}
