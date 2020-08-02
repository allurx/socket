package red.zyc.socket.nio.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

/**
 * @author zyc
 */
public class Client {

    public static void main(String[] args) throws IOException {

        try (SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("localhost", 9000));
             Scanner scanner = new Scanner(System.in)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            // 阻塞直到控制台有满足条件的输入
            while (scanner.hasNext()) {
                String message = scanner.next();
                socketChannel.write(ByteBuffer.wrap(message.getBytes()));
            }
        }
    }
}
