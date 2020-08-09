# 基于aio的tcp网络通信
1. 启动服务端  
运行[Server](https://github.com/Allurx/socket/blob/master/nio/nio-server/src/main/java/red/zyc/socket/nio/server/MainReactor.java)的main方法启动服务端。
2. 启动客户端    
运行[Client](https://github.com/Allurx/socket/blob/master/nio/nio-client/src/main/java/red/zyc/socket/nio/client/Client.java)的main方法启动客户端。
3. 观察服务端和客户端的控制台输出即可看到一次tcp请求的完整流程。
# 架构
基于多reactor模式设计的非阻塞服务端，MainReactor只负责接受tcp连接然后产生一个Connection传输给SubReactor。
SubReactor负责监听Connection的读写事件，将读取到的数据放入Connection然后提交给业务线程池执行，
业务线程执行完毕后将响应数据放入Connection并唤醒所属的SubReactor以触发写事件，最后SubReactor将响应数据写入SocketChannel。
# 参考
[Scalable IO in Java](http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf)