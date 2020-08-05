package red.zyc.socket.nio.server;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

/**
 * @author zyc
 */
public class SubReactor implements Runnable {

    private final ServerSocketChannel serverSocketChannel;

    private final Selector selector;

    public SubReactor(ServerSocketChannel serverSocketChannel) throws IOException {
        this.serverSocketChannel = serverSocketChannel;
        this.selector = Selector.open();
    }

    @Override
    public void run() {

    }
}
