package Server;

import java.util.concurrent.atomic.AtomicInteger;

public class HTTP1ReadWriteHandlerFactory implements ISocketReadWriteHandlerFactory {
	public IReadWriteHandler createHandler() {
		return new HTTP1ReadWriteHandler(ServerConfig.getInstance(), new AtomicInteger(0));
	}
}
