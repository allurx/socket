# socket-chat
基于Java Socket Api通过控制台输入模拟点对点以及广播式通信。
# 快速开始
1. 启动服务端  
运行[Server](https://github.com/Allurx/socket-chat/blob/master/socket-server/src/main/java/red/zyc/socket/server/Server.java)的main方法启动服务端。服务端默认限制最多3个客户端连接，
当然你也可以更改服务端线程池的配置以启动更多的客户端。
2. 启动客户端    
运行[Client](https://github.com/Allurx/socket-chat/blob/master/socket-client/src/main/java/red/zyc/socket/client/Client.java)的main方法启动客户端。
3. 发送消息  
    1. 点对点   
在任意启动的客户端控制台输入一些消息然后按下回车键即可在服务端控制台看到客户端发送过来的信息。
    2. 广播   
在服务端控制台输入一些消息然后按下回车键即可在所有已启动的客户端控制台中看到服务端广播的信息。
