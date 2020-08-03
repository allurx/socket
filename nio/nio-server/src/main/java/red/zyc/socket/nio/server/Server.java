package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 响应器
 *
 * @author zyc
 */
@Slf4j
@Getter
public class Server {

    /**
     * 服务端监听的端口
     */
    private static final int LISTEN = 9001;

    /**
     * 服务端socket通道
     */
    private ServerSocketChannel serverSocketChannel;

    /**
     * 选择器
     */
    private final Selector selector;

    /**
     * 所有客户端连接
     */
    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    /**
     * 往客户端写消息的线程池
     */
    private final ExecutorService producer = Executors.newFixedThreadPool(1);

    public Server(Selector selector) {
        this.selector = selector;
    }

    public static void main(String[] args) throws IOException {
        new Server(Selector.open()).start();
    }

    public void start() throws IOException {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {

            this.serverSocketChannel = server;

            // 监听本地端口
            serverSocketChannel.bind(new InetSocketAddress(LISTEN));

            // 与Selector一起使用时，Channel必须处于非阻塞模式下
            serverSocketChannel.configureBlocking(false);

            // 向选择器注册感兴趣的事件，可以用“位或”操作符将常量连接起来SelectionKey.OP_READ | SelectionKey.OP_WRITE。
            // 返回值代表此通道在该选择器中注册的键
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            writeMessageToClient();
            readClientMessage();
        }
    }

    /**
     * 读客户端信息
     *
     * @throws IOException io异常
     */
    private void readClientMessage() throws IOException {

        while (!Thread.interrupted()) {

            // 只要selectedKeys不为空就会返回，否则阻塞直到有一个已注册的通道上有满足条件的事件就绪，
            // 或者selector的wakeup方法被调用或者当前线程被中断。
            // 方法返回的int值表示自上次select以来新的已经就绪的通道数。
            // 如果没有把上一次已处理的SelectionKey移除掉，那么下一次循环select方法就会立马返回，因为selectedKeys不为空。
            // 这是一个坑，一定要在迭代结束后移除已处理的SelectionKey
            int select = selector.select();
            if (select == 0) {
                continue;
            }

            // 当前选择器中所有符合事件的选择键
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            // 遍历所有准备就绪的SelectionKey
            while (iterator.hasNext()) {

                SelectionKey selectionKey = iterator.next();

                // 当前SelectionKey的通道能够获取一个新的socket connection事件
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);

                    // 将这个socket通道注册到selector中，监听读事件
                    SelectionKey register = socketChannel.register(selector, SelectionKey.OP_READ);

                    InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                    log.info("客户端[{}:{}]已连接", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
                    Connection connection = new Connection(this, socketChannel, selectionKey);
                    connections.add(connection);
                    register.attach(connection);

                    // 当前SelectionKey的通道能够读取事件
                } else if (selectionKey.isReadable()) {
                    Connection connection = (Connection) selectionKey.attachment();
                    connection.readClientMessage();
                }
                // 一定要移除掉
                iterator.remove();
            }
        }
    }

    /**
     * 将控制台输入消息发送到服务端
     */
    private void writeMessageToClient() {
        producer.execute(() -> {
            try (Scanner scanner = new Scanner(System.in)) {

                // 阻塞直到控制台有满足条件的输入
                while (scanner.hasNext()) {
                    String message = scanner.next();
                    connections.forEach(connection -> connection.writeMessageToClient(message));
                }
            } catch (Exception e) {
                log.error("往客户端写消息时发生异常", e);
            } finally {
                producer.shutdown();
            }
        });
    }
}
