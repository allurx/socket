package red.zyc.socket.aio.server;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务线程池任务队列已满时拒绝本次请求
 *
 * @author zyc
 */
@Slf4j
public class RejectedRequestHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        ProcessTask processTask = (ProcessTask) runnable;
        log.error("服务端负载已满，本次连接{}的请求数据已被丢弃", processTask.getConnection().getId());

        // 这一次任务被丢弃了但还是要发送一个换行符标记告诉客户端本次请求结束了
        Connection connection = processTask.getConnection();

        // 这个ByteBuffer不要设置为成员变量或者静态变量，因为在SocketChannel.write之后，这个ByteBuffer的position会被置为limit，
        // 除非在写完之后调用flip方法才能再次写这个ByteBuffer。
        ByteBuffer rejectedResponse = ByteBuffer.wrap(String.format("服务端负载已满，连接%s的请求数据已被丢弃%n", connection.getId()).getBytes());
        connection.setResponse(rejectedResponse);
        connection.write();
    }
}
