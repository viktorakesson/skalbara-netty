package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;

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
    private final List<Node> closedNodes;
    private final List<Node> startedNodes;
    private final int minimumAmountOfNodes;

    public NodeHandler(ReverseProxyServer server) {
        this.server = server;
        this.activeNodes = new ArrayList<>();
        this.closedNodes = new ArrayList<>();
        this.startedNodes = new ArrayList<>();
        this.minimumAmountOfNodes = 3;
        this.portCounter = 8080;
        this.lock = new ReentrantReadWriteLock();

        this.startThread();

    }

    private void startThread() {
        var thread = new Thread(this::runThread);

        thread.start();
    }

    private void runThread() {

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            lock.writeLock().lock();
            if (!alive) {
                return;
            }

            activateStartedNodes();
            balanceLoad();
            removeClosedNodes();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }

        runThread();
    }

    public void createNodes(int nrOfNodes) {

        for (var i = 0; i < nrOfNodes; i++) {
            var node = new Node(portCounter++);
            startedNodes.add(node);
            try {
                node.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void closeNode() {

        var node = activeNodes.stream().min(Comparator.comparing(n -> n.getConnections().size())).orElseThrow();
        System.out.println("Removing node: " + node.getPort());
        node.stop();
        activeNodes.remove(node);
        closedNodes.add(node);
    }

    public void activateNode(Node node) {

        try {
            lock.writeLock().lock();
            startedNodes.remove(node);
            activeNodes.add(node);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void activateStartedNodes() {

        for (var node : startedNodes)
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

    private void removeClosedNodes() {

        var iterator = closedNodes.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();
            if (!node.getConnections().isEmpty()) continue;
            node.stop();
            iterator.remove();
        }
    }

    public Node getLeastUsedNode() {
        try {
            lock.readLock().lock();
            return activeNodes.stream().min(Comparator.comparing(Node::getRequests)).orElseThrow(RuntimeException::new);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void balanceLoad() {

        if (activeNodes.size() < minimumAmountOfNodes)
            createNodes(1);

        var currentLoad = activeNodes.stream().mapToInt(Node::getRequests).average();
        if (currentLoad.isEmpty())
            return;

        activeNodes.forEach(Node::resetRequests);

        if (currentLoad.getAsDouble() > 3.0) {
            System.out.println(">>>");
            createNodes(1);
        } else if (currentLoad.getAsDouble() < 1.0 && activeNodes.size() > minimumAmountOfNodes) {
            System.out.println("<<<");
            closeNode();
        }
    }

    public void shutdown() {
        try {
            lock.writeLock().lock();
            alive = false;
            startedNodes.forEach(Node::stop);
            activeNodes.forEach(Node::stop);
            closedNodes.forEach(Node::stop);
        } finally {
            lock.writeLock().unlock();
        }
    }

}
