package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zyc
 */
@Slf4j
@Getter
public class SubReactor implements Runnable {

    /**
     * 处理业务逻辑的线程池
     */
    private static final ThreadPoolExecutor PROCESS_EXECUTOR = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());

    private final ServerSocketChannel serverSocketChannel;

    private final Selector selector;

    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public SubReactor(ServerSocketChannel serverSocketChannel) throws IOException {
        this.serverSocketChannel = serverSocketChannel;
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
                register();
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

                            // 当前SelectionKey的通道能够读取事件，这个方法可能会抛出CancelledKeyException
                            if (selectionKey.isReadable()) {
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

            } catch (Exception e) {
                throw new ServerException(e);
            }

        }
    }

    public void register() throws IOException {
        for (Connection connection : connections.values()) {
            // 按照顺序将这个socket通道注册到SubReactor的selector中，让这个SubReactor处理该连接的io事件
            // 注意register方法是与select方法同步互斥的，他们内部都synchronized了publicKeys，所以register必须在select之前执行
            connections.remove(connection.getId());
            SelectionKey register = connection.getSocketChannel().register(selector, 0);
            connection.setSelectionKey(register);
            register.attach(connection);
            register.interestOps(SelectionKey.OP_READ);
        }
    }
}
