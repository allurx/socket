package red.zyc.socket.aio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @author zyc
 */
@Slf4j
public class Acceptor implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

    private static final int LISTEN = 9001;

    public static void main(String[] args) throws IOException, InterruptedException {
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(LISTEN))) {
            Thread.currentThread().setName("Acceptor");
            server.accept(server, new Acceptor());
            Thread.currentThread().join();
        }
    }

    @Override
    public void completed(AsynchronousSocketChannel client, AsynchronousServerSocketChannel server) {
        server.accept(server, this);
        new Connection(client).read();
    }

    @Override
    public void failed(Throwable exc, AsynchronousServerSocketChannel server) {
        log.error("获取SocketChannel失败", exc);
    }
}
