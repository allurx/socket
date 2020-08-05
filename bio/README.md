# 基于bio的tcp网络通信
1. 启动服务端  
运行[Server](https://github.com/Allurx/socket-chat/blob/master/bio/bio-server/src/main/java/red/zyc/socket/bio/server/Server.java)的main方法启动服务端。服务端默认限制最多3个客户端连接，
当然你也可以更改服务端线程池的配置以启动更多的客户端。
2. 启动客户端    
运行[Client](https://github.com/Allurx/socket-chat/blob/master/bio/bio-client/src/main/java/red/zyc/socket/bio/client/Client.java)的main方法启动客户端。
