package Server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTP1ReadHandler {
	// Request Fields
	private StringBuffer line_buffer;
	public String method;
	public String target;
	public String query_string;
	public String protocol;

	public HashMap<String, String> headers;
	public String body;
	private int bodyByteCount;

	private enum ReadStates {
		REQUEST_METHOD, REQUEST_TARGET, REQUEST_PROTOCOL, REQUEST_HEADERS, REQUEST_BODY, REQUEST_COMPLETE,
		REQUEST_PARSE_ERROR
	}

	private ReadStates currentReadState;

	public HTTP1ReadHandler() {
		bodyByteCount = 0;
		line_buffer = new StringBuffer(4096);
		headers = new HashMap<String, String>();
		currentReadState = ReadStates.REQUEST_METHOD;
    }
    public void resetHandler(){
        bodyByteCount = 0;
		line_buffer.setLength(0);
		headers = new HashMap<String,String>();
		currentReadState = ReadStates.REQUEST_METHOD;
	}

	public void readBuffer(ByteBuffer inBuffer) {

		while (!isRequestComplete() && inBuffer.hasRemaining() && line_buffer.length() < line_buffer.capacity()) {
			char ch = (char) inBuffer.get();

			switch (currentReadState) {
				case REQUEST_METHOD:
					if (ch == ' ') { // Handle a whitespace character
						currentReadState = ReadStates.REQUEST_TARGET;
						method = line_buffer.toString();
						line_buffer.setLength(0);
						Debug.DEBUG("METHOD:" + method);
					} else if (ch == '\r' || ch == '\n')
						handleException();// INVALID FORMATTING throw exception
					else
						line_buffer.append(ch);
					break;

				case REQUEST_TARGET:
					if (ch == ' ') { // Handle a whitespace character
						Debug.DEBUG("TARGET_LINE" + line_buffer.toString());
						currentReadState = ReadStates.REQUEST_PROTOCOL;
						parseTargetLine();
						line_buffer.setLength(0);
						Debug.DEBUG("Target: " + target);
						Debug.DEBUG("Query: " + query_string);
					} else if (ch == '\r' || ch == '\n')
						handleException();// INVALID FORMATTING throw exception
					else
						line_buffer.append(ch);
					break;

				case REQUEST_PROTOCOL:
					if (ch == '\n') {
						currentReadState = ReadStates.REQUEST_HEADERS;
						protocol = line_buffer.toString();
						line_buffer.setLength(0);
						Debug.DEBUG("Protocol:" + protocol);
					} else if (ch == '\r') {
					} // INVALID FORMATTING throw exception
					else
						line_buffer.append(ch);
					break;

				case REQUEST_HEADERS:
					if (ch == '\n') {
						Debug.DEBUG("Header : " + line_buffer.toString());
						// If two new lines in a row, move on to messge body.
						if (!parseHeaderLine()) {
							// If there is no Content Length message, assume request is complete.
							Debug.DEBUG("Clength: " + headers.get("Content-Length"));
							if (headers.get("Content-Length") == null) {
								currentReadState = ReadStates.REQUEST_COMPLETE;
								body = null;
							} else {
								if (headers.get("Content-Length").matches("[0-9]+")) {
									currentReadState = ReadStates.REQUEST_BODY;
									Debug.DEBUG("Reading Body");
								} else {
									handleException();
								}
							}
						}
						line_buffer.setLength(0);
					} else if (ch == '\r') {
					} else
						line_buffer.append(ch);
					break;

				case REQUEST_BODY:
					int maxbytes = Integer.parseInt(headers.get("Content-Length"));
					if (bodyByteCount >= maxbytes - 1) {
						currentReadState = ReadStates.REQUEST_COMPLETE;
						line_buffer.append(ch);
						body = line_buffer.toString();
						Debug.DEBUG("Body:" + body);
					} else {
						line_buffer.append(ch);
						bodyByteCount += 1;
					}
					break;
					
				case REQUEST_COMPLETE:
					break;
			} // end of switch
		} // end of while
	}

	// Returns true if header is added. False if empty space was detected and no
	// header was added.
	private Boolean parseHeaderLine() {
		String s = line_buffer.toString();
		if (s.length() == 0) {
			return false;
		}
		String matchregex = "([\\w-]+):\\s*(.+)\\s*";
        Pattern pattern = Pattern.compile(matchregex);
        Matcher matcher = pattern.matcher(line_buffer.toString());
		try{
            matcher.find();
			String fieldName = matcher.group(1);
			String fieldValue = matcher.group(2);

            if (fieldValue == null){
                handleException();
				return true;
            }
			headers.put(fieldName,fieldValue);
			return true;
        }
        catch (Exception e){
            handleException();
            e.printStackTrace();
			return true;
        }
		
		
	}
    //Seperates the query string
    private void parseTargetLine(){
        String matchregex = "([\\/\\\\a-zA-Z0-9\\.-_]+)(\\?.+)?";
        Pattern pattern = Pattern.compile(matchregex);
        Matcher matcher = pattern.matcher(line_buffer.toString());
        try{
            matcher.find();
            target = matcher.group(1);
            query_string = matcher.group(2);
            if (query_string == null){
                query_string = "";
            }
            else{
                query_string = query_string.substring(1);
            }
        }
        catch (Exception e){
            handleException();
            e.printStackTrace();
        }
        

    }

    public boolean isRequestComplete(){
        return (currentReadState == ReadStates.REQUEST_COMPLETE || currentReadState == ReadStates.REQUEST_PARSE_ERROR);
    }

    public boolean isRequestError(){
        return currentReadState == ReadStates.REQUEST_PARSE_ERROR;
    }

    public boolean willKeepAlive(){
        //By Default Keep Alive
        return headers.get("Connection") == null || headers.get("Connection").toLowerCase().equals("keep-alive");
    }

    public void handleException(){
        currentReadState = ReadStates.REQUEST_PARSE_ERROR;
        return;
    }
    

}
