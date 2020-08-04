package red.zyc.socket.nio.client;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
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
            client.configureBlocking(false);
            this.socketChannel = client;
            client.register(selector, SelectionKey.OP_READ);
            writeMessageToServer();
            readServerMessage();
        }
    }

    /**
     * 读取服务端发送过来的消息
     */
    private void readServerMessage() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        while (!Thread.interrupted()) {
            int select = selector.select();
            if (select == 0) {
                continue;
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            for (SelectionKey selectionKey : selectionKeys) {
                if (selectionKey.isReadable()) {
                    int read = socketChannel.read(byteBuffer);
                    if (read <= 0) {
                        socketChannel.close();
                        selectionKey.cancel();
                    } else {
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                        log.info("来自服务端[{}:{}]的消息: {}", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), new String(byteBuffer.array(), 0, byteBuffer.position()));
                    }
                    byteBuffer.clear();
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
            } finally {
                System.exit(0);
            }
        });
    }
}
