package bgu.spl.net.srv;

import bgu.spl.net.impl.data.User;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    // Channel -> { ConnectionId -> SubscriptionId }
    ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscriptions;
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
        ConcurrentHashMap<Integer, String> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers.keySet()) {
                String subscriptionId = subscribers.get(connectionId);
                String personalizedMsg = createMessage(msg, subscriptionId, channel);
                send(connectionId, (T) personalizedMsg);
            }
        }
    }
    
    private String createMessage(T msg, String subscriptionId, String channel) {
        // Assuming msg is the body of the message
        return "MESSAGE\n" +
               "subscription:" + subscriptionId + "\n" +
               "message-id:" + java.util.UUID.randomUUID().toString() + "\n" +
               "destination:" + channel + "\n" +
               "\n" +
               msg;
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);

        if (handler != null) {
            activeConnections.remove(connectionId);
            connectionIdToAuthUser.remove(connectionId);

            // Also remove the connectionId from any channel subscriptions
            for (ConcurrentHashMap<Integer, String> subscribers : channelSubscriptions.values()) {
                subscribers.remove(connectionId);
            }
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    @Override
    public void subscribe(String channel, int connectionId, String subscriptionId) {
        channelSubscriptions.putIfAbsent(channel, new ConcurrentHashMap<>());
        channelSubscriptions.get(channel).put(connectionId, subscriptionId);
    }

    @Override
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, String> subscribers = channelSubscriptions.get(channel);

        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }

}
