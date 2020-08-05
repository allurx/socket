package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zyc
 */
@Slf4j
public class MainReactor {

    private static final int CPUS = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService SUB_REACTORS_EVENT_LOOP = Executors.newFixedThreadPool(CPUS);

    private final ServerSocketChannel serverSocketChannel;

    private final Selector selector;

    private final SubReactor[] subReactors = new SubReactor[CPUS];

    private int currentReactor = 0;

    public MainReactor(ServerSocketChannel serverSocketChannel) throws IOException {
        this.serverSocketChannel = serverSocketChannel;
        this.selector = Selector.open();

        //初始化并执行SubReactor
        initSubReactors();

        // 向选择器注册感兴趣的事件，可以用“按位或”操作符将常量连接起来SelectionKey.OP_READ | SelectionKey.OP_WRITE。
        // 返回值代表此通道在该选择器中注册的键，主reactor只关心accept事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

    }

    public void accept() throws IOException {
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
                    if (selectionKey.isValid() && selectionKey.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                        SocketChannel socketChannel = channel.accept();
                        socketChannel.configureBlocking(false);

                        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                        log.info("客户端[{}:{}]已连接", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());

                        // 按照顺序将这个socket通道注册到selector中，让这个SubReactor监听该连接的读事件
                        SelectionKey register = socketChannel.register(nextSubReactor().getSelector(), SelectionKey.OP_READ);
                        register.attach(new Connection(UUID.randomUUID().toString(), socketChannel, register));
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            // 清除所有selectionKey，否则下一次select返回的selectedKeys就会包含这一次的selectedKeys，
            selectionKeys.clear();
        }

    }

    /**
     * @return 下一个SubReactor
     */
    private SubReactor nextSubReactor() {
        if (currentReactor == CPUS) {
            currentReactor = 0;
        }
        return subReactors[currentReactor++];
    }

    /**
     * 初始化并执行SubReactor
     *
     * @throws IOException io异常
     */
    private void initSubReactors() throws IOException {
        for (int i = 0; i < subReactors.length; i++) {
            subReactors[i] = new SubReactor(serverSocketChannel);
            SUB_REACTORS_EVENT_LOOP.execute(subReactors[i]);
        }
    }

}
