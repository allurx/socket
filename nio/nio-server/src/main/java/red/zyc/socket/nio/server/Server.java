package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

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
        new Server().start();
    }

    /**
     * 启动服务器
     *
     * @throws IOException io异常
     */
    public void start() throws IOException {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {

            // 监听本地端口
            server.bind(new InetSocketAddress(LISTEN));

            // 与Selector一起使用时，Channel必须处于非阻塞模式下
            server.configureBlocking(false);

            new MainReactor(server).accept();
        }
    }

}
