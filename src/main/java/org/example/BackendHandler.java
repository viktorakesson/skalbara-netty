package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class BackendHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final Channel channel;
    private final Node node;

    public BackendHandler(Channel channel, Node node) {
        this.channel = channel;
        this.node = node;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        channel.writeAndFlush(byteBuf.copy());
        node.addRequest();
        System.out.println("Request to node: " + node.getPort());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(">>> Channel active.");
        node.addConnection(channel);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("<<< Channel inactive");
        node.removeConnection(channel);
        super.channelInactive(ctx);
    }
}
