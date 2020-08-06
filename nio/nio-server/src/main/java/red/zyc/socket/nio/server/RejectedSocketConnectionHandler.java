package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务队列已满时拒绝socket连接
 *
 * @author zyc
 */
@Slf4j
public class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        ProcessTask processTask = (ProcessTask) runnable;
        log.error("服务端负载已满，请求{}已被丢弃", processTask.getConnection().getId());
    }
}
