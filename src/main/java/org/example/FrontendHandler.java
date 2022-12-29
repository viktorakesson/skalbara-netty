package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class FrontendHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final ReverseProxyServer server;

    private Channel outboundChannel;

    public FrontendHandler(ReverseProxyServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        var b = new Bootstrap();
        var node = server.getNodeHandler().getLeastUsedNode();

        try {
          this.outboundChannel = b.group(server.getWorkerGroup())
                  .channel(NioSocketChannel.class)
                  .handler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      protected void initChannel(SocketChannel socketChannel) throws Exception {
                          var p = socketChannel.pipeline();
                          p.addLast(new BackendHandler(inboundChannel, node, server));
                      }
                  })
                  .connect("localhost", node.getPort())
                  .channel();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        outboundChannel.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        outboundChannel.writeAndFlush(byteBuf.copy());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        outboundChannel.close();
        ctx.channel().close();
    }

}
