package server;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*; // for Set and Iterator and ArrayList

public class Worker implements Runnable {

	private Selector selector;

	public Worker(Selector s) {
		// create selector
    selector = s;
	} // end of Dispatcher

	public Selector selector() {
		return selector;
	}
	/*
	 * public SelectionKey registerNewSelection(SelectableChannel channel,
	 * IChannelHandler handler, int ops) throws ClosedChannelException {
	 * SelectionKey key = channel.register(selector, ops); key.attach(handler);
	 * return key; } // end of registerNewChannel
	 * 
	 * public SelectionKey keyFor(SelectableChannel channel) { return
	 * channel.keyFor(selector); }
	 * 
	 * public void deregisterSelection(SelectionKey key) throws IOException {
	 * key.cancel(); }
	 * 
	 * public void updateInterests(SelectionKey sk, int newOps) {
	 * sk.interestOps(newOps); }
	 */

		// need these debug statements to run correctly for some reason????
	public void run() {

		while (true) {
			// Debug.DEBUG("Enter selection");
			try {
				// check to see if any events
				selector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}

							Debug.DEBUG("worker 1");
			// readKeys is a set of ready events
			Set<SelectionKey> readyKeys = selector.selectedKeys();


							Debug.DEBUG("worker 2");

			// create an iterator for the set
			Iterator<SelectionKey> iterator = readyKeys.iterator();


							Debug.DEBUG("worker 3");

			// iterate over all events
			while (iterator.hasNext()) {
							Debug.DEBUG("worker 4");

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
							Debug.DEBUG("worker 5");
				
		} // end of while (true)
							Debug.DEBUG("worker 6");
	} // end of run
}