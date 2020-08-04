package red.zyc.socket.nio.server;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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
     * 测试通道数据是否溢出的字节缓冲
     */
    private static final ByteBuffer READ_OVERFLOW_BUFFER = ByteBuffer.allocate(1);
    /**
     * 处理业务逻辑的线程池
     */
    private static final ExecutorService PROCESS_EXECUTOR = new ThreadPoolExecutor(10, 10, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());
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


    public Connection(String id, Server server, SocketChannel socketChannel) {
        this.id = id;
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
            PROCESS_EXECUTOR.execute(new ProcessTask(data));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
            disconnect();
        }
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
    private ByteBuffer readBuffer() throws IOException {
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
     * 断开连接
     */
    private void disconnect() {
        try {
            server.getConnections().remove(id);
            socketChannel.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 任务队列已满时拒绝socket连接。
     */
    private static class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

        @SneakyThrows
        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.error("服务端负载已满");
            ProcessTask processTask = (ProcessTask) runnable;
            processTask.connection.disconnect();
        }
    }

    /**
     * 处理业务逻辑，非io操作通常是放在线程池里执行的。
     * 这里我们仅仅输出了客户端的消息。
     */
    private class ProcessTask implements Runnable {

        private final ByteBuffer data;

        private final Connection connection = Connection.this;

        ProcessTask(ByteBuffer data) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                log.info("来自客户端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), StandardCharsets.UTF_8.decode(data));
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            } finally {
                disconnect();
            }
        }
    }

}
