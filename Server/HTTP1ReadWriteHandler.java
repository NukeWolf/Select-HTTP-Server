package server;
import server.Htaccess;
import server.HTTP1ReadHandler;
import server.HTTP1WriteHandler;


import java.nio.*;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;

public class HTTP1ReadWriteHandler implements IReadWriteHandler {

	//Sockets
	private ByteBuffer inBuffer;
	private ByteBuffer outBuffer;
	private ByteBuffer fileBuffer;

	// Buffer Handlers
	private HTTP1ReadHandler requestHandler;
	private HTTP1WriteHandler writeHandler;

	private boolean channelClosed;

	private final int REQUEST_TIMEOUT = 3;
	// private State state;

	public HTTP1ReadWriteHandler() {
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);
		fileBuffer = null;

		requestHandler = new HTTP1ReadHandler();
		writeHandler = new HTTP1WriteHandler();

		// initial state
		channelClosed = false;

	}

	public void handleException(){

	}
	private void close_socket(SelectionKey key){
		try{
			ServerSocketChannel client = (ServerSocketChannel) key.channel();
			client.close();
			key.cancel();
			channelClosed = true; 
			return;
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
	}

	private void reset_handler(){
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);
		fileBuffer = null;

		requestHandler.resetHandler();
		writeHandler.resetHandler();
		
		// initial state
		channelClosed = false;
	}


	public int getInitOps() {
		return SelectionKey.OP_READ;
	}

	public void handleRead(SelectionKey key) throws IOException {

		if (requestHandler.isRequestComplete()) { // this call should not happen, ignore
			return;
		}
		// process data
		processInBuffer(key);

		// update state
		updateSelectorState(key);

	} // end of handleRead


	private void updateSelectorState(SelectionKey key) throws IOException {

		if (channelClosed)
			return;
		

		// Keep-Connection Alive or not.
		if (writeHandler.isResponseSent()) { 
			if(requestHandler.willKeepAlive()){
				Debug.DEBUG("***Response sent; keep-connection alive"); 
				key.interestOps(getInitOps());
				reset_handler();
				return;
			}
			else{
				Debug.DEBUG("***Response sent; shutdown connection"); 
				close_socket(key);
				return;
			}
		}
		 

		//Process Read ops
		int nextState = key.interestOps();
		if (requestHandler.isRequestComplete()) {
			nextState = nextState & ~SelectionKey.OP_READ;
			Debug.DEBUG("New state: -Read since request parsed complete");
		} else {
			nextState = nextState | SelectionKey.OP_READ;
			Debug.DEBUG("New state: +Read to continue to read");
		}

		//Process Write Ops
		if(requestHandler.isRequestComplete()){
			//If Response is sending, keep  write selection key open.
			if (writeHandler.isSending()) {
				nextState = nextState | SelectionKey.OP_WRITE;
				Debug.DEBUG("New state: +Write since response ready but not done sent");
			}
			else {
				nextState = nextState & ~SelectionKey.OP_WRITE;
				Debug.DEBUG("New state: -Write since response ready and sent");
			}
		}
		
		key.interestOps(nextState);

	}


	public void handleWrite(SelectionKey key) throws IOException {

		if(writeHandler.isResponseSent()) return;
		if(writeHandler.isResponseReady()) {
			writeHandler.generateResponseHeaders(outBuffer,requestHandler.protocol);
			writeHandler.setSendingHeaders();
		}

		Debug.DEBUG("->handleWrite");
		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer);
		
		// Send Headers
		if (writeHandler.isSendingHeaders()){
			writeHandler.sendHeaders(client, outBuffer);
		}
		
		//Send the body
		if(writeHandler.isSendingBody()){
			writeHandler.sendBody(client, fileBuffer);
		}
		
		
		// update state
		updateSelectorState(key);

		// try {Thread.sleep(5000);} catch (InterruptedException e) {}
		Debug.DEBUG("handleWrite->");
	} // end of handleWrite

	private void processInBuffer(SelectionKey key) throws IOException {

		// Read from Socket
		SocketChannel client = (SocketChannel) key.channel();
		int readBytes = client.read(inBuffer);

		Debug.DEBUG("handleRead: Read data from connection " + client + " for " + readBytes + " byte(s); to buffer "
		+ inBuffer);

		// If no bytes to read. TODO: Not sure if right as a request ends in a CRLF.
		if (readBytes == -1) { 
			return;
		} 
		inBuffer.flip(); // read input

		requestHandler.readBuffer(inBuffer);

		if(requestHandler.isRequestError()){
			writeHandler.setStatusLine("400","Bad Request");
		}
		
		inBuffer.clear(); // we do not keep things in the inBuffer

		if (requestHandler.isRequestComplete()) { 
			//validateRequest();
			generateResponse();
		}
	} // end of process input


	private byte[] file_to_bytearr(File f){
		byte[] arr = new byte[(int)f.length()]; 
		try{
			FileInputStream fl = new FileInputStream(f);
			fl.read(arr);
			fl.close();
		}
		catch(Exception e){
			return null;
		}
		
		return arr;
	}
	
	private void generateResponse() {

		// If Response has already been created, don't run
		if (!writeHandler.isCreatingResponse()) return;

		String target = requestHandler.target;
		HashMap<String,String> headers = requestHandler.headers;

		//By Default all error messages have no content. Eventually put into a handle exception call.
		writeHandler.addOutputHeader("Content-Length","0");

		String Root = "/Users/whyalex/Desktop/Code Projects/CPSC434-HTTP-SERVER/www/example1";
		File resource = new File(Root + target);

		//Mobile Content Check
		if(target == "/"){
			String device = requestHandler.headers.get("User-Agent");
			if(device.matches("iPhone|Mobile")){
				resource = new File (Root + "/index_m.html");
			}
			else{
				resource = new File (Root + "/index.html");
			}
		}

		// Directory Check and change to index.html
		if(resource.isDirectory()) {
			Debug.DEBUG("Looking for Index.html");
			resource = new File(Root+target+"index.html");
		}

		//Check if URL exists.
		if (!resource.exists()){
			writeHandler.setStatusLine("404","Not Found");
			return;
		}


		//Check for Authorization
		Path htacessPath = resource.getParentFile().toPath().resolve(".htaccess");
		File access = htacessPath.toFile();
		if (access.exists()){
			Htaccess auth = new Htaccess(access);
			// Confirm Authorization Token.
			if (headers.get("Authorization") != null){
				if (!auth.authenticateToken(headers.get("Authorization"))){
					Debug.DEBUG("Wrong Token");
					writeHandler.addOutputHeader("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
					writeHandler.setStatusLine("401","Invalid Credentials");
					return;
				}
			}
			//No Token is sent, send back authenticate request.
			else{
				Debug.DEBUG("UNAUTHORIZED");
				writeHandler.addOutputHeader("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
				writeHandler.setStatusLine("401","Unauthorized");
				return;
			}
		}

		
		// Executable Check
		if(target.endsWith(".cgi")){
			if (!resource.canExecute()){
				writeHandler.setStatusLine("403", "Can't Execute Resource");
				return;
			}
			// ProcessBuilder pb = new ProcessBuilder("myCommand", "myArg1", "myArg2");
			// Map<String, String> env = pb.environment();
			// env.put("VAR1", "myValue");
			// env.remove("OTHERVAR");
			// env.put("VAR2", env.get("VAR1") + "suffix");
			// pb.directory("myDir");
			// Process p = pb.start();
		}


		//Read Check
		fileBuffer = ByteBuffer.wrap(file_to_bytearr(resource));	
		if(fileBuffer == null){
			writeHandler.setStatusLine("500", "File couldn't be read.");
			return;
		}
		//Check if URL is readable
		if (!resource.canRead()){
			writeHandler.setStatusLine("403", "Can't read resource");
			return;
		}
		
		// File Specific Headers
		LocalDateTime lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(resource.lastModified()), 
                                TimeZone.getDefault().toZoneId()); 
		OffsetDateTime odt_last_modified = lastModified.atOffset(ZoneOffset.UTC);
		writeHandler.addOutputHeader("Last-Modified",odt_last_modified.format(DateTimeFormatter.RFC_1123_DATE_TIME));
		writeHandler.addOutputHeader("Content-Length",Long.toString(resource.length()));

		writeHandler.setStatusLine("200", "File Found");
		return;
	} // end of generate response

}
