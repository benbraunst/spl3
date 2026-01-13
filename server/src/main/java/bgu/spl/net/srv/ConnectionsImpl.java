package bgu.spl.net.srv;

import bgu.spl.net.impl.data.User;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    ConcurrentHashMap<String, List<Integer>> channelSubscriptions;
    ConcurrentHashMap<Integer, User> connectionIdToAuthUser;

    public ConnectionsImpl() {
        this.activeConnections = new ConcurrentHashMap<>();
        this.channelSubscriptions = new ConcurrentHashMap<>();
        this.connectionIdToAuthUser = new ConcurrentHashMap<>();
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        List<Integer> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers) {
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);

        if (handler != null) {
            // Add any necessary cleanup or notification logic here
            activeConnections.remove(connectionId);
            connectionIdToAuthUser.remove(connectionId);

            // Also remove the connectionId from any channel subscriptions
            for (List<Integer> subscribers : channelSubscriptions.values()) {
                subscribers.remove(Integer.valueOf(connectionId));
            }
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    @Override
    public void subscribe(String channel, int connectionId) {
        // Thread-safe for concurrent reads and writes
        channelSubscriptions.putIfAbsent(channel, new java.util.concurrent.CopyOnWriteArrayList<>());

        channelSubscriptions.get(channel).add(connectionId);
    }

    @Override
    public void unsubscribe(String channel, int connectionId) {
        List<Integer> subscribers = channelSubscriptions.get(channel);

        if (subscribers != null) {
            subscribers.remove(Integer.valueOf(connectionId));
        }
    }

}
