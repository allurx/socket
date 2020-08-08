package red.zyc.socket.aio.server;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zyc
 */
@Slf4j
@Getter
@Setter
public class Connection implements CompletionHandler<Integer, Integer> {

    /**
     * 读缓冲大小1024 (1 KiB)
     */
    private static final int BUFFER_CAPACITY = 1 << 10;

    /**
     * 读已完成
     */
    private static final int READ_COMPLETED = 1;

    /**
     * 写已完成
     */
    private static final int WRITE_COMPLETED = 2;

    /**
     * 处理业务逻辑的线程池
     */
    private static final ThreadPoolExecutor PROCESS_EXECUTOR = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new NamedThreadFactory("Process"), new RejectedRequestHandler());

    /**
     * 当前连接的id
     */
    private final String id;

    /**
     * 读取请求数据的字节缓冲对象
     */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 服务端与客户端的socket通道
     */
    private final AsynchronousSocketChannel socketChannel;

    /**
     * 请求数据，读取成功能够立马对业务线程可见
     */
    private volatile ByteBuffer request;
    /**
     * 响应数据，写入成功能够立马对当前线程可见
     */
    private volatile ByteBuffer response;

    public Connection(AsynchronousSocketChannel socketChannel) {
        this.id = UUID.randomUUID().toString();
        this.socketChannel = socketChannel;
    }

    /**
     * 与此连接关联的SocketChannel读写操作成功时的回调方法
     *
     * @param result 读取或写入的字节数
     * @param event  触发该方法的事件，读完成还是写完成
     */
    @SneakyThrows
    @Override
    public void completed(Integer result, Integer event) {

        // 客户端已关闭
        if (result == -1) {
            log.info("客户端{}已关闭", clientAddress());
            disconnect();
        } else {

            // 数据读完之后将读取到的数据提交到业务线程池执行
            if (event == READ_COMPLETED) {
                request = simpleDecode();
                PROCESS_EXECUTOR.execute(new ProcessTask(this));

                // 数据写完之后继续执行读
            } else {
                read();
            }
        }
    }

    /**
     * 与此连接关联的SocketChannel读写操作失败时的回调方法
     *
     * @param t     失败的原因
     * @param event 触发该方法的事件，读还是写
     */
    @Override
    public void failed(Throwable t, Integer event) {
        log.error("读写客户端{}发生异常时连接[{}]触发的事件为: [{}]", clientAddress(), id, event, t);
        readBuffer.clear();
        disconnect();
    }

    /**
     * 将与此连接关联的SocketChannel中的数据读取到缓冲区。read方法是异步执行的。
     */
    public void read() {
        socketChannel.read(readBuffer, READ_COMPLETED, this);
    }

    /**
     * 将缓冲区数据写入到与此连接关联的SocketChannel中去。write方法是异步执行的。
     */
    public void write() {
        socketChannel.write(response, WRITE_COMPLETED, this);
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
        try {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            return String.format("[%s:%s]", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    /**
     * 简单的进行一次tcp读的解码，仅仅将{@link #readBuffer}中位置0到position之间的字节包装成一个新的{@link ByteBuffer}返回，
     * 然后调用{@link Buffer#clear()}方法重置position为0。<br><br>
     * 注意：该方法仅仅是为了简单调试用的，实际与客户端进行通讯时我们应当和客户端商量好一个标记位，读到这个标记位则代表一次请求数据读取完毕了，
     * 例如http协议可能会在请求头中定义一个content-length代表一次请求体的长度。
     *
     * @return 当前缓冲中可读的字节
     */
    private ByteBuffer simpleDecode() {
        try {
            return ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position()));
        } finally {
            readBuffer.clear();
        }
    }


}
