package red.zyc.socket.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * 客户端与服务端的socket连接
 *
 * @author zyc
 */
@Slf4j
@Getter
public class Connection {

    private final Socket socket;

    private final Server server;

    /**
     * socket关闭时read方法返回-1，socket没有关闭但是没有数据时read方法会阻塞当前线程直到
     * 流里面有数据为止。
     */
    private final BufferedReader reader;

    private final BufferedWriter writer;

    private final LocalDateTime createdTime;

    public Connection(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.createdTime = LocalDateTime.now();
    }

    /**
     * 读客户端发送过来的消息
     */
    public void readClientMessage() {
        try {
            String line;
            // readLine会阻塞直到读到一个换行符为止，返回null代表socket关闭了
            while ((line = reader.readLine()) != null) {
                log.info("来自客户端[{}:{}]的消息: {}", socket.getInetAddress().getHostAddress(), socket.getPort(), line);
            }
        } catch (Exception e) {
            throw new ServerException(e);
        } finally {
            // 进入finally代表读socket时发生了异常或者socket已经关闭了
            server.getConnections().remove(this);
        }
    }

    /**
     * 将消息发送给客户端
     *
     * @param message 消息
     */
    public void writeMessageToClient(String message) {
        try {

            writer.write(message);

            // 写入一个换行符以便客户端能够识别一行数据，避免另一端read方法一直阻塞
            writer.newLine();

            // 将writer缓冲区的数据立即刷新发送出去，否则必须等到缓冲满了才会发送
            writer.flush();

        } catch (Exception e) {
            server.getConnections().remove(this);
            throw new ServerException(e);
        }
    }

    /**
     * 测试连接是否异常
     *
     * @return 当前连接是否异常
     */
    public boolean abnormal() {
        try {
            // 默认情况下SocketOptions#SO_OOBINLINE是关闭的，即发送的字节不会被追加到流中去。
            // 如果发送失败代表客户端与服务端的连接有问题，可能是一方的连接被重置了。
            socket.sendUrgentData(0xff);
        } catch (Exception e) {
            return true;
        }
        return server.getServerSocket().isClosed() ||
                socket.isClosed() ||
                socket.isOutputShutdown() ||
                socket.isInputShutdown();
    }

}
