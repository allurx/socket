# 基于bio的tcp网络通信
1. 启动服务端  
运行[Server](https://github.com/Allurx/socket/blob/master/bio/bio-server/src/main/java/red/zyc/socket/bio/server/Server.java)的main方法启动服务端。服务端默认限制最多100个客户端连接，
当然你也可以更改服务端线程池的配置以启动更多的客户端。
2. 启动客户端    
运行[Client](https://github.com/Allurx/socket/blob/master/bio/bio-client/src/main/java/red/zyc/socket/bio/client/Client.java)的main方法启动客户端。
3. 观察服务端和客户端的控制台输出即可看到一次tcp请求的完整流程。
# 架构
bio（blocking io）即java.io包中的InputStream、OutputStream、Reader、Writer这些类的子类。其中InputStream和Reader的read方法是一种**不确定阻塞读**，
只要当前Stream中没有数据且Stream未关闭的话，就会阻塞当前线程。假设一次请求的数据是10KiB，而服务端是无法知道这次请求究竟发送了多少字节的，所以服务端只能
通过read()方法一个字节一个字节的读，等读到第11次时，如果客户端还没有发送数据过来，那么服务端这个读线程就会阻塞住，所以通常情况下我们需要和客户端商量一个协议来确定
一次请求的开始和结束，例如可以约定读到一个换行符就代表一次请求结束了。正因为这种读是一种不确定的阻塞读，如果当前线程读的时候被阻塞了，那么接下来的请求就都会被阻塞，
所以我们就必须对于每一次socket连接都开启一个线程，避免其中一个socket读阻塞影响其它请求。然后通过线程池来管理这些线程。