package red.zyc.socket.bio.client;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * @author zyc
 */
@Slf4j
public class Client {

    /**
     * 服务端端口
     */
    private static final int SERVER_PORT = 9000;

    /**
     * 服务端主机地址
     */
    private static final String SERVER_HOST = "localhost";

    /**
     * 客户端socket
     */
    private Socket socket;

    public static void main(String[] args) throws IOException {
        new Client().start();
    }

    /**
     * 启动客户端
     *
     * @throws IOException io异常
     */
    public void start() throws IOException {
        try (Socket client = new Socket(SERVER_HOST, SERVER_PORT)) {
            this.socket = client;
            writeMessageToServer();
            readServerMessage();
        } finally {
            System.exit(0);
        }
    }

    /**
     * 将控制台输入消息发送到服务端
     *
     * @throws IOException io异常
     */
    private void writeMessageToServer() throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        writer.write("我是客户端");

        // 写入一个换行符以便客户端能够识别一行数据，避免另一端read方法一直阻塞
        writer.newLine();

        // 将writer缓冲区的数据立即刷新发送出去，否则必须等到缓冲满了才会发送
        writer.flush();
    }

    /**
     * 读取服务端发送过来的消息
     */
    private void readServerMessage() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            // readLine会阻塞直到读到一个换行符为止，返回null代表socket关闭了
            if ((line = reader.readLine()) != null) {
                log.info("来自服务端[{}:{}]的消息: {}", socket.getInetAddress().getHostAddress(), socket.getPort(), line);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}