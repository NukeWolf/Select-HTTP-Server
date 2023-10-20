package Server;
import java.nio.*;
import java.nio.channels.*;
import java.util.HashMap;
import java.io.IOException;

public class HTTP1ReadWriteHandler implements IReadWriteHandler {

	private ByteBuffer inBuffer;
	private ByteBuffer outBuffer;

	private boolean requestComplete;
	private boolean responseReady;
	private boolean responseSent;
	private boolean channelClosed;

	private StringBuffer line_buffer;

	private String method;
	private String target;
	private String protocol;

	private HashMap<String,String> headers;
	private String body;
	private int bodyByteCount;

	private enum ReadStates { REQUEST_METHOD, REQUEST_TARGET, REQUEST_PROTOCOL, REQUEST_HEADERS, REQUEST_BODY, REQUEST_COMPLETE}
	private ReadStates currentReadState;

	// private State state;

	public HTTP1ReadWriteHandler() {
		inBuffer = ByteBuffer.allocate(4096);
		outBuffer = ByteBuffer.allocate(4096);

		// initial state
		requestComplete = false;
		responseReady = false;
		responseSent = false;
		channelClosed = false;

		bodyByteCount = 0;


		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;
	}

	public int getInitOps() {
		return SelectionKey.OP_READ;
	}

	public void handleException() {
	}

	public void handleRead(SelectionKey key) throws IOException {

		if (requestComplete) { // this call should not happen, ignore
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

		/*
		 * if (responseSent) { Debug.DEBUG(
		 * "***Response sent; shutdown connection"); client.close();
		 * dispatcher.deregisterSelection(sk); channelClosed = true; return; }
		 */

		int nextState = key.interestOps();
		if (currentReadState == ReadStates.REQUEST_COMPLETE) {
			nextState = nextState & ~SelectionKey.OP_READ;
			Debug.DEBUG("New state: -Read since request parsed complete");
		} else {
			nextState = nextState | SelectionKey.OP_READ;
			Debug.DEBUG("New state: +Read to continue to read");
		}

		if (responseReady) {
			if (!responseSent) {
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
		Debug.DEBUG("->handleWrite");

		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer);
		int writeBytes = client.write(outBuffer);
		Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer);

		if (responseReady && (outBuffer.remaining() == 0)) {
			responseSent = true;
			Debug.DEBUG("handleWrite: responseSent");
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
			requestComplete = true;
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
			//generateResponse();
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



	
	// private void generateResponse() {
	// 	for (int i = 0; i < request.length(); i++) {
	// 		char ch = (char) request.charAt(i);

	// 		ch = Character.toUpperCase(ch);

	// 		outBuffer.put((byte) ch);
	// 	}
	// 	outBuffer.flip();
	// 	responseReady = true;
	// } // end of generate response

}