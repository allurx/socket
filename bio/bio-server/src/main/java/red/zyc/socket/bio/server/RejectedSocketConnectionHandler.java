package red.zyc.socket.bio.server;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * socket连接数已满时的拒绝任务策略。
 * 连接数已满时关闭socket。然后客户端read方法就会返回-1。
 *
 * @author zyc
 */
@Slf4j
public class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

    @SneakyThrows
    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        log.error("服务端连接数已满");
        ProcessTask task = (ProcessTask) runnable;
        task.getConnection().disconnect();
    }
}
