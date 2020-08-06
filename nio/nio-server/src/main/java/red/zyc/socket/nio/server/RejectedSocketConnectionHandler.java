package red.zyc.socket.nio.server;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务队列已满时拒绝socket连接
 *
 * @author zyc
 */
@Slf4j
public class RejectedSocketConnectionHandler implements RejectedExecutionHandler {

    /**
     * 一次tcp响应的结束标记
     */
    private static final ByteBuffer EOL = ByteBuffer.wrap(new byte[]{'\n'});

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        ProcessTask processTask = (ProcessTask) runnable;
        log.error("服务端负载已满，连接{}已被丢弃", processTask.getConnection().getId());

        Connection connection = processTask.getConnection();
        connection.setResponse(EOL);

        // 注册本次请求的写事件
        connection.getSelectionKey().interestOps(SelectionKey.OP_WRITE);

        // 唤醒SubReactor线程以触发写事件
        connection.getSelectionKey().selector().wakeup();
    }
}
