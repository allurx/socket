package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * @author zyc
 */
@Slf4j
@Getter
public class Connection {

    /**
     * 读缓冲大小1024 * 1024 (1MiB)
     */
    private static final int BUFFER_CAPACITY = 1 << 20;

    /**
     * 读字节缓冲
     */
    private static final ByteBuffer READ_BUFFER = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 用来测试通道数据是否溢出的字节缓冲
     */
    private static final ByteBuffer READ_OVERFLOW_BUFFER = ByteBuffer.allocate(1);

    /**
     * 当前连接的id
     */
    private final String id;

    /**
     * {@link Server}
     */
    private final Server server;

    /**
     * 服务端与客户端的socket通道
     */
    private final SocketChannel socketChannel;

    /**
     * 当前socket通道的网络地址
     */
    private final InetSocketAddress inetSocketAddress;

    /**
     * 连接创建时间
     */
    private final LocalDateTime createdTime;


    public Connection(String id, Server server, SocketChannel socketChannel) throws IOException {
        this.id = id;
        this.server = server;
        this.socketChannel = socketChannel;
        this.inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        this.createdTime = LocalDateTime.now();
    }

    /**
     * 从socket通道中读取信息到字节缓冲中。<br>
     * 注意如果在执行这个方法之前客户端已经连续发送了多次数据到socket通道中，那么就会一次性将这几次发送的数据都读出来。
     * 如果要区分一次客户端请求，那么我们必须和客户端商量好一个标记位，读到这个标记位则代表一次请求数据读取完毕了，例如http协议
     * 可能会在请求头中定义一个content-length代表一次请求体的长度。
     *
     * @return 请求的数据
     * @throws IOException io异常
     */
    public ByteBuffer readData() throws IOException {
        try {
            int read = socketChannel.read(READ_BUFFER);
            // 通道已关闭
            if (read == -1) {
                socketChannel.close();
                throw new ClosedChannelException();
            }
            // 未读满缓冲区
            if (read < READ_BUFFER.limit()) {
                return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
            }
            // 通道中还有数据未读
            if (socketChannel.read(READ_OVERFLOW_BUFFER) > 0) {
                throw new ServerException("请求数据太大");
            }
            // 刚好读满缓冲区
            return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
        } finally {
            READ_BUFFER.clear();
            READ_OVERFLOW_BUFFER.clear();
        }
    }

    /**
     * 将消息发送给客户端
     *
     * @param message 消息
     */
    public void writeData(String message) {
        try {
            socketChannel.write(ByteBuffer.wrap(message.getBytes()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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

}
