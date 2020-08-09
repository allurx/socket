package red.zyc.socket.bio.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * socket连接任务
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getSocket().getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getSocket().getOutputStream()))) {
            log.info("客户端{}已连接", connection.clientAddress());

            while (!Thread.interrupted()) {
                String request = readData(reader);
                if (request != null) {
                    log.info("来自客户端{}的消息: {}", connection.clientAddress(), request);

                    // 写入业务返回的数据，约定换行符代表一次tcp响应的结尾
                    writeData(writer, String.format("我是请求%s的响应%n", connection.getId()));
                } else {
                    log.info(String.format("客户端%s已关闭", connection.clientAddress()));
                    break;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 读客户端发送过来的消息
     */
    public String readData(BufferedReader reader) throws IOException {
        String request;
        // readLine会阻塞直到读到一个换行符为止，返回null代表客户端socket关闭了
        if ((request = reader.readLine()) != null) {
            return request;
        } else {
            return null;
        }
    }

    /**
     * 将消息发送给客户端
     *
     * @param message 消息
     */
    public void writeData(BufferedWriter writer, String message) throws IOException {
        writer.write(message);

        // 写入一个换行符以便客户端能够识别一行数据，避免另一端read方法一直阻塞
        writer.newLine();

        // 将writer缓冲区的数据立即刷新发送出去，否则必须等到缓冲满了才会发送
        writer.flush();
    }
}
