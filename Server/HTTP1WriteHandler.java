package server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class HTTP1WriteHandler {
    //Response Fields
	private String response_status_code;
	private String response_status_msg;
	private HashMap<String,String> outheaders;

    //States
	private enum WriteStates { RESPONSE_CREATING, RESPONSE_READY, RESPONSE_SENDING_HEADERS, RESPONSE_SENDING_BODY, RESPONSE_SENT}
	private WriteStates currentWriteState;

    public HTTP1WriteHandler(){
        currentWriteState = WriteStates.RESPONSE_CREATING;
        outheaders = new HashMap<String,String>();
    }

    public void resetHandler(){
        currentWriteState = WriteStates.RESPONSE_CREATING;
        outheaders = new HashMap<String,String>();
    }



    public boolean isResponseSent(){
        return currentWriteState == WriteStates.RESPONSE_SENT;
    }
    public boolean isCreatingResponse(){
        return currentWriteState == WriteStates.RESPONSE_CREATING;
    }

    public boolean isResponseReady(){
        return currentWriteState == WriteStates.RESPONSE_READY;
    }
    public boolean isSending(){
        return currentWriteState == WriteStates.RESPONSE_READY || currentWriteState == WriteStates.RESPONSE_SENDING_BODY || currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS;
    }
     public boolean isSendingHeaders(){
        return currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS;
    }
    public boolean isSendingBody(){
        return currentWriteState == WriteStates.RESPONSE_SENDING_BODY;
    }

    public void setSendingHeaders(){
        currentWriteState = WriteStates.RESPONSE_SENDING_HEADERS;
    }
    public void sendHeaders(SocketChannel client, ByteBuffer outBuffer){
        try{
            int writeBytes = client.write(outBuffer);
            if (outBuffer.remaining() == 0) {
                currentWriteState = WriteStates.RESPONSE_SENDING_BODY;
                Debug.DEBUG("handleWrite: headersSent");
            }
            Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer);
        }
        catch (Exception e){
            // Should Return Server Error
            e.printStackTrace();
        }
        
        
    }
    public void sendBody(SocketChannel client, ByteBuffer fileBuffer){
        try{
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
        catch (Exception e){
            // Should Return Server Error
            e.printStackTrace();
        }
        
    }


    public void appendStringToByteBuf(ByteBuffer b, String s){
		for (int i  = 0; i<s.length();i++){
			b.put((byte) s.charAt(i));
		}
	}

	public void generateResponseHeaders(ByteBuffer outBuffer, String protocol){
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

    public void setStatusLine(String status_code, String msg){
        response_status_code = status_code;
        response_status_msg = msg;
        currentWriteState = WriteStates.RESPONSE_READY;
    }

    public void addOutputHeader(String key, String value){
        outheaders.put(key,value);
    }
}
