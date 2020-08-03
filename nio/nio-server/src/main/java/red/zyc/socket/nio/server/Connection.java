package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author zyc
 */
@Slf4j
public class Connection {

    /**
     * 读字节缓冲
     */
    private static final ByteBuffer READ_BUFFER = ByteBuffer.allocate(1024);

    /**
     * {@link Server}
     */
    private final Server server;
    /**
     * 服务端与客户端的socket通道
     */
    private final SocketChannel socketChannel;

    public Connection(Server server, SocketChannel socketChannel) {
        this.server = server;
        this.socketChannel = socketChannel;
    }

    public void readClientMessage() {
        try {
            if (readBuffer()) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                String message = new String(READ_BUFFER.array(), 0, READ_BUFFER.position());
                log.info("来自客户端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), message);
            }
        } catch (Exception e) {
            disconnect();
            throw new ServerException(e);
        } finally {
            READ_BUFFER.clear();
            disconnect();
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
            disconnect();
            throw new ServerException(e);
        }
    }

    /**
     * 从socket通道中读取信息到字节缓冲中
     *
     * @return 是都读成功
     * @throws IOException io异常
     */
    private boolean readBuffer() throws IOException {
        int read = socketChannel.read(READ_BUFFER);
        if (read <= 0) {
            log.info("通道已关闭");
            disconnect();
            return false;
        }
        return true;
    }

    /**
     * 断开连接
     */
    private void disconnect() {
        try {
            server.getConnections().remove(this);
            socketChannel.close();
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

}
