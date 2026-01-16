package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelSubscriptions;

    public ConnectionsImpl() {
        this.activeConnections = new ConcurrentHashMap<>();
        this.channelSubscriptions = new ConcurrentHashMap<>();
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
            // Create base message once (assumes T is String for STOMP protocol)
            String baseMessage = "MESSAGE\n" +
                               "subscription:%d\n" +
                               "message-id:" + java.util.UUID.randomUUID().toString() + "\n" +
                               "destination:" + channel + "\n" +
                               "\n" +
                               msg;
            
            // Use entrySet() to avoid race condition between get() calls
            for (java.util.Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {
                Integer connectionId = entry.getKey();
                Integer subscriptionId = entry.getValue();
                if (subscriptionId != null) {
                    String personalizedMsg = String.format(baseMessage, subscriptionId);
                    send(connectionId, (T) personalizedMsg);
                }
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);

        if (handler != null) {
            activeConnections.remove(connectionId);

            // Remove from all channel subscriptions
            for (String channel : channelSubscriptions.keySet()) {
                ConcurrentHashMap<Integer, Integer> subscribers = channelSubscriptions.get(channel);
                if (subscribers != null) {
                    subscribers.remove(connectionId);
                }
            }
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    @Override
    public void subscribe(String channel, int connectionId, int subscriptionId) {
        channelSubscriptions.putIfAbsent(channel, new ConcurrentHashMap<>());
        channelSubscriptions.get(channel).put(connectionId, subscriptionId);
    }

    @Override
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, Integer> channelSubscribers = channelSubscriptions.get(channel);
        if (channelSubscribers != null) {
            channelSubscribers.remove(connectionId);
        }
    }
}
