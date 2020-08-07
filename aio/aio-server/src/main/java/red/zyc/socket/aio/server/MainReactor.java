package red.zyc.socket.aio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * MainReactor只获取SocketChannel
 *
 * @author zyc
 */
@Slf4j
public class MainReactor implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

    /**
     * 监听的端口
     */
    private static final int LISTEN = 9001;

    public static void main(String[] args) throws IOException {
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(LISTEN))) {
            Thread.currentThread().setName("MainReactor");
            server.accept(server, new MainReactor());
        }
    }

    @Override
    public void completed(AsynchronousSocketChannel client, AsynchronousServerSocketChannel server) {
        server.accept(server, this);
        SubReactor.receiveConnection(client);
    }

    @Override
    public void failed(Throwable exc, AsynchronousServerSocketChannel server) {
        log.error("获取SocketChannel失败", exc);
    }
}
