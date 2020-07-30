package red.zyc.socket.server;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
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
     * 处理每个客户端连接的线程池
     */
    private final ExecutorService consumer = new ThreadPoolExecutor(3, 3, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory(), new RejectedSocketConnectionHandler());

    /**
     * 往每个客户端发送消息的线程池
     */
    private final ExecutorService producer = Executors.newFixedThreadPool(1);

    /**
     * 服务端socket
     */
    private ServerSocket serverSocket;

    /**
     * 所有客户端连接
     */
    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        new Server().start();
    }

    /**
     * 启动服务器
     *
     * @throws IOException io异常
     */
    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(8200)) {
            this.serverSocket = server;
            sendMessageToClient();
            acceptClient();
        }
    }

    /**
     * 监听客户端
     *
     * @throws IOException io异常
     */
    private void acceptClient() throws IOException {
        for (; ; ) {

            // main线程阻塞直到有一个连接建立为止
            Socket socket = serverSocket.accept();

            // 通过线程池处理每个客户端连接
            consumer.execute(new SocketConnectionTask(socket));

        }
    }

    /**
     * 将控制台输入消息发送到客户端
     */
    private void sendMessageToClient() {
        producer.execute(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                // 阻塞直到控制台有满足条件的输入
                while (scanner.hasNext()) {
                    String message = scanner.next();
                    connections.forEach(connection -> connection.writeMessageToClient(message));
                }
            } catch (Exception e) {
                // 仅仅打印异常而不是退出这个任务
                log.error(e.getMessage(), e);
            }
        });
    }

    /**
     * socket连接任务
     *
     * @author zyc
     */
    @Getter
    private class SocketConnectionTask implements Runnable {

        private final Socket socket;

        public SocketConnectionTask(Socket socket) {
            this.socket = socket;
        }

        @SneakyThrows
        @Override
        public void run() {
            try {
                log.info("客户端[{}:{}]已连接", socket.getInetAddress().getHostAddress(), socket.getPort());
                Connection connection = new Connection(Server.this, socket);
                connections.add(connection);
                connection.readClientMessage();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * socket连接数已满时的拒绝任务策略。
     * 连接数已满时关闭socket。然后客户端read方法就会返回-1。
     */
    private class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

        @SneakyThrows
        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.error("服务端连接数已满");
            SocketConnectionTask task = (SocketConnectionTask) runnable;
            task.getSocket().close();
        }
    }
}
