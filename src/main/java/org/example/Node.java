package org.example;

import io.netty.channel.Channel;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
public class Node {

    private final ReentrantReadWriteLock lock;
    private final int port;
    private Process process;
    private int requests;
    private List<Channel> connections;

    public Node(int port) {
        this.port = port;
        this.requests = 0;
        this.connections = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void start() throws IOException {

        System.out.println("Node start: " + port + ".");
        var args = new String[] {
                "java",
                "-jar",
                "spring.jar",
                "--server.port=" + port
        };

        this.process = Runtime
                .getRuntime()
                .exec(args);
    }

    public void stop() {

        System.out.println("Node stop: " + port + ".");
        if (process == null)
            return;

        process.destroyForcibly();
        process = null;

    }

    public void addRequest() {
        try {
            lock.writeLock().lock();
            this.requests++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addConnection(Channel channel) {
        try {
            lock.writeLock().lock();
            this.connections.add(channel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeConnection(Channel channel) {
        try {
            lock.writeLock().lock();
            this.connections.remove(channel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void resetRequests() {
        try {
            lock.writeLock().lock();
            this.requests = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

}
