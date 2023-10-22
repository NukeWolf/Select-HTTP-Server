package server;

import java.nio.channels.*;
import java.net.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

		// get dispatcher/selector
		Dispatcher dispatcher = new Dispatcher();

		// open server socket channel
		ServerSocketChannel sch = openServerChannel(serverConfig.getPort());

		// create server acceptor for Echo Line ReadWrite Handler
		// ISocketReadWriteHandlerFactory echoFactory = new EchoLineReadWriteHandlerFactory();
		ISocketReadWriteHandlerFactory readFactory = new HTTP1ReadWriteHandlerFactory();
		Acceptor acceptor = new Acceptor(readFactory);

		Thread dispatcherThread;
		// register the server channel to a selector
		try {
			SelectionKey key = sch.register(dispatcher.selector(), SelectionKey.OP_ACCEPT);
			key.attach(acceptor);

			// start dispatcher
			dispatcherThread = new Thread(dispatcher);
			dispatcherThread.start();
		} catch (IOException ex) {
			System.out.println("Cannot register and start server");
			System.exit(1);
		}
		// may need to join the dispatcher thread

	} // end of main

} // end of class
