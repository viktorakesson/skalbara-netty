package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Scanner;

public class Server {

    private final int port;
    private final EventLoopGroup bossGroup, workerGroup;

    public Server(int port) {
        this.port = port;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public void run() {
        var b = new ServerBootstrap();

        try {
            var channel = b
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new Handler(Server.this));
                        }
                    })
                    .bind(port)
                    .sync()
                    .channel();


            var scanner = new Scanner(System.in);
            while (!scanner.nextLine().equals("exit"));
//
//        nodeHandler.closeAll();
        channel.closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }


    }
}
