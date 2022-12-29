package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NodeInitializer extends SimpleChannelInboundHandler<ByteBuf> {

    private final Node node;
    private final NodeHandler nodeHandler;

    public NodeInitializer(Node node, NodeHandler nodeHandler) {
        this.node = node;
        this.nodeHandler = nodeHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        nodeHandler.activateNode(node);
        System.out.println("Node active: " + node.getPort());
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();

    }
}
