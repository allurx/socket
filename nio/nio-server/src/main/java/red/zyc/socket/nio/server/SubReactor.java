package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final ThreadPoolExecutor PROCESS_EXECUTOR = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), new NamedThreadFactory("Process"), new RejectedSocketConnectionHandler());

    /**
     * 读字节缓冲
     */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);

    /**
     * 用来测试通道数据是否溢出的字节缓冲
     */
    private final ByteBuffer readOverflowBuffer = ByteBuffer.allocate(1);


    /**
     * 与此reactor关联的选择器
     */
    private final Selector selector;

    /**
     * 当前的SubReactor处理的所有连接。
     */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public SubReactor() throws IOException {
        this.selector = Selector.open();
        PROCESS_EXECUTOR.prestartAllCoreThreads();
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
        String connectionId = UUID.randomUUID().toString();
        connections.put(connectionId, new Connection(connectionId, socketChannel));

        // 唤醒自己以便注册这个SocketChannel并监听其io事件
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
        if (readSocketChannel(connection)) {

            // 请求数据读完之后提交到业务线程池中执行
            PROCESS_EXECUTOR.execute(new ProcessTask(connection));
        }
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

        // 注意：写完要注销写事件，否则会一直触发写事件导致cpu超载
        // 可以在数据写入之后调用connection.disconnect()方法关闭tcp，
        // 这样就相当于每次tcp连接都创建一个SocketChannel，开销很大。
        connection.getSelectionKey().interestOps(SelectionKey.OP_READ);

    }

    /**
     * 从SocketChannel中读取信息到字节缓冲中。<br>
     * 注意如果在执行这个方法之前客户端已经连续发送了多次数据到SocketChannel中，那么就会一次性将这几次发送的数据都读出来。
     * 如果要区分一次客户端请求，那么我们必须和客户端商量好一个标记位，读到这个标记位则代表一次请求数据读取完毕了，例如http协议
     * 可能会在请求头中定义一个content-length代表一次请求体的长度。<br>
     * 注意客户端主动关闭SocketChannel是会触发写事件的。
     *
     * @return 是否读成功
     * @throws IOException io异常
     */
    private boolean readSocketChannel(Connection connection) throws IOException {
        try {
            SocketChannel socketChannel = connection.getSocketChannel();
            int read = socketChannel.read(readBuffer);

            // 客户端通道已关闭
            if (read == -1) {
                log.info(String.format("客户端%s已关闭", connection.clientAddress()));
                connection.disconnect();
                return false;
            }
            // 未读满缓冲区
            if (read < readBuffer.limit()) {
                connection.setRequest(ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position())));
                return true;
            }
            // 通道中还有数据未读
            if (socketChannel.read(readOverflowBuffer) > 0) {
                throw new ServerException(String.format("客户端%s请求数据太大", connection.clientAddress()));
            }
            // 刚好读满缓冲区
            connection.setRequest(ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position())));
            return true;
        } finally {
            readBuffer.clear();
            readOverflowBuffer.clear();
        }
    }

    /**
     * SubReactor被唤醒之后就会将容器中本次拿到的所有SocketChannel都注册到自己的selector中以监听读写事件。
     *
     * @throws IOException io异常
     */
    private void register() throws IOException {
        for (Connection connection : connections.values()) {
            connections.remove(connection.getId());
            SocketChannel socketChannel = connection.getSocketChannel();
            socketChannel.configureBlocking(false);

            // 注意register方法是与select方法同步互斥的，他们内部都synchronized了publicKeys，
            // 所以通常情况下register必须在select之前执行，由于我们使用的主从reactor模式，两个reactor
            // 是运行在不同的线程上的，我们需要将MainReactor接受到的连接传递给SubReactor，
            // 而SubReactor已经提前在线程池中运行很有可能已经阻塞在select方法上了，
            // 所以我们可以通过在MainReactor线程上将socket连接添加到SubReactor内部的容器中接着再唤醒SubReactor，
            // SubReactor再调用这个注册方法将容器中的连接都注册到自己的selector中。
            SelectionKey register = socketChannel.register(selector, SelectionKey.OP_READ);
            connection.setSelectionKey(register);
            register.attach(connection);
        }
    }
}
