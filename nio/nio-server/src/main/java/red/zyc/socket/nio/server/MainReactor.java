package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * MainReactor只处理SocketChannel的Accept事件
 *
 * @author zyc
 */
@Slf4j
public class MainReactor {

    /**
     * 监听的端口
     */
    private static final int LISTEN = 9001;

    /**
     * SubReactor数量
     */
    private static final int SUB_REACTOR_NUM = Runtime.getRuntime().availableProcessors() - 1;

    /**
     * SubReactors
     */
    private static final SubReactor[] SUB_REACTORS = IntStream.range(0, SUB_REACTOR_NUM).mapToObj(i -> new SubReactor()).toArray(SubReactor[]::new);

    /**
     * SubReactor线程池
     */
    private static final ExecutorService SUB_REACTORS_EVENT_LOOP = new ThreadPoolExecutor(SUB_REACTOR_NUM, SUB_REACTOR_NUM, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), new NamedThreadFactory("SubReactor"), new ThreadPoolExecutor.DiscardPolicy());

    /**
     * 监听accept事件的选择器
     */
    private static Selector selector;

    /**
     * 处理accept到的SocketChannel的下一个SubReactor在数组中的索引
     */
    private static int nextReactor = 0;

    /**
     * io多路复用是基于事件驱动实现的一种io模型。
     * <ul>
     *     <li>
     *          首先要知道的是操作系统为我们提供了一个功能，当某个socket可读或者可写的时候，它会给我们一个通知，这样当配合非阻塞的socket使用时，只有当系统通知我哪个描述符可读了，
     *          我才去执行read操作，可以保证每次read都能读到有效数据而不做纯返回-1和EAGAIN的无用功，写操作类似。
     *          操作系统是通过select/poll/epoll/kqueue之类的系统调用函数来实现的，这些函数都可以同时监视多个描述符的读写就绪状况，
     *          这样多个描述符的I/O操作都能在一个线程内交替地顺序完成，这就叫I/O多路复用，这里的“复用”指的是复用同一个线程来处理多个已准备就绪的io事件。
     *     </li>
     *     <li>
     *          为什么io复用需要配合非阻塞io进行读写？<br>
     *          因为在一次可读事件发生的时候假如此时使用的是阻塞io来读取socket通道中的数据，我们并不知道通道中
     *          有多少数据可读，本质在于阻塞io读取不到数据时就会阻塞当前线程。而换成非阻塞io读的话，我们只要循环读取通道中的数据直到返回0代表无数据可读
     *         或者和客户端商量好一个标记代表一次读操作的结束，这样就不会阻塞当前线程了。
     *     </li>
     * </ul>
     *
     * @param args 参数
     * @throws IOException io异常
     */
    public static void main(String[] args) throws IOException {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            MainReactor.selector = selector;

            // 监听本地端口
            serverSocketChannel.bind(new InetSocketAddress(LISTEN));

            // 与Selector一起使用时，Channel必须处于非阻塞模式下
            serverSocketChannel.configureBlocking(false);

            // 向选择器注册感兴趣的事件，可以用“按位或”操作符将常量连接起来SelectionKey.OP_READ | SelectionKey.OP_WRITE。
            // 返回值代表此通道在该选择器中注册的键，MainReactor只关心accept事件
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            Thread.currentThread().setName("MainReactor");

            // 启动所有SubReactor
            Arrays.stream(SUB_REACTORS).forEach(SUB_REACTORS_EVENT_LOOP::execute);

            // 监听客户端连接
            accept();
        }
    }

    /**
     * MainReactor只负责获取SocketChannel，然后传输给SubReactor让其处理io事件。
     */
    public static void accept() {
        while (!Thread.interrupted()) {
            try {

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
                selectionKeys.forEach(MainReactor::transferSocketChannel);

                // 清除所有selectionKey，否则下一次select返回的selectedKeys就会包含这一次的selectedKeys，
                selectionKeys.clear();
            } catch (Exception e) {
                throw new ServerException(e);
            }
        }
    }

    /**
     * 将SocketChannel传输给SubReactor
     *
     * @param selectionKey ServerSocketChannel关联的选择键
     */
    private static void transferSocketChannel(SelectionKey selectionKey) {
        try {
            if (selectionKey.isValid() && selectionKey.isAcceptable()) {
                ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                SocketChannel socketChannel = channel.accept();
                InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                log.info("客户端[{}:{}]已连接", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());

                // 按照自然顺序将SocketChannel平均传输到每个SubReactor中
                nextSubReactor().receiveConnection(socketChannel);
            }
            // 一个SocketChannel传输失败不应该结束MainReactor的accept方法
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * @return 下一个SubReactor
     */
    private static SubReactor nextSubReactor() {
        if (nextReactor == SUB_REACTOR_NUM) {
            nextReactor = 0;
        }
        return SUB_REACTORS[nextReactor++];
    }

}
