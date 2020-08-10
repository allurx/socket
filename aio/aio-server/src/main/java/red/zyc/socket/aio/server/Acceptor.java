package red.zyc.socket.aio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * 用来接收SocketChannel接收器
 *
 * @author zyc
 */
@Slf4j
public class Acceptor implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

    private static final int LISTEN = 9002;

    public static void main(String[] args) throws IOException, InterruptedException {
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(LISTEN))) {
            Thread.currentThread().setName("Acceptor");
            server.accept(server, new Acceptor());
            Thread.currentThread().join();
        }
    }

    @Override
    public void completed(AsynchronousSocketChannel client, AsynchronousServerSocketChannel server) {

        // 接收到SocketChannel后将其包装成Connection进行读写交替直到通道关闭，注意read、write都是异步执行的
        new Connection(client).read();

        // 递归accept下一个SocketChannel
        server.accept(server, this);
    }

    @Override
    public void failed(Throwable exc, AsynchronousServerSocketChannel server) {
        log.error("获取SocketChannel失败", exc);
    }
}
