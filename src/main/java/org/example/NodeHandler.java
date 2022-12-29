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

    public void closeNode() {

        var node = activeNodes.stream().min(Comparator.comparing(n -> n.getConnections().size())).orElseThrow();
        System.out.println("removing node: " + node.getPort());
        node.stop();
        activeNodes.remove(node);
        closingQueue.add(node);
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

    private void removeClosedNodes() {

        var iterator = closingQueue.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();
            if (!node.getConnections().isEmpty()) continue;
            node.stop();
            iterator.remove();
        }

    }

    private void adjustNumberOfNodes() {

        var currentLoad = activeNodes.stream().mapToInt(Node::getRequests).average();
        if (currentLoad.isEmpty())
            return;

        activeNodes.forEach(Node::resetRequests);

        if (currentLoad.getAsDouble() > 3.0 || activeNodes.size() < minimumAmountOfNodes) {
            startNodes(1);
        } else if (currentLoad.getAsDouble() < 1.0 && activeNodes.size() > minimumAmountOfNodes) {
            closeNode();
        }

    }

    public void closeAll() {
        try {
            lock.writeLock().lock();
            alive = false;
            startingQueue.forEach(Node::stop);
            activeNodes.forEach(Node::stop);
            closingQueue.forEach(Node::stop);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void runThread() {

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            lock.writeLock().lock();
            if (!alive) {
                return;
            }

            activateStartedNodes();
            adjustNumberOfNodes();
            removeClosedNodes();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }

        runThread();
    }

}
