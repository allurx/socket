package red.zyc.socket.aio.client;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

/**
 * @author zyc
 */
@Slf4j
public class Client implements CompletionHandler<Integer, Integer> {

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
     * 服务端端口
     */
    private static final int SERVER_PORT = 9002;

    /**
     * 服务端主机地址
     */
    private static final String SERVER_HOST = "localhost";

    /**
     * 客户端与服务端的socket通道
     */
    private final AsynchronousSocketChannel socketChannel;

    /**
     * 读取响应数据的字节缓冲对象
     */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 网络地址
     */
    private final InetSocketAddress inetSocketAddress;

    private final Thread clientThread;

    public Client(AsynchronousSocketChannel socketChannel) throws IOException {
        this.socketChannel = socketChannel;
        this.inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        this.clientThread = Thread.currentThread();
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        try (AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open()) {

            // 阻塞直到与服务端建立连接
            socketChannel.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT)).get();

            // 连接建立后往服务端写信息
            socketChannel.write(ByteBuffer.wrap("我是客户端".getBytes()), WRITE_COMPLETED, new Client(socketChannel));

            // 阻塞main线程避免AsynchronousSocketChannel被try with resource关闭
            LockSupport.park();
        }
    }

    @Override
    public void completed(Integer result, Integer event) {
        if (result == -1) {
            log.info("服务端{}已关闭", serverAddress());
            disconnect();
        } else {
            // 数据写完之后继续读服务端发送过来的信息
            if (event == WRITE_COMPLETED) {
                socketChannel.read(readBuffer, READ_COMPLETED, this);

                // 数据读完之后断开连接
            } else {
                log.info("来自服务端{}的消息: {}", serverAddress(), StandardCharsets.UTF_8.decode(simpleDecode()));
                LockSupport.unpark(clientThread);
                disconnect();
            }
        }
    }

    @Override
    public void failed(Throwable t, Integer event) {
        log.error("读写服务端{}发生异常时触发的事件为: [{}]", serverAddress(), event, t);
        readBuffer.clear();
        disconnect();
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
     * @return 服务端返回的信息
     */
    private ByteBuffer simpleDecode() {
        try {
            return ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position()));
        } finally {
            readBuffer.clear();
        }
    }

    /**
     * @return 服务端地址信息
     */
    public String serverAddress() {
        return String.format("[%s:%s]", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
    }
}
