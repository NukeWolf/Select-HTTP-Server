package server;

import java.nio.channels.*;
import java.util.Scanner;
import java.net.*;

import java.io.IOException;

public class Server {

	public static ServerConfig serverConfig;

	public static ServerSocketChannel openServerChannel(int port) {
		ServerSocketChannel serverChannel = null;
		try {

			// open server socket for accept
			serverChannel = ServerSocketChannel.open();

			// extract server socket of the server channel and bind the port
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(port);
			ss.bind(address);

			// configure it to be non blocking
			serverChannel.configureBlocking(false);

			Debug.DEBUG("Server listening for connections on port " + port);

		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		} // end of catch

		return serverChannel;
	} // end of open serverChannel

	public static void main(String[] args) {

		// configure server
		serverConfig = new ServerConfig();
		if (args.length == 2) {
			serverConfig.parseConfigurationFile(args[1]);
		}

		// open server socket channel
		ServerSocketChannel sch = openServerChannel(serverConfig.getPort());

		// get dispatcher/selector
		Dispatcher dispatcher = new Dispatcher(sch, serverConfig.getNSelectLoops());

		// create server acceptor
		ISocketReadWriteHandlerFactory readFactory = new HTTP1ReadWriteHandlerFactory();
		Acceptor acceptor = new Acceptor(readFactory);

		Thread dispatcherThread = new Thread(dispatcher);
		dispatcherThread.start();
		// may need to join the dispatcher thread

		try(Scanner scanner = new Scanner(System.in)) {
			while(true) {
				System.out.print("server> ");
				String line = scanner.nextLine().trim();
				if (line.equals("SHUTDOWN")) {
					System.out.println("Shutting down");
					try {
						dispatcher.gracefulShutdown();
					}
					catch (IOException ex) {
						System.out.println("Error shutting down");
						ex.printStackTrace();
						System.exit(1);
					}
					return;
				}
			}
		}

	} // end of main

} // end of class
