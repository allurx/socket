package red.zyc.socket.bio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 客户端与服务端的socket连接
 *
 * @author zyc
 */
@Slf4j
@Getter
public class Connection {

    private final String id;

    private final Socket socket;

    private final InetSocketAddress inetSocketAddress;

    private final LocalDateTime createdTime;

    public Connection(Socket socket) {
        this.id = UUID.randomUUID().toString();
        this.socket = socket;
        this.inetSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.createdTime = LocalDateTime.now();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * @return 客户端地址信息
     */
    public String clientAddress() {
        return String.format("[%s:%s]", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort());
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
        return socket.isClosed() ||
                socket.isOutputShutdown() ||
                socket.isInputShutdown();
    }

}
