package server;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.io.IOException;

public interface IAcceptHandler extends IChannelHandler {
	public void handleAccept(SelectionKey key, Selector workSelector) throws IOException;
}