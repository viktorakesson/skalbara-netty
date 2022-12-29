package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
public class NodeHandler {

    private final ReverseProxyServer server;

    private final ReentrantReadWriteLock lock;
    private boolean alive = true;

    private int portCounter;
    private final List<Node> activeNodes;
    private final List<Node> closingQueue;
    private final List<Node> startingQueue;
    private final int minimumAmountOfNodes;

    public NodeHandler(ReverseProxyServer server) {
        this.server = server;
        this.activeNodes = new ArrayList<>();
        this.closingQueue = new ArrayList<>();
        this.startingQueue = new ArrayList<>();
        this.minimumAmountOfNodes = 3;
        this.portCounter = 8080;
        this.lock = new ReentrantReadWriteLock();

        this.startThread();
        this.startNodes(minimumAmountOfNodes);
    }

    public Node getLeastUsedNode() {
        try {
            lock.readLock().lock();
            return activeNodes.stream().min(Comparator.comparing(Node::getRequests)).orElseThrow(RuntimeException::new);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void startNodes(int nrOfNodes) {
        for (var i = 0; i < nrOfNodes; i++) {
            var node = new Node(portCounter++);
            startingQueue.add(node);

            try {
                node.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeNodes(int nrOfNodes) {
        for (var i = 1; i < nrOfNodes; i++) {
            var node = activeNodes.get(activeNodes.size() - i);
            activeNodes.remove(node);
            closingQueue.add(node);
        }

    }

    private void startThread() {
        var thread = new Thread(this::runThread);

        thread.start();
    }

    public void activateNode(Node node) {

        try {
            lock.writeLock().lock();
            startingQueue.remove(node);
            activeNodes.add(node);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void activateStartedNodes() {

        for (Node node : startingQueue) {
            try {
                var b = new Bootstrap();
                b.group(server.getWorkerGroup())
                        .channel(NioSocketChannel.class)
                        .handler(new NodeInitializer(node, this))
                        .connect("localhost", node.getPort()).sync();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void closeAll() {
        try {
            lock.writeLock().lock();
            alive = false;
            activeNodes.forEach(Node::stop);
            closingQueue.forEach(Node::stop);
            startingQueue.forEach(Node::stop);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void runThread() {
        try {
            lock.writeLock().lock();
            if (!alive) {
                return;
            }

            activateStartedNodes();
            //checkup();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }

        try {
            Thread.sleep(1000);
        } catch(Exception e) {
            e.printStackTrace();
        }

        runThread();
    }

}
