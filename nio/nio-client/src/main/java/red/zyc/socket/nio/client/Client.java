package red.zyc.socket.nio.client;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

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
     * 读缓冲大小1024 (1KiB)
     */
    private static final int BUFFER_CAPACITY = 1 << 10;

    /**
     * 读字节缓冲
     */
    private static final ByteBuffer READ_BUFFER = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 选择器
     */
    private final Selector selector;

    /**
     * 客户端与服务端的socket通道
     */
    private SocketChannel socketChannel;

    /**
     * 网络地址
     */
    private InetSocketAddress inetSocketAddress;

    public Client(Selector selector) {
        this.selector = selector;
    }

    public static void main(String[] args) throws IOException {
        new Client(Selector.open()).start();
    }

    public void start() throws IOException {
        try (SocketChannel client = SocketChannel.open(new InetSocketAddress(SERVER_HOST, SERVER_PORT))) {
            this.socketChannel = client;
            this.inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);

            // 发送消息到服务端
            socketChannel.write(ByteBuffer.wrap("我是客户端".getBytes()));

            // 监听可读事件
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
                        Optional.ofNullable(simpleDecode()).ifPresent(byteBuffer -> log.info("来自服务端{}的消息: {}", serverAddress(), StandardCharsets.UTF_8.decode(byteBuffer)));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    } finally {
                        socketChannel.close();
                        System.exit(0);
                    }
                }
            }
            selectionKeys.clear();
        }
    }

    /**
     * 从SocketChannel中读取信息到字节缓冲中
     *
     * @return 响应的数据
     * @throws IOException io异常
     */
    private ByteBuffer simpleDecode() throws IOException {
        try {

            // 如果服务端由于断网等原因造成的关闭，那么read方法会抛出一个IOException而不是返回-1。
            // 只有服务端主动调用socketChannel.close()方法read方法才会返回-1。
            int read = socketChannel.read(READ_BUFFER);

            // 客户端通道已关闭
            if (read == -1) {
                log.info(String.format("服务端%s已关闭", serverAddress()));
                socketChannel.close();
                return null;
            }
            return ByteBuffer.wrap(Arrays.copyOfRange(READ_BUFFER.array(), 0, READ_BUFFER.position()));
        } finally {
            READ_BUFFER.clear();
        }
    }

    /**
     * @return 服务端地址信息
     */
    public String serverAddress() {
        return String.format("[%s:%s]", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
    }
}
