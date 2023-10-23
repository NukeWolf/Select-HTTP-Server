package server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class HTTP1WriteHandler {
    //Response Fields
	private String response_status_code;
	private String response_status_msg;
	private HashMap<String,String> outheaders;
    public boolean sendbody;
    public boolean sendcgi;

    private boolean cgi_headers_sent;
    private int number_of_returns;
    //States
	private enum WriteStates { RESPONSE_CREATING, RESPONSE_READY, RESPONSE_SENDING_HEADERS, RESPONSE_SENDING_BODY, 
                               RESPONSE_CREATE_CGI_CHUNK, RESPONSE_SENDING_CGI, RESPONSE_SEND_CGI_TERMINATOR, 
                               RESPONSE_SENT}
	private WriteStates currentWriteState;

    public HTTP1WriteHandler(){
        currentWriteState = WriteStates.RESPONSE_CREATING;
        outheaders = new HashMap<String,String>();
        sendbody = false;
        sendcgi = false;
        cgi_headers_sent = false;
        number_of_returns = 0;
    }

    public void resetHandler(){
        currentWriteState = WriteStates.RESPONSE_CREATING;
        outheaders = new HashMap<String,String>();
        sendbody = false;
        sendcgi = false;
        cgi_headers_sent = false;
        number_of_returns = 0;
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
        return currentWriteState == WriteStates.RESPONSE_READY || currentWriteState == WriteStates.RESPONSE_SENDING_BODY || currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS || isSendingCGI();
    }
    public boolean isSendingCGI(){
        return currentWriteState == WriteStates.RESPONSE_CREATE_CGI_CHUNK || currentWriteState == WriteStates.RESPONSE_SENDING_CGI || currentWriteState == WriteStates.RESPONSE_SEND_CGI_TERMINATOR;
    }

    public boolean isSendingCGITerminator(){
        return currentWriteState == WriteStates.RESPONSE_SEND_CGI_TERMINATOR;
    }

     public boolean isSendingHeaders(){
        return currentWriteState == WriteStates.RESPONSE_SENDING_HEADERS;
    }
    public boolean isSendingBody(){
        return currentWriteState == WriteStates.RESPONSE_SENDING_BODY;
    }


    public boolean needsCGIchunk(){
        return currentWriteState == WriteStates.RESPONSE_CREATE_CGI_CHUNK;
    }

    public void setSendingHeaders(){
        currentWriteState = WriteStates.RESPONSE_SENDING_HEADERS;
    }
    public void setResponseSent(){
        currentWriteState = WriteStates.RESPONSE_SENT;
    }
    public void setSendingCGI(){
        currentWriteState = WriteStates.RESPONSE_SENDING_CGI;
    }

    public void sendHeaders(SocketChannel client, ByteBuffer outBuffer){
        try{
            int writeBytes = client.write(outBuffer);
            if (outBuffer.remaining() == 0) {
                if(sendbody){
                    currentWriteState = WriteStates.RESPONSE_SENDING_BODY;
                }
                else if(sendcgi){
                    currentWriteState = WriteStates.RESPONSE_CREATE_CGI_CHUNK;
                }
                else{
                    currentWriteState = WriteStates.RESPONSE_SENT;
                }

                Debug.DEBUG("handleWrite: headersSent");
                outBuffer.flip();
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

    public void sendCGI(SocketChannel client, ByteBuffer outBuffer){
        try{
            client.write(outBuffer);
            if (outBuffer.remaining() == 0){
                currentWriteState = WriteStates.RESPONSE_CREATE_CGI_CHUNK;
                outBuffer.flip();
            }
        }
        catch (Exception e){
            // Should Return Server Error
            e.printStackTrace();
        }
    }
    public void sendCGITerminator(SocketChannel client, ByteBuffer outBuffer){
        try{
            client.write(outBuffer);
            if (outBuffer.remaining() == 0){
                currentWriteState = WriteStates.RESPONSE_SENT;
                outBuffer.flip();
            }
            else{
                currentWriteState = WriteStates.RESPONSE_SEND_CGI_TERMINATOR;
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

        // Add Date Header
        String date= OffsetDateTime.now(ZoneId.of("Etc/UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
		String date_header = "Date: " + date + "\r\n";
		appendStringToByteBuf(outBuffer, date_header);

        //Add Server Header
        appendStringToByteBuf(outBuffer, "Server: Yague/1.0 (" + System.getProperty("os.name")+")\r\n");

        //Add remainining headers to be added
		outheaders.forEach((key,value) -> {
			appendStringToByteBuf(outBuffer, key + ":" + value + "\r\n");
		});
		if(sendcgi != true){
            appendStringToByteBuf(outBuffer, "\r\n");
        }

		outBuffer.flip();
	}

    public void generateCGIChunk (ByteBuffer outBuffer, byte[] bytes, int length){
        int remaining_length = length;
        // Byte by Byte send all bytes until we get to the end of the headers denoted by \r\n\r\n.
        if(cgi_headers_sent == false){
            for (int i = 0; i < length; i++){
                remaining_length -= 1;
                outBuffer.put(bytes[i]);

                if (bytes[i] == '\r' || bytes[i] == '\n'){
                    number_of_returns += 1;
                }
                else{
                    number_of_returns = 0;
                }
                if (number_of_returns == 4){
                    cgi_headers_sent = true;
                    i = length;
                }
            }
            if (remaining_length == 0) return;
        }
        appendStringToByteBuf(outBuffer, Integer.toHexString(remaining_length));
        appendStringToByteBuf(outBuffer, "\r\n");
        outBuffer.put(bytes,length - remaining_length,remaining_length);
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
    public void removeOutputHeader(String key){
        outheaders.remove(key);
    }
}
