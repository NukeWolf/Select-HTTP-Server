package server;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class HTTP1ReadHandler {
    // Request Fields
	private StringBuffer line_buffer;
	public String method;
	public String target;
	public String protocol;

	public HashMap<String,String> headers;
	public String body;
	private int bodyByteCount;

    private enum ReadStates { REQUEST_METHOD, REQUEST_TARGET, REQUEST_PROTOCOL, REQUEST_HEADERS, REQUEST_BODY, REQUEST_COMPLETE , REQUEST_PARSE_ERROR}
	private ReadStates currentReadState;

    public HTTP1ReadHandler(){
        bodyByteCount = 0;
		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;

    }
    public void resetHandler(){
        bodyByteCount = 0;
		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;
    }

    public void readBuffer(ByteBuffer inBuffer){

        while (!isRequestComplete() && inBuffer.hasRemaining() && line_buffer.length() < line_buffer.capacity()) {
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
                        //If two new lines in a row, move on to messge body.
						if(!parseHeaderLine()){
                            // If there is no Content Length message, assume request is complete.
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
    }

    // Returns true if header is added. False if empty space was detected and no header was added.
	private Boolean parseHeaderLine(){
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

    public boolean isRequestComplete(){
        return (currentReadState == ReadStates.REQUEST_COMPLETE || currentReadState == ReadStates.REQUEST_PARSE_ERROR);
    }

    public boolean isRequestError(){
        return currentReadState == ReadStates.REQUEST_PARSE_ERROR;
    }

    public boolean willKeepAlive(){
        //By Default Keep Alive
        return headers.get("Connection") == null || headers.get("Connection").equals("keep-alive");
    }

    public void handleException(){
        currentReadState = ReadStates.REQUEST_PARSE_ERROR;
        return;
    }
    

}
