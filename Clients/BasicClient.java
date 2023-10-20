package Clients;

import java.net.*;
import java.io.*;


import javax.net.ssl.HttpsURLConnection;

public class BasicClient {
    public static void main(String[] args){
        try{
            URL server_url = new URL("http","localhost",6789,"/");
            HttpURLConnection con = (HttpURLConnection) server_url.openConnection();
            System.out.println("Response Code:"
                                   + con.getResponseCode());
                System.out.println(
                    "Response Message:"
                    + con.getResponseMessage());

            Socket clientSocket = new Socket("localhost", 6789);
            BufferedReader inFromUser =
			new BufferedReader(new InputStreamReader(System.in));
            String sentence = inFromUser.readLine();

            // write to server
            DataOutputStream outToServer 
            = new DataOutputStream(clientSocket.getOutputStream());
            outToServer.writeBytes(sentence + '\n');

                //BufferedOutputStream bos = new BufferedOutputStream( clientSocket.getOutputStream() );
            //DataOutputStream outToServer = new DataOutputStream( bos );
            //outToServer.writeBytes(sentence + '\n');

            // outToServer.flush();

            System.out.println("written to server; waiting for server reply...");

            // create read stream and receive from server
            BufferedReader inFromServer 
            = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String sentenceFromServer = inFromServer.readLine();

            // print output
            System.out.println("From Server: " + sentenceFromServer);

            // close client socket
            clientSocket.close();



        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
       
    }
}
