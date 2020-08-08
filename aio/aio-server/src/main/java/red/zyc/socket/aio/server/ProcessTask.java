package red.zyc.socket.aio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 处理业务逻辑，如果是io密集型的业务操作通常是放在自己的业务线程池里执行的。
 * 这里我们仅仅输出了客户端的消息。
 *
 * @author zyc
 */
@Slf4j
@Getter
public class ProcessTask implements Runnable {

    private final Connection connection;

    public ProcessTask(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        try {

            // 模拟业务操作，这里仅仅打印了请求的数据
            log.info("来自客户端{}的消息: {}", connection.clientAddress(), StandardCharsets.UTF_8.decode(connection.getRequest()).toString());

            // 写入业务返回的数据，约定换行符代表一次tcp响应的结尾
            connection.setResponse(ByteBuffer.wrap(String.format("我是连接%s的响应%n", connection.getId()).getBytes()));

            // 写入响应数据
            connection.write();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}
