package server;
import server.Htaccess;

import java.nio.*;
import java.nio.channels.*;
import java.util.HashMap;
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
	
	// Request Fields
	private StringBuffer line_buffer;
	private String method;
	private String target;
	private String protocol;

	private HashMap<String,String> headers;
	private String body;
	private int bodyByteCount;

	//Response Fields
	private String response_status_code;
	private String response_status_msg;

	private HashMap<String,String> outheaders;

	//States
	private enum ReadStates { REQUEST_METHOD, REQUEST_TARGET, REQUEST_PROTOCOL, REQUEST_HEADERS, REQUEST_BODY, REQUEST_COMPLETE}
	private enum WriteStates { RESPONSE_CREATION, RESPONSE_READY, RESPONSE_SENDING_HEADERS, RESPONSE_SENDING_BODY, RESPONSE_SENT}
	private ReadStates currentReadState;
	private WriteStates currentWriteState;

	private boolean channelClosed;

	private final int REQUEST_TIMEOUT = 3;
	// private State state;

	public HTTP1ReadWriteHandler() {
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);
		fileBuffer = null;


		// initial state
		channelClosed = false;

		bodyByteCount = 0;

		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;
		currentWriteState = WriteStates.RESPONSE_CREATION;
		outheaders = new HashMap<String,String>();

	}

	private void reset_handler(){
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);
		fileBuffer = null;
		

		// initial state
		channelClosed = false;

		bodyByteCount = 0;

		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;
		currentWriteState = WriteStates.RESPONSE_CREATION;
		outheaders = new HashMap<String,String>();
	}


	public int getInitOps() {
		return SelectionKey.OP_READ;
	}

	public void handleException() {

	}

	public void handleRead(SelectionKey key) throws IOException {

		if (currentReadState == ReadStates.REQUEST_COMPLETE) { // this call should not happen, ignore
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
		if (currentWriteState == WriteStates.RESPONSE_SENT) { 
			if(headers.get("Connection") != null && headers.get("Connection").equals("closed")){
				Debug.DEBUG("***Response sent; shutdown connection"); 
				ServerSocketChannel client = (ServerSocketChannel) key.channel();
				client.close();
				key.cancel();
				channelClosed = true; 
				return;
			}
			// Default is keep connection alive
			else{
				Debug.DEBUG("***Response sent; keep-connection alive"); 
				key.interestOps(getInitOps());
				reset_handler();
				return;
			}
		}
		 

		int nextState = key.interestOps();
		if (currentReadState == ReadStates.REQUEST_COMPLETE) {
			nextState = nextState & ~SelectionKey.OP_READ;
			Debug.DEBUG("New state: -Read since request parsed complete");
		} else {
			nextState = nextState | SelectionKey.OP_READ;
			Debug.DEBUG("New state: +Read to continue to read");
		}
		if(currentReadState == ReadStates.REQUEST_COMPLETE){
			if (currentWriteState == WriteStates.RESPONSE_READY || currentWriteState == WriteStates.RESPONSE_SENDING_BODY || currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS) {
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

	public void appendStringToByteBuf(ByteBuffer b, String s){
		for (int i  = 0; i<s.length();i++){
			b.put((byte) s.charAt(i));
		}
	}

	public void generateResponseHeaders(){
		String status_line = protocol + " " + response_status_code + " " + response_status_msg + "\r\n";
		appendStringToByteBuf(outBuffer, status_line);
		String date_header = "Date: " + OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n";

		appendStringToByteBuf(outBuffer, date_header);

		outheaders.forEach((key,value) -> {
			appendStringToByteBuf(outBuffer, key + ":" + value + "\r\n");
		});
		appendStringToByteBuf(outBuffer, "\r\n");

		outBuffer.flip();
	}

	public void handleWrite(SelectionKey key) throws IOException {

		if(currentWriteState==WriteStates.RESPONSE_SENT) return;
		if(currentWriteState==WriteStates.RESPONSE_READY) {
			generateResponseHeaders();
			currentWriteState = WriteStates.RESPONSE_SENDING_HEADERS;
		}

		Debug.DEBUG("->handleWrite");
		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer);
		
		// Send Headers
		if (currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS){
			int writeBytes = client.write(outBuffer);
			if (outBuffer.remaining() == 0) {
				currentWriteState = WriteStates.RESPONSE_SENDING_BODY;
				Debug.DEBUG("handleWrite: headersSent");
			}
			Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer);
		}
		
		//Send the body
		if(currentWriteState == WriteStates.RESPONSE_SENDING_BODY){
			if(fileBuffer != null){
				client.write(fileBuffer);
				if (fileBuffer.remaining() == 0){
					currentWriteState = WriteStates.RESPONSE_SENT;
				}
			}
			else{
				currentWriteState = WriteStates.RESPONSE_SENT;
			}
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

		// Debug.DEBUG("handleRead: Read data from connection " + client + " for " + readBytes + " byte(s); to buffer "
		// + inBuffer);

		// If no bytes to read. TODO: Not sure if right as a request ends in a CRLF.
		if (readBytes == -1) { 
			return;
		} 

		inBuffer.flip(); // read input

		while (currentReadState != ReadStates.REQUEST_COMPLETE && inBuffer.hasRemaining() && line_buffer.length() < line_buffer.capacity()) {
			char ch = (char) inBuffer.get();
			switch(currentReadState){
				case REQUEST_METHOD:
					if (ch == ' ') { // Handle a whitespace character
						currentReadState = ReadStates.REQUEST_TARGET;
						method = line_buffer.toString();
						line_buffer.setLength(0);
						Debug.DEBUG("METHOD:" + method);
					}
					else if (ch == '\r' || ch == '\n') handleException();// INVALID FORMATTING throw exception
					else line_buffer.append(ch);
					break;
				case REQUEST_TARGET:
					if (ch == ' ') { // Handle a whitespace character
						currentReadState = ReadStates.REQUEST_PROTOCOL;
						target = line_buffer.toString();
						line_buffer.setLength(0);
						Debug.DEBUG("Target:" + target);
					}
					else if (ch == '\r' || ch == '\n') handleException();// INVALID FORMATTING throw exception
					else line_buffer.append(ch);
					break;
				case REQUEST_PROTOCOL:
					if (ch == '\n'){
						currentReadState = ReadStates.REQUEST_HEADERS;
						protocol = line_buffer.toString();
						line_buffer.setLength(0);
						Debug.DEBUG("Protocol:" + protocol);
					}
					else if (ch == '\r'){}  // INVALID FORMATTING throw exception
					else line_buffer.append(ch);
					break;
				case REQUEST_HEADERS:
					if (ch == '\n'){
						Debug.DEBUG("Header : " + line_buffer.toString());
						if(!processLineBufferHeader()){
							if(headers.get("Content-Length") == null){
								currentReadState = ReadStates.REQUEST_COMPLETE;

							}
							else{
								if(headers.get("Content-Length").matches("[0-9]+")){
									currentReadState = ReadStates.REQUEST_BODY;
								}
								else{
									handleException();
								}
							}
						}
						line_buffer.setLength(0);
					}
					else if (ch == '\r') {}
					else line_buffer.append(ch);
					break;
				case REQUEST_BODY:
					int maxbytes = Integer.parseInt(headers.get("Content-Length"));
					if (bodyByteCount >= maxbytes){
						currentReadState = ReadStates.REQUEST_COMPLETE;
						body = line_buffer.toString();
					}
					else{
						line_buffer.append(ch);
						bodyByteCount += 1;
					}
					break;
				case REQUEST_COMPLETE:
					break;
			} // end of switch
		} // end of while
		inBuffer.clear(); // we do not keep things in the inBuffer

		if (currentReadState == ReadStates.REQUEST_COMPLETE) { 
			//validateRequest();
			generateResponse();
		}
	} // end of process input

	// Returns true if header is added. False if empty space was detected and no header was added.
	private Boolean processLineBufferHeader(){
		String s = line_buffer.toString();
		if (s.length() == 0){
			return false;
		}
		String[] headerInfo = s.split(":");
		if (headerInfo.length < 2){
			handleException();
			return false;
		}
		String fieldName = headerInfo[0];
		String fieldValue = headerInfo[1].trim();
		headers.put(fieldName,fieldValue);
		return true;
	}

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
		// generateHeaders();
		if (!(currentWriteState == WriteStates.RESPONSE_CREATION)) return;

		//By Default all error messages have no content. Eventually put into a handle exception call.
		outheaders.put("Content-Length","0");

		String Root = "/Users/whyalex/Desktop/Code Projects/CPSC434-HTTP-SERVER/www/example1";
		File resource = new File(Root + target);

		//Mobile Content Check
		if(target == "/"){
			String device = headers.get("User-Agent");
			if(device.matches("iPhone|Mobile")){
				resource = new File (Root + "/index_m.html");
			}
			else{
				resource = new File (Root + "/index.html");
			}
		
		// Directory Check and change to index.html
		}
		if(resource.isDirectory()) {
			Debug.DEBUG("Looking for Index.html");
			resource = new File(Root+target+"index.html");
		}

		//Check if URL exists.
		if (!resource.exists()){
			response_status_code = "404";
			response_status_msg = "Resource Not Found";
			currentWriteState = WriteStates.RESPONSE_READY;
			return;
		}

		//Check if URL is readable
		if (!resource.canRead()){
			response_status_code = "403";
			response_status_msg = "Can't Read Resource";
			currentWriteState = WriteStates.RESPONSE_READY;
			return;
		}

		//Check for Authorization
		Path htacessPath = resource.getParentFile().toPath().resolve(".htaccess");
		File access = htacessPath.toFile();
		if (access.exists()){
			Htaccess auth = new Htaccess(access);
			if (headers.get("Authorization") != null){
				if (!auth.authenticateToken(headers.get("Authorization"))){
					Debug.DEBUG("Wrong Token");
					response_status_code = "401";
					response_status_msg = "Invalid Credentials";
					outheaders.put("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
					currentWriteState = WriteStates.RESPONSE_READY;
					return;
				}
			}
			else{
				Debug.DEBUG("UNAUTHORIZED");
				response_status_code = "401";
				response_status_msg = "Unauthorized";
				outheaders.put("WWW-Authenticate", "Basic realm=\"Restricted Files\"");
				currentWriteState = WriteStates.RESPONSE_READY;
				return;
			}
		}

		
		fileBuffer = ByteBuffer.wrap(file_to_bytearr(resource));	
		if(fileBuffer == null){
			response_status_code = "500";
			response_status_msg = "File couldn't be read.";
			currentWriteState = WriteStates.RESPONSE_READY;
			return;
		}
		

		// File Specific Headers
		LocalDateTime lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(resource.lastModified()), 
                                TimeZone.getDefault().toZoneId()); 
		OffsetDateTime odt_last_modified = lastModified.atOffset(ZoneOffset.UTC);
		outheaders.put("Last-Modified",odt_last_modified.format(DateTimeFormatter.RFC_1123_DATE_TIME));
		outheaders.put("Content-Length",Long.toString(resource.length()));

		response_status_code = "200";
		response_status_msg = "File Found";
		
		currentWriteState = WriteStates.RESPONSE_READY;
		return;
	} // end of generate response

<<<<<<< HEAD

=======
>>>>>>> 732e6d29c0ba585c15c23d54acbee0e2eaa0452c
}
