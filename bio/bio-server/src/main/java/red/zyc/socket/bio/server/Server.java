package red.zyc.socket.bio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zyc
 */
@Slf4j
@Getter
public class Server {

    /**
     * 服务端监听的端口
     */
    private static final int LISTEN = 9000;

    /**
     * 处理每个客户端连接的线程池
     */
    private final ExecutorService consumer = new ThreadPoolExecutor(100, 100, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());

    /**
     * 服务端socket
     */
    private ServerSocket serverSocket;

    /**
     * 基于bio使用线程池实现的服务器<br>
     * bio就是阻塞io也就是java.io包下的各种流。这些流在读数据的时候如果读不到数据就会阻塞当前线程，
     * 因此在一个socket连接建立即请求来临时为了不阻塞main线程从中读取数据我们必须对每一个socket
     * 连接都开启一个线程，这种io模型的好处是在连接数较小时有比较好的性能，一个线程对应一个socket读写任务，代码编写也比较简单。
     * 不好之处在于如果有大量连接同时建立时我们不可能无限制的新建线程，我们就必须要使用固定大小的线程池来管理线程，因此对于海量的tcp连接这种io模型是无能为力的。
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
        try (ServerSocket server = new ServerSocket(LISTEN)) {
            this.serverSocket = server;
            acceptClient();
        }
    }

    /**
     * 监听客户端
     *
     * @throws IOException io异常
     */
    private void acceptClient() throws IOException {
        while (!Thread.interrupted()) {

            // main线程阻塞直到有一个连接建立为止
            Socket socket = serverSocket.accept();

            // 通过线程池处理每个客户端连接
            consumer.execute(new ProcessTask(new Connection(socket)));

        }
    }

}
