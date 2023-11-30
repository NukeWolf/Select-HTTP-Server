package Server;
public class HTTP1ReadWriteHandlerFactory implements ISocketReadWriteHandlerFactory {
	public IReadWriteHandler createHandler() {
		return new HTTP1ReadWriteHandler();
	}
}
