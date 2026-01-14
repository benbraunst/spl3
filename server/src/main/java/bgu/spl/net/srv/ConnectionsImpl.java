package bgu.spl.net.srv;

import bgu.spl.net.impl.data.User;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    // Channel -> { ConnectionId -> SubscriptionId }
    ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelSubscriptions;
    // ConnectionId -> { SubscriptionId -> Channel }
    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, String>> connectionIdToSubscriptions;
    ConcurrentHashMap<Integer, User> connectionIdToAuthUser;

    public ConnectionsImpl() {
        this.activeConnections = new ConcurrentHashMap<>();
        this.channelSubscriptions = new ConcurrentHashMap<>();
        this.connectionIdToSubscriptions = new ConcurrentHashMap<>();
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
        ConcurrentHashMap<Integer, Integer> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers.keySet()) {
                Integer subscriptionId = subscribers.get(connectionId);
                String personalizedMsg = createMessage(msg, subscriptionId, channel);
                send(connectionId, (T) personalizedMsg);
            }
        }
    }
    
    private String createMessage(T msg, Integer subscriptionId, String channel) {
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
            // Use the reverse map for efficient removal
            ConcurrentHashMap<Integer, String> clientSubs = connectionIdToSubscriptions.get(connectionId);
            if (clientSubs != null) {
                for (String channel : clientSubs.values()) {
                    if (channelSubscriptions.containsKey(channel)) {
                        channelSubscriptions.get(channel).remove(connectionId);
                    }
                }
                connectionIdToSubscriptions.remove(connectionId);
            }
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
        connectionIdToSubscriptions.put(connectionId, new ConcurrentHashMap<>());
    }

    @Override
    public void subscribe(String channel, int connectionId, int subscriptionId) {
        channelSubscriptions.putIfAbsent(channel, new ConcurrentHashMap<>());
        channelSubscriptions.get(channel).put(connectionId, subscriptionId);
        
        connectionIdToSubscriptions.putIfAbsent(connectionId, new ConcurrentHashMap<>());
        connectionIdToSubscriptions.get(connectionId).put(subscriptionId, channel);
    }

    @Override
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, Integer> subscribers = channelSubscriptions.get(channel);

        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }
    
    public void unsubscribe(int subscriptionId, int connectionId) {
        if (connectionIdToSubscriptions.containsKey(connectionId)) {
            String channel = connectionIdToSubscriptions.get(connectionId).remove(subscriptionId);
            if (channel != null) {
                unsubscribe(channel, connectionId);
            }
        }
    }

}
