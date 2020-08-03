package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * @author zyc
 */
@Slf4j
public class Connection {

    private final Server server;

    /**
     * 服务端与客户端的socket通道
     */
    private final SocketChannel socketChannel;

    private final SelectionKey selectionKey;

    /**
     * 读字节缓冲
     */
    private static final ByteBuffer READ_BUFFER = ByteBuffer.allocate(1024);

    /**
     * 写字节缓冲
     */
    private static final ByteBuffer WRITE_BUFFER = ByteBuffer.allocate(1024);

    public Connection(Server server, SocketChannel socketChannel, SelectionKey selectionKey) {
        this.server = server;
        this.socketChannel = socketChannel;
        this.selectionKey = selectionKey;
    }

    public void readClientMessage() {
        try {
            readBuffer();
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            String message = new String(READ_BUFFER.array());
            log.info("来自客户端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), message);
        } catch (Exception e) {
            server.getConnections().remove(this);
            throw new ServerException(e);
        } finally {
            READ_BUFFER.clear();
        }
    }

    /**
     * 将消息发送给客户端
     *
     * @param message 消息
     */
    public void writeMessageToClient(String message) {
        try {
            socketChannel.write(ByteBuffer.wrap(message.getBytes()));
        } catch (Exception e) {
            server.getConnections().remove(this);
            throw new ServerException(e);
        } finally {
            WRITE_BUFFER.clear();
        }
    }

    /**
     * 从socket通道中读取信息到字节缓冲中
     *
     * @throws IOException io异常
     */
    private void readBuffer() throws IOException {
        int read = socketChannel.read(READ_BUFFER);
        if (read <= 0) {
            log.info("通道已关闭");
            selectionKey.cancel();
        }
    }

}
