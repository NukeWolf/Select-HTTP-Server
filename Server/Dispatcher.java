package server;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*; // for Set and Iterator and ArrayList

public class Dispatcher implements Runnable {

	private Selector selectLoops[];
	private int currentWorkerSelector;
	private Selector selector;

	public Dispatcher(int nSelectLoops) {
		// create selector
		try {
			selector = Selector.open();
			selectLoops = new Selector[nSelectLoops];
			Debug.DEBUG("nSelectLoops: " + selectLoops.length);

			for (int i = 0; i < nSelectLoops; i++) {
				Selector s = Selector.open();
				selectLoops[i] = s;
				Thread workThread = new Thread(new Worker(s));
				workThread.start();
			}
			currentWorkerSelector = 0;
			
		} catch (IOException ex) {
			System.out.println("Cannot create selector.");
			ex.printStackTrace();
			System.exit(1);
		} // end of catch
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

	public void run() {

		while (true) {
			
			try {
				// check to see if any events
				selector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}

			// readKeys is a set of ready events
			Set<SelectionKey> readyKeys = selector.selectedKeys();

			// create an iterator for the set
			Iterator<SelectionKey> iterator = readyKeys.iterator();

			// iterate over all events
			while (iterator.hasNext()) {

				SelectionKey key = (SelectionKey) iterator.next();
				iterator.remove();

				try {
					if (key.isAcceptable()) { // a new connection is ready to be
												// accepted
						IAcceptHandler aH = (IAcceptHandler) key.attachment();
						aH.handleAccept(key, selectLoops[currentWorkerSelector]);
						currentWorkerSelector = (currentWorkerSelector+1)%selectLoops.length;
						Debug.DEBUG("accepted connection -- assigning to worker thread ("+currentWorkerSelector+")");
					} // end of isAcceptable

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

		} // end of while (true)
	} // end of run
}