package red.zyc.socket.aio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * SubReactor只处理SocketChannel的读写
 *
 * @author zyc
 */
@Slf4j
@Getter
public class SubReactor implements CompletionHandler<Integer, Connection>, Runnable {

    /**
     * SubReactor数量
     */
    private static final int SUB_REACTOR_NUM = Runtime.getRuntime().availableProcessors() - 1;

    /**
     * SubReactor线程池
     */
    private static final ExecutorService SUB_REACTORS_EVENT_LOOP = new ThreadPoolExecutor(SUB_REACTOR_NUM, SUB_REACTOR_NUM, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), new NamedThreadFactory("SubReactor"), new ThreadPoolExecutor.DiscardPolicy());

    /**
     * 所有SubReactor待处理的所有连接。
     */
    private static final BlockingQueue<Connection> PENDING_CONNECTIONS = new ArrayBlockingQueue<>(1000);

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                // 阻塞直到获取到一个Connection为止
                Connection connection = PENDING_CONNECTIONS.take();

            } catch (Exception e) {

            }


        }
    }

    @Override
    public void completed(Integer result, Connection connection) {

    }

    @Override
    public void failed(Throwable exc, Connection connection) {
        log.error("");
    }

    /**
     * 将MainReactor获取的SocketChannel添加到内部待处理的队列中
     *
     * @param socketChannel MainReactor监听到的SocketChannel
     * @return 是否添加成功，返回false代表当前所有SubReactor都在忙于处理读写事件
     */
    public static boolean receiveConnection(AsynchronousSocketChannel socketChannel) {
        return PENDING_CONNECTIONS.offer(new Connection(socketChannel));
    }

    /**
     * 初始化执行所有SubReactor
     */
    static void init() {
        IntStream.range(0, SUB_REACTOR_NUM).forEach(i -> SUB_REACTORS_EVENT_LOOP.execute(new SubReactor()));
    }

    static {
        init();
    }
}
