package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SubReactor只处理SocketChannel的读写事件
 *
 * @author zyc
 */
@Slf4j
@Getter
public class SubReactor implements Runnable {

    /**
     * 读缓冲大小1024 (1 KiB)
     */
    private static final int BUFFER_CAPACITY = 1 << 10;

    /**
     * 处理业务逻辑的线程池
     */
    private static final ThreadPoolExecutor PROCESS_EXECUTOR = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new NamedThreadFactory("Process"), new RejectedRequestHandler());

    /**
     * 读取请求数据的字节缓冲对象，对于同一个SubReactor来说每个Connection的按顺序读的，所以该对象是可以复用的。
     */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 与此reactor关联的选择器
     */
    private final Selector selector;

    /**
     * 当前的SubReactor处理的所有连接。按照FIFO方式处理连接。新增连接是往队列尾部插入元素，注册连接是从头部移除元素，这两者时间复杂度都是O(1)。
     */
    private final Deque<Connection> connections = new LinkedList<>();


    public SubReactor() throws IOException {
        this.selector = Selector.open();
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {

                // 阻塞直到有一个已注册的通道上有满足条件的事件就绪，或者selector的wakeup方法被调用或者当前线程被中断。
                // 方法返回的int值表示有io事件准备就绪的所有已注册的SelectionKey。注意如果没有把上一次select返回的selectedKeys移除掉，
                // 那么下一次循环select方法返回的selectedKeys就会包含上一次的selectedKeys，这是一个坑一定要在迭代结束后移除已处理的SelectionKey
                int select = selector.select();

                // 注册MainReactor传递的SocketChannel
                register();

                if (select == 0) {
                    continue;
                }

                // 当前选择器中所有符合事件的选择键
                Set<SelectionKey> selectionKeys = selector.selectedKeys();

                // 遍历所有准备就绪的SelectionKey
                for (SelectionKey selectionKey : selectionKeys) {
                    dispatchEvent(selectionKey);
                }
                // 清除所有selectionKey，否则下一次select返回的selectedKeys就会包含这一次的selectedKeys，
                selectionKeys.clear();

            } catch (Exception e) {
                throw new ServerException(e);
            }
        }
    }

    /**
     * 将MainReactor获取的SocketChannel添加到自己内部待处理的容器中，然后唤醒自己的selector以便将
     * 此SocketChannel注册到selector中监听io事件。
     *
     * @param socketChannel MainReactor监听到的SocketChannel
     * @throws IOException io异常
     */
    public void receiveConnection(SocketChannel socketChannel) throws IOException {
        connections.addLast(new Connection(socketChannel));
        // 唤醒阻塞在select方法上SubReactor线程或者使下一次select方法直接返回，然后注册队列中的所有SocketChannel并监听其io事件
        selector.wakeup();
    }

    /**
     * 派发selectionKey关联的SocketChannel的读写事件
     *
     * @param selectionKey SocketChannel关联的选择键
     */
    private void dispatchEvent(SelectionKey selectionKey) {
        try {
            // 只处理有效的selectionKey
            if (selectionKey.isValid()) {

                // 当前SelectionKey的通道能够读取事件，这个方法可能会抛出CancelledKeyException
                if (selectionKey.isReadable()) {
                    handleReadEvent(selectionKey);

                    // 当前SelectionKey的通道能够写事件，这个方法可能会抛出CancelledKeyException
                } else if (selectionKey.isWritable()) {
                    handleWriteEvent(selectionKey);
                }
            }
            // 一次读写ByteBuffer产生的异常不应该停止事件轮询
        } catch (Exception e) {
            // 读写SocketChannel数据发生异常时，将其从selector的注册中取消，不在关注其任何io事件。
            selectionKey.cancel();
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 读一次SocketChannel数据
     *
     * @param selectionKey SocketChannel关联的选择键
     */
    private void handleReadEvent(SelectionKey selectionKey) throws IOException {
        Connection connection = (Connection) selectionKey.attachment();

        // SubReactor线程读一次请求数据，然后将读到的数据传递给业务线程池执行，此刻读通常情况下是不会阻塞的，
        // 因为此刻SocketChannel是可读的，是能够立马从tcp缓存区读取数据到用户空间中。
        Optional.ofNullable(simpleDecode(connection)).ifPresent(byteBuffer -> {
            connection.setRequest(byteBuffer);

            // 请求数据读完之后提交到业务线程池中执行
            PROCESS_EXECUTOR.execute(new ProcessTask(connection));
        });
    }

    /**
     * 写一次数据到SocketChannel中
     *
     * @param selectionKey SocketChannel关联的选择键
     */
    private void handleWriteEvent(SelectionKey selectionKey) throws IOException {
        Connection connection = (Connection) selectionKey.attachment();
        SocketChannel socketChannel = connection.getSocketChannel();
        socketChannel.write(connection.getResponse());

        // 注意：写完要切换为读事件，否则会一直触发写事件导致cpu超载
        // 可以在数据写入之后调用connection.disconnect()方法关闭SocketChannel，
        // 这样就相当于每次tcp连接都创建一个SocketChannel，开销很大。
        connection.getSelectionKey().interestOps(SelectionKey.OP_READ);

    }

    /**
     * 简单的进行一次tcp读解码，仅仅将{@link #readBuffer}中位置0到position之间的字节包装成一个新的{@link ByteBuffer}返回，
     * 然后调用{@link Buffer#clear()}方法重置position为0。<br><br>
     * 注意：该方法仅仅是为了简单调试用的，实际与客户端进行通讯时我们应当和客户端商量好一个标记位，读到这个标记位则代表一次请求数据读取完毕了，
     * 例如http协议可能会在请求头中定义一个content-length代表一次请求体的长度。
     *
     * @return 返回null代表客户端已关闭，否则返回从SocketChannel中读取到的数据。
     * @throws IOException io异常
     */
    private ByteBuffer simpleDecode(Connection connection) throws IOException {
        try {
            SocketChannel socketChannel = connection.getSocketChannel();

            // 如果客户端由于断网等原因造成的关闭，那么read方法会抛出一个IOException而不是返回-1。
            // 只有客户端主动调用socketChannel.close()方法read方法才会返回-1。
            int read = socketChannel.read(readBuffer);

            // 客户端通道已关闭
            if (read == -1) {
                log.info(String.format("客户端%s已关闭", connection.clientAddress()));
                connection.disconnect();
                return null;
            }
            return ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position()));
        } finally {
            readBuffer.clear();
        }
    }

    /**
     * SubReactor被唤醒之后就会按照FIFO的方式将此刻{@link #connections 队列}中所有与{@link Connection}关联的{@link SocketChannel}都注册到自己的selector中以监听读写事件。
     *
     * @throws IOException io异常
     */
    private void register() throws IOException {
        Connection connection = connections.pollFirst();
        if (connection != null) {
            SocketChannel socketChannel = connection.getSocketChannel();
            socketChannel.configureBlocking(false);

            // 注意register方法是与select方法同步互斥的，他们内部都synchronized了publicKeys，
            // 所以通常情况下register必须在select之前执行，由于我们使用的主从reactor模式，两个reactor
            // 是运行在不同的线程上的，我们需要将MainReactor接受到的SocketChannel传递给SubReactor，
            // 而SubReactor已经提前在线程池中运行很有可能已经阻塞在select方法上了，
            // 所以我们可以通过在MainReactor线程上将socket连接添加到SubReactor内部的容器中接着再唤醒SubReactor，
            // SubReactor再调用这个注册方法将容器中的连接都注册到自己的selector中。
            SelectionKey register = socketChannel.register(selector, SelectionKey.OP_READ, connection);
            connection.setSelectionKey(register);
            register();
        }
    }
}
