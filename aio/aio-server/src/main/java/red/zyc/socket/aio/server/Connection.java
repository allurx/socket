package red.zyc.socket.aio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.UUID;

/**
 * @author zyc
 */
@Slf4j
public class Connection {

    /**
     * 当前连接的id
     */
    private final String id;

    /**
     * 服务端与客户端的socket通道
     */
    private final AsynchronousSocketChannel socketChannel;

    /**
     * 当前socket通道的网络地址
     */
    private final InetSocketAddress inetSocketAddress;

    /**
     * 请求数据，SubReactor线程每次读取成功能够立马对业务线程可见
     */
    private volatile ByteBuffer request;

    /**
     * 响应数据，业务线程每次写入成功能够立马对SubReactor线程可见
     */
    private volatile ByteBuffer response;

    public Connection(AsynchronousSocketChannel socketChannel) {
        try {
            this.id = UUID.randomUUID().toString();
            this.socketChannel = socketChannel;
            this.inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * @return 客户端地址信息
     */
    public String clientAddress() {
        return String.format("[%s:%s]", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
    }
}
