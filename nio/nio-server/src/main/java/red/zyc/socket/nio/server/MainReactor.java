package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * MainReactor只关心socket连接能够获取事件
 *
 * @author zyc
 */
@Slf4j
public class MainReactor {

    /**
     * SubReactor数量
     */
    private static final int SUB_REACTOR_NUM = Runtime.getRuntime().availableProcessors() - 1;

    /**
     * SubReactors
     */
    private static final SubReactor[] SUB_REACTORS = new SubReactor[SUB_REACTOR_NUM];

    /**
     * SubReactor线程池，该线程池只会在{@link #initSubReactors 初始化时}执行{@link #SUB_REACTORS}中的所有任务，多余的任务将会被抛弃。
     */
    private static final ExecutorService SUB_REACTORS_EVENT_LOOP = new ThreadPoolExecutor(SUB_REACTOR_NUM, SUB_REACTOR_NUM, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory("SubReactor"), new ThreadPoolExecutor.DiscardPolicy());

    /**
     * 监听accept事件的选择器
     */
    private final Selector selector;

    /**
     * 处理accept到的SocketChannel的下一个SubReactor在数组中的索引
     */
    private int nextReactor = 0;

    public MainReactor(Selector selector) throws IOException {
        this.selector = selector;
        initSubReactors();
    }

    /**
     * MainReactor只负责获取SocketChannel，然后传输给SubReactor让其处理io事件。
     */
    public void accept() {
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
                for (SelectionKey selectionKey : selectionKeys) {
                    transferSocketChannel(selectionKey);
                }

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
    private void transferSocketChannel(SelectionKey selectionKey) {
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
    private SubReactor nextSubReactor() {
        if (nextReactor == SUB_REACTOR_NUM) {
            nextReactor = 0;
        }
        return SUB_REACTORS[nextReactor++];
    }

    /**
     * 初始化并执行SubReactor
     *
     * @throws IOException io异常
     */
    private void initSubReactors() throws IOException {
        for (int i = 0; i < SUB_REACTORS.length; i++) {
            SUB_REACTORS[i] = new SubReactor();
            SUB_REACTORS_EVENT_LOOP.execute(SUB_REACTORS[i]);
        }
    }

}
