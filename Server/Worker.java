package Server;

import java.nio.channels.*;
import java.time.Instant;
import java.io.IOException;
import java.util.*; // for Set and Iterator and ArrayList
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import Server.HTTP1ReadHandler;
import Server.HTTP1ReadWriteHandler;

public class Worker implements Runnable {

	private ConcurrentLinkedQueue<SocketChannel> clientSocketQueue;
	private ConcurrentHashMap<SocketChannel, Instant> unusedChannelTable;

	private Selector selector;

	private boolean shutdown;

	public Worker(ConcurrentLinkedQueue<SocketChannel> cSQ, ConcurrentHashMap<SocketChannel, Instant> uCT) {
		clientSocketQueue = cSQ;
		unusedChannelTable = uCT;
		try {
			selector = Selector.open();
		} catch (IOException ex) {
			System.out.println("Cannot create selector for worker");
			ex.printStackTrace();
			System.exit(1);
		}
		shutdown = false;
	}

	public Selector getSelector() {
		return selector;
	}

	public void run() {

		while (!shutdown) {
			try {
				// check for new channels to add to this worker
				SocketChannel cch = clientSocketQueue.poll();
				if (cch != null) {
					HTTP1ReadWriteHandler handler= new HTTP1ReadWriteHandler();
					SelectionKey key = cch.register(selector, handler.getInitOps());
					key.attach(handler);
				}
				selector.select(); 
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}

			// readKeys is a set of ready events
			Set<SelectionKey> readyKeys = selector.selectedKeys();

			// create an iterator for the set
			Iterator<SelectionKey> iterator = readyKeys.iterator();

			// iterate over all events (replace with for-each?)
			while (iterator.hasNext()) {

				SelectionKey key = (SelectionKey) iterator.next();
				iterator.remove();

				try {
					if (key.isReadable() || key.isWritable()) {
						IReadWriteHandler rwH = (IReadWriteHandler) key.attachment();

						if (key.isReadable()) {
							rwH.handleRead(key);
						} // end of if isReadable

						if (key.isValid() && key.isWritable()) {
							unusedChannelTable.remove(key.channel());
							rwH.handleWrite(key);
						} // end of if isWritable
					} // end of readwrite

				} 
				catch (CancelledKeyException ex) {
					// not actually an issue, 
				}
				catch (IOException ex) {
					Debug.DEBUG("IOException when handling key " + key);
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException cex) {
						System.out.println("Failed to close problematic channel on read-write");
					}
				} // end of catch
			} // end of while (iterator.hasNext())

		} // end of while (true)
		// Debug.DEBUG("Finished worker run");
	} // end of run

	public void shutdown() {
		shutdown = true;
	}
}