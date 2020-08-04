package red.zyc.socket.nio.server;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zyc
 */
@Slf4j
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
     * 测试通道数据是否异常的字节缓冲
     */
    private static final ByteBuffer READ_OVERFLOW_BUFFER = ByteBuffer.allocate(1);

    /**
     * {@link Server}
     */
    private final Server server;

    /**
     * 服务端与客户端的socket通道
     */
    private final SocketChannel socketChannel;

    /**
     * 处理业务逻辑的线程池
     */
    private final ExecutorService processExecutor = new ThreadPoolExecutor(10, 10, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());

    public Connection(Server server, SocketChannel socketChannel) {
        this.server = server;
        this.socketChannel = socketChannel;
    }

    /**
     * 读客户端信息
     */
    public void readClientMessage() {
        try {

            // 读取socket通道中的数据
            ByteBuffer data = readBuffer();

            // 业务逻辑放到线程池中执行
            processExecutor.execute(() -> process(data));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            disconnect();
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
    public void writeMessageToClient(String message) {
        try {
            socketChannel.write(ByteBuffer.wrap(message.getBytes()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            disconnect();
        }
    }

    /**
     * 处理业务逻辑，非io操作通常是放在线程池里执行的。
     * 这里我们仅仅输出了客户端的消息。
     *
     * @param data 请求数据
     */
    public void process(ByteBuffer data) {
        try {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            log.info("来自客户端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), StandardCharsets.UTF_8.decode(data));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }finally {
            disconnect();
        }
    }

    /**
     * 从socket通道中读取信息到字节缓冲中
     *
     * @return 请求的数据
     * @throws IOException io异常
     */
    private ByteBuffer readBuffer() throws IOException {

        // 未读满缓冲区
        if (socketChannel.read(READ_BUFFER) < READ_BUFFER.limit()) {
            return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
        }
        // 通道中还有数据未读
        if (socketChannel.read(READ_OVERFLOW_BUFFER) > 0) {
            throw new ServerException("请求数据太大");
        }
        // 刚好读满缓冲区
        return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
    }

    /**
     * 断开连接
     */
    private void disconnect() {
        try {
            server.getConnections().remove(this);
            socketChannel.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 任务队列已满时拒绝socket连接。
     */
    private class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

        @SneakyThrows
        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.error("服务端负载已满");
            disconnect();
        }
    }

}
