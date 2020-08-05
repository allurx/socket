package red.zyc.socket.nio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 处理业务逻辑，如果是io密集型的业务操作通常是放在自己的业务线程池里执行的。
 * 这里我们仅仅输出了客户端的消息。
 *
 * @author zyc
 */
@Slf4j
@Getter
public class ProcessTask implements Runnable {

    private final String id;

    private final ByteBuffer data;

    private final Connection connection;

    public ProcessTask(Connection connection, ByteBuffer data) {
        this.connection = connection;
        this.data = data;
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        try {
            log.info("来自客户端[{}:{}]的消息: {}", connection.getInetSocketAddress().getAddress().getHostAddress(), connection.getInetSocketAddress().getPort(), StandardCharsets.UTF_8.decode(data));
            connection.writeData(String.format("我是请求%s的响应", id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            connection.disconnect();
        }
    }
}
