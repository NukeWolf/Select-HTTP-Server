package Server;

import java.io.IOException;
import java.nio.channels.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class Dispatcher implements Runnable {

	private ServerSocketChannel sch;
	private Selector connectionSelector;


	public volatile boolean shutdown;
	private ConcurrentLinkedQueue<SocketChannel> clientSocketQueue;
	private ConcurrentHashMap<SocketChannel, Instant> unusedChannelTable;
	private ConcurrentLinkedQueue<Runnable> commandQueue;

	public UnusedChannelMonitor unusedChannelMonitor;
	public Thread unusedChannelMonitorThread;

	public Worker[] workers;
	public ThreadPoolExecutor workerThreads;

	public Dispatcher(ServerSocketChannel sch, int nSelectLoops) {
		// add listening channels to selector
		try {
			connectionSelector = Selector.open();
			this.sch = sch;
			this.sch.register(connectionSelector, SelectionKey.OP_ACCEPT);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		// initialize command structures
		shutdown = false;
		clientSocketQueue = new ConcurrentLinkedQueue<SocketChannel>();
		unusedChannelTable = new ConcurrentHashMap<SocketChannel, Instant>();
		commandQueue = new ConcurrentLinkedQueue<Runnable>();

		unusedChannelMonitor = new UnusedChannelMonitor(unusedChannelTable);
		unusedChannelMonitorThread = new Thread(unusedChannelMonitor);
		unusedChannelMonitorThread.start();

		// start worker selector loops
		workers = new Worker[nSelectLoops];
		workerThreads = (ThreadPoolExecutor) Executors.newFixedThreadPool(nSelectLoops);
		for (int i = 0; i < nSelectLoops; i++) {
			workers[i] = new Worker(clientSocketQueue, unusedChannelTable);
			Debug.DEBUG("selector: " + workers[i].getSelector());

			workerThreads.execute(workers[i]);
		}
	} // end constructor

	// best way?
	public void wakeWorkers() {
		for (Worker w : workers) w.getSelector().wakeup();
	}

	// attach this to an acceptor somehow? --> tbh don't really need to
	public void handleAccept(SelectionKey key) throws IOException {
		// configure client channel
		ServerSocketChannel server = (ServerSocketChannel) key.channel();
		SocketChannel client = server.accept();
		client.configureBlocking(false);

		// add to shared datastructures
		clientSocketQueue.add(client);	// this line keeps it from being static
		unusedChannelTable.put(client, Instant.now());

		wakeWorkers();
		Debug.DEBUG("Added connection from " + client + " to client queue and channel monitor");
	}

	public void run() {

		// new connection selector loop
		while (!shutdown) {

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
						handleAccept(key);
					}
				} catch (IOException e) {
					System.out.println("Exception when handling key " + key);
					key.cancel();
					try {
						key.channel().close();
					}
					catch (IOException ex) {
						System.out.println("Failed to close problematic accept channel: " + key.channel());
					}
				} // end try-catch
			} // end iterator.hasNext()
			Debug.DEBUG("Finished accept loop");

			// // add a command queue
			while (!commandQueue.isEmpty()) {
			}

		} // end of while (true)
		Debug.DEBUG("Terminating Dispatcher");
	} // end of run

	// gracefulShutdown
	public void gracefulShutdown() throws IOException {
		// call wakeWorkers until client queue is empty

		// handler needs to tell if the requests are complete
		for (Worker w : workers) {
			w.shutdown();
			w.getSelector().wakeup();
		}
		Debug.DEBUG("Waiting on workers");
		workerThreads.shutdown();
		Debug.DEBUG("Waiting on channel monitor");
		unusedChannelMonitorThread.interrupt();

		shutdown = true;
		sch.close();
		connectionSelector.wakeup(); // unblock
		return;
	}

} // end of dispatcher