package red.zyc.socket.nio.client;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.ServerException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zyc
 */
@Slf4j
public class Client {

    /**
     * 服务端端口
     */
    private static final int SERVER_PORT = 9001;

    /**
     * 服务端主机地址
     */
    private static final String SERVER_HOST = "localhost";

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
     * 选择器
     */
    private final Selector selector;

    /**
     * 往服务端写消息的线程池
     */
    private final ExecutorService producer = Executors.newFixedThreadPool(1);

    /**
     * 客户端与服务端的socket通道
     */
    private SocketChannel socketChannel;

    public Client(Selector selector) {
        this.selector = selector;
    }

    public static void main(String[] args) throws IOException {
        new Client(Selector.open()).start();
    }

    public void start() throws IOException {
        try (SocketChannel client = SocketChannel.open(new InetSocketAddress(SERVER_HOST, SERVER_PORT))) {
            this.socketChannel = client;
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            writeMessageToServer();
            readServerMessage();
        }
    }

    /**
     * 读取服务端发送过来的消息
     */
    private void readServerMessage() throws IOException {
        while (!Thread.interrupted()) {
            int select = selector.select();
            if (select == 0) {
                continue;
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            for (SelectionKey selectionKey : selectionKeys) {
                if (selectionKey.isValid() && selectionKey.isReadable()) {
                    try {
                        ByteBuffer response = readBuffer();
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                        log.info("来自服务端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), StandardCharsets.UTF_8.decode(response));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        System.exit(0);
                    }
                }
            }
            selectionKeys.clear();
        }
    }


    /**
     * 将控制台输入消息发送到服务端
     */
    private void writeMessageToServer() {
        producer.execute(() -> {
            try (Scanner scanner = new Scanner(System.in)) {

                // 阻塞直到控制台有满足条件的输入
                while (scanner.hasNext()) {
                    String message = scanner.next();
                    socketChannel.write(ByteBuffer.wrap(message.getBytes()));
                }
            } catch (Exception e) {
                log.error("往服务端写消息时发生异常", e);
                System.exit(0);
            }
        });
    }

    /**
     * 从socket通道中读取信息到字节缓冲中
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
                throw new ServerException("响应数据太大");
            }
            // 刚好读满缓冲区
            return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
        } finally {
            READ_BUFFER.clear();
            READ_OVERFLOW_BUFFER.clear();
        }

    }
}
