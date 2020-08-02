package red.zyc.socket.nio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * @author zyc
 */
public class Server {

    public static void main(String[] args) throws IOException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", 9000));

        // 与Selector一起使用时，Channel必须处于非阻塞模式下
        serverSocketChannel.configureBlocking(false);


        Selector selector = Selector.open();

        // 向选择器注册感兴趣的事件，可以用“位或”操作符将常量连接起来SelectionKey.OP_READ | SelectionKey.OP_WRITE。
        // 返回值代表此通道在该选择器中注册的键
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {

            // 阻塞到至少有一个通道在你注册的事件上就绪了。
            // 方法返回的int值表示自上次select以来新的已经就绪的通道数。
            // 如果没有把上一次已处理的SelectionKey移除掉，那么下一次循环select方法就会立马返回。
            int select = selector.select();
            if (select == 0) {
                continue;
            }

            // 当前选择器中所有符合事件的选择键
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();

                // 一个新的socket connection能够获取事件
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel accept = channel.accept();
                    accept.configureBlocking(false);
                    // 将这个客户端socket注册到selector中，监听事件为读操作
                    accept.register(selector, SelectionKey.OP_READ);

                    // 该SelectionKey的通道能够读取事件
                } else if (selectionKey.isReadable()) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    SocketChannel channel = (SocketChannel) selectionKey.channel();
                    channel.read(byteBuffer);
                    System.out.println(StandardCharsets.UTF_8.decode(byteBuffer).toString());
                }
                iterator.remove();
            }
        }
    }
}
