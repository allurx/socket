package red.zyc.socket.bio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * socket连接任务
 *
 * @author zyc
 */
@Slf4j
@Getter
public class ProcessTask implements Runnable {

    private final String id;

    private final Connection connection;

    public ProcessTask(Connection connection) {
        this.connection = connection;
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        try {
            log.info("客户端[{}:{}]已连接", connection.getInetSocketAddress().getAddress().getHostAddress(), connection.getInetSocketAddress().getPort());
            connection.readData();
            connection.writeData(String.format("我是请求%s的响应", id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            // 关闭socket使客户端readLine方法返回
            connection.disconnect();
        }
    }
}
