package server;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

public class Dispatcher implements Runnable {

	private Selector connectionSelector;

	private ServerSocketChannel sch;

	private ConcurrentLinkedQueue<SocketChannel> clientSocketQueue;
	private ConcurrentLinkedQueue<Runnable> commandQueue;

	public volatile boolean shutdown;

	public Worker[] workers;
	public ThreadPoolExecutor workerThreads;

	public Dispatcher(ServerSocketChannel sch, int nSelectLoops) {
		this.sch = sch;
		try {
			connectionSelector = Selector.open();
			this.sch.register(connectionSelector, SelectionKey.OP_ACCEPT);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		clientSocketQueue = new ConcurrentLinkedQueue<SocketChannel>();
		commandQueue = new ConcurrentLinkedQueue<Runnable>();

		shutdown = false;

		// start worker selector loops
		workers = new Worker[nSelectLoops];
		workerThreads = (ThreadPoolExecutor) Executors.newFixedThreadPool(nSelectLoops);
		for (int i = 0; i < nSelectLoops; i++) {
			workers[i] = new Worker(clientSocketQueue);
			Debug.DEBUG("selector: " + workers[i].getSelector());

			workerThreads.execute(workers[i]);
		}
	} // end constructor

	public void run() {

		// new connection selector loop
		while (true) {

			// try {
			// SocketChannel clientSocketChannel = sch.accept();
			// if (clientSocketChannel != null) {
			// clientSocketChannel.configureBlocking(false);
			// clientSocketQueue.add(clientSocketChannel);
			// }
			// } catch (IOException ex) {
			// ex.printStackTrace();
			// System.exit(1);
			// }

			try {
				connectionSelector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}

			Set<SelectionKey> readyKeys = connectionSelector.selectedKeys();
			Iterator<SelectionKey> iterator = readyKeys.iterator();

			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();

				try {
					if (key.isAcceptable()) {
						acceptConnection(key);
					}
				} catch (IOException e) {
					System.out.println("Exception when handling key " + key);
					key.cancel();
					try {
						key.channel().close();
					}
					catch (IOException ex) {
					}
				} // end try-catch
			} // end iterator.hasNext()
			
			Debug.DEBUG("Processed all accept events");

			// // add a command queue
			while (!commandQueue.isEmpty()) {
			}

			if (shutdown) {
				break;
			}

		} // end of while (true)
		Debug.DEBUG("Finshed dispatcher run");
	} // end of run

	// attach this to an acceptor somehow?
	public void acceptConnection(SelectionKey key) throws IOException {
		ServerSocketChannel server = (ServerSocketChannel) key.channel();
		SocketChannel client = server.accept();
		client.configureBlocking(false);
		clientSocketQueue.add(client);	// this line keeps it from being static
		wakeWorkers();
		Debug.DEBUG("Added connection from " + client + " to client queue");
	}

	public void wakeWorkers() {
		for (Worker w : workers) w.getSelector().wakeup();
	}

	// gracefulShutdown
	public void gracefulShutdown() throws IOException {
		for (Worker w : workers) {
			w.shutdown();
			w.getSelector().wakeup();
		}
		Debug.DEBUG("Waiting on workers");
		workerThreads.shutdown();
		Debug.DEBUG("DONEISH");

		shutdown = true;
		sch.close();
		connectionSelector.wakeup();
		return;
	}

} // end of dispatcher