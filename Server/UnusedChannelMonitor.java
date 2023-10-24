package server;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class UnusedChannelMonitor implements Runnable {

  ConcurrentHashMap<SocketChannel, Instant> unusedChannelTable;

  public UnusedChannelMonitor(ConcurrentHashMap<SocketChannel, Instant> uCT) {
    unusedChannelTable = uCT;
  }

  public void run() {
    while (!Thread.interrupted()) {

      Iterator<SocketChannel> iterator = unusedChannelTable.keySet().iterator();

      while (iterator.hasNext()) {
        SocketChannel channel = iterator.next();

        try {
          Instant accepted = unusedChannelTable.get(channel);
          long timeSinceAcceptance = Duration.between(accepted, Instant.now()).getSeconds();
          
          if (timeSinceAcceptance >= 3) {
            Debug.DEBUG("Closing unused channel");
            channel.close();
            unusedChannelTable.remove(channel);
          }
        } catch (NullPointerException ex) {
          Debug.DEBUG("Channel received request");
        } catch (IOException ex) {
          System.out.println("Error closing unused channel: " + channel);
          ex.printStackTrace();
        }
      } // end of while iterator.hasNext()
    } // end of while (true)
  } // end of run
} // end of class
