# 基于aio的tcp网络通信
1. 启动服务端  
运行[Server](https://github.com/Allurx/socket/blob/master/aio/aio-server/src/main/java/red/zyc/socket/aio/server/Acceptor.java)的main方法启动服务端。
2. 启动客户端    
运行[Client](https://github.com/Allurx/socket/blob/master/aio/aio-client/src/main/java/red/zyc/socket/aio/client/Client.java)的main方法启动客户端。
3. 观察服务端和客户端的控制台输出即可看到一次tcp请求的完整流程。
# 架构
aio是jdk1.7对nio的一次版本升级，也可以称为nio2，在nio中所有高性能的io处理都是基于selector的事件驱动来完成的，
我们会将一系列的channel注册到某个selector中并添加我们对这个通道感兴趣的io事件（read/write/connection/accept），
然后当io事件来临时我们需要根据事件的类型来手动对channel进行数据的读写操作。以nio的方式来处理一次tcp通信的流程大致分为以下几个步骤：
1. accept一个SocketChannel（触发Acceptable事件）
2. 注册SocketChannel到selector中，设置感兴趣为可读事件
3. 读取SocketChannel中的数据，设置感兴趣为可写事件，然后唤醒selector（触发Readable事件）
4. 写数据到SocketChannel中，设置感兴趣为可读事件。（触发Writeable事件）
5. 重复3.4两步

从上面nio处理的流程可以看出我们是基于事件触发来处理io的，然后在事件发生时手动从channel中进行数据的读写。
而aio则是以一种**io事件发生完毕时的异步函数回调**的方式来处理io。我们现在不需要关心SocketChannel什么时候可获取、可读、可写，
我们只需要在这个channel中添加这些事件**发生完毕时**的回调函数，然后再以递归的方式继续添加回调函数直到channel关闭为止。
以aio的方式来处理一次tcp通信的流程大致分为以下几个步骤：
1. accept时添加一个CompletionHandler（回调函数），SocketChannel获取成功时则回调CompletionHandler的completed方法，我们就能
拿到这个SocketChannel，如果获取失败则回调failed方法。通常情况下我们可以在completed方法中继续以递归的方式继续调用accept方法以获取新的tcp连接。
2. 调用上一步获取到的SocketChannel的read方法并添加一个读CompletionHandler，在SocketChannel中的数据读取成功后会回调completed方法，
读取失败则回调failed方法。
3. 在第2步成功读取完毕后通常情况下会将读到的数据传递给业务线程池执行，然后将业务数据返回给客户端，这个时候就需要调用SocketChannel的write方法，‘
同样我们也需要添加一个写CompletionHandler，在write成功后会回调completed方法，然后我们继续以递归的方式调用SocketChannel的read方法添加
第2步中的那个读CompletionHandler作为回调函数，这样只要tcp连接没有关闭我们就能一直在2.3两步中来回切换读写。

