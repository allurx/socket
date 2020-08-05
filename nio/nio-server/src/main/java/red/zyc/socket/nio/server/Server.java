package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
     * 处理业务逻辑的线程池
     */
    private static final ThreadPoolExecutor PROCESS_EXECUTOR = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());

    /**
     * 选择器
     */
    private final Selector selector;

    /**
     * 服务端socket通道
     */
    private ServerSocketChannel serverSocketChannel;

    public Server(Selector selector) {
        this.selector = selector;
    }

    /**
     * io多路复用是基于事件驱动实现的一种io模型。
     * <ul>
     *     <li>
     *          首先要知道的是操作系统为我们提供了一个功能，当某个socket可读或者可写的时候，它会给我们一个通知，这样当配合非阻塞的socket使用时，只有当系统通知我哪个描述符可读了，
     *          我才去执行read操作，可以保证每次read都能读到有效数据而不做纯返回-1和EAGAIN的无用功。写操作类似。
     *          操作系统的这个功能通过select/poll/epoll/kqueue之类的系统调用函数来实现的，这些函数都可以同时监视多个描述符的读写就绪状况，
     *          这样多个描述符的I/O操作都能在一个线程内并发交替地顺序完成，这就叫I/O多路复用，这里的“复用”指的是复用同一个线程来处理多个已准备就绪的io事件。
     *     </li>
     *     <li>
     *          为什么io复用需要配合非阻塞io进行读写？<br>
     *          因为在一次可读事件发生的时候假如此时使用的是阻塞io来读取socket通道中的数据，我们并不知道通道中
     *          有多少数据可读，本质在于阻塞io读取不到数据时就会阻塞当前线程。而换成非阻塞io读的话，我们只要循环读取通道中的数据直到返回0代表无数据可读
     *         或者和客户端商量好一个标记代表一次读操作的结束。这样就不会阻塞当前线程了。
     *     </li>
     * </ul>
     *
     * @param args 参数
     * @throws IOException io异常
     */
    public static void main(String[] args) throws IOException {
        new Server(Selector.open()).start();
        PROCESS_EXECUTOR.prestartAllCoreThreads();
    }

    /**
     * 启动服务器
     *
     * @throws IOException io异常
     */
    public void start() throws IOException {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {

            this.serverSocketChannel = server;

            // 监听本地端口
            serverSocketChannel.bind(new InetSocketAddress(LISTEN));

            // 与Selector一起使用时，Channel必须处于非阻塞模式下
            serverSocketChannel.configureBlocking(false);

            // 向选择器注册感兴趣的事件，可以用“按位或”操作符将常量连接起来SelectionKey.OP_READ | SelectionKey.OP_WRITE。
            // 返回值代表此通道在该选择器中注册的键
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            // 轮询io事件
            eventLoop();
        }
    }

    /**
     * io事件监听以及派发
     *
     * @throws IOException io异常
     */
    private void eventLoop() throws IOException {

        while (!Thread.interrupted()) {

            // 阻塞直到有一个已注册的通道上有满足条件的事件就绪，或者selector的wakeup方法被调用或者当前线程被中断。
            // 方法返回的int值表示有io事件准备就绪的所有已注册的SelectionKey。注意如果没有把上一次select返回的selectedKeys移除掉，
            // 那么下一次循环select方法返回的selectedKeys就会包含上一次的selectedKeys，这是一个坑一定要在迭代结束后移除已处理的SelectionKey
            int select = selector.select();
            if (select == 0) {
                continue;
            }

            // 当前选择器中所有符合事件的选择键
            Set<SelectionKey> selectionKeys = selector.selectedKeys();

            // 遍历所有准备就绪的SelectionKey
            for (SelectionKey selectionKey : selectionKeys) {
                try {
                    // 只处理有效的selectionKey
                    if (selectionKey.isValid()) {

                        // 当前SelectionKey的通道能够获取一个新的socket connection事件
                        if (selectionKey.isAcceptable()) {
                            ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                            SocketChannel socketChannel = channel.accept();
                            socketChannel.configureBlocking(false);

                            // 将这个socket通道注册到selector中，监听读事件
                            SelectionKey register = socketChannel.register(selector, SelectionKey.OP_READ);

                            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                            log.info("客户端[{}:{}]已连接", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
                            register.attach(new Connection(UUID.randomUUID().toString(), this, socketChannel, register));

                            // 当前SelectionKey的通道能够读取事件，这个方法可能会抛出CancelledKeyException
                        } else if (selectionKey.isReadable()) {
                            Connection connection = (Connection) selectionKey.attachment();

                            // nio线程读一次请求数据，然后将读到的数据传递给业务线程池执行，此刻读通常情况下是不会阻塞的，因为此刻socket
                            // 通道是可读的，是能够立马从tcp缓存区读取数据到用户空间中。
                            connection.readData();

                            // 请求数据读完之后提交到业务线程池中执行
                            PROCESS_EXECUTOR.execute(new ProcessTask(connection));

                            // 当前SelectionKey的通道能够写事件，这个方法可能会抛出CancelledKeyException
                        } else if (selectionKey.isWritable()) {
                            Connection connection = (Connection) selectionKey.attachment();
                            connection.writeData();
                            connection.disconnect();
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            // 清除所有selectionKey，否则下一次select返回的selectedKeys就会包含这一次的selectedKeys，
            selectionKeys.clear();
        }
    }

}
