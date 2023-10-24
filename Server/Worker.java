package server;

import java.nio.channels.*;
import java.io.IOException;
import java.util.*; // for Set and Iterator and ArrayList
import java.util.concurrent.ConcurrentLinkedQueue;

public class Worker implements Runnable {

	private ConcurrentLinkedQueue<SocketChannel> clientSocketQueue;

	private Selector selector;

	private boolean shutdown;

	public Worker(ConcurrentLinkedQueue<SocketChannel> clientSocketQueue) {
		this.clientSocketQueue = clientSocketQueue;
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

		while (true) {
			try {
				// try to add new connection
				SocketChannel cch = clientSocketQueue.poll();
				if (cch != null) {
					SelectionKey key = cch.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					key.attach(new HTTP1ReadWriteHandler());
				}
				// check to see if any events
				selector.select(); // better way to do this?
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

						if (key.isWritable()) {
							rwH.handleWrite(key);
						} // end of if isWritable
					} // end of readwrite

				} catch (IOException ex) {
					Debug.DEBUG("Exception when handling key " + key);
					key.cancel();
					try {
						key.channel().close();
						// in a more general design, call have a handleException
					} catch (IOException cex) {
					}
				} // end of catch
			} // end of while (iterator.hasNext()) {

			if (shutdown) {
				Debug.DEBUG("Shutting down worker");
				break;
			}

		} // end of while (true)
		Debug.DEBUG("Finished run");
	} // end of run

	public void shutdown() {
		shutdown = true;
	}
}