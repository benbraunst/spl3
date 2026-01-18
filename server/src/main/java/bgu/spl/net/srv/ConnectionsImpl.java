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
        System.out.println("[ConnectionsImpl] Broadcasting to channel '" + channel + "'");
        
        if (subscribers != null) {
            System.out.println("[ConnectionsImpl] Channel has " + subscribers.size() + " subscriber(s)");
            
            // Create headers template once (without body to avoid String.format issues)
            String messageId = java.util.UUID.randomUUID().toString();
            String headerTemplate = "MESSAGE\n" +
                                   "subscription:%d\n" +
                                   "message-id:" + messageId + "\n" +
                                   "destination:" + channel + "\n" +
                                   "\n";
            
            // Use entrySet() to avoid race condition between get() calls
            for (java.util.Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {
                Integer connectionId = entry.getKey();
                Integer subscriptionId = entry.getValue();
                if (subscriptionId != null) {
                    // Format only the header part, then append body
                    String headers = String.format(headerTemplate, subscriptionId);
                    String personalizedMsg = headers + msg;
                    System.out.println("[ConnectionsImpl] Sending MESSAGE to client " + connectionId + " (subscription ID: " + subscriptionId + ")");
                    send(connectionId, (T) personalizedMsg);
                }
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);

        if (handler != null) {
            System.out.println("[ConnectionsImpl] Disconnecting client " + connectionId);
            activeConnections.remove(connectionId);

            // Remove from all channel subscriptions
            int totalUnsubscribed = 0;
            for (String channel : channelSubscriptions.keySet()) {
                ConcurrentHashMap<Integer, Integer> subscribers = channelSubscriptions.get(channel);
                if (subscribers != null) {
                    Integer subId = subscribers.remove(connectionId);
                    if (subId != null) {
                        totalUnsubscribed++;
                        System.out.println("[ConnectionsImpl] Removed client " + connectionId + " from channel '" + channel + "'");
                    }
                }
            }
            System.out.println("[ConnectionsImpl] Client " + connectionId + " unsubscribed from " + totalUnsubscribed + " channel(s)");
        } else {
            System.out.println("[ConnectionsImpl] WARNING: Attempted to disconnect unknown client " + connectionId);
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    @Override
    public void subscribe(String channel, int connectionId, int subscriptionId) {
        System.out.println("[ConnectionsImpl] Subscribing client " + connectionId + " to channel '" + channel + "' with subscription ID " + subscriptionId);
        channelSubscriptions.putIfAbsent(channel, new ConcurrentHashMap<>());
        channelSubscriptions.get(channel).put(connectionId, subscriptionId);
        System.out.println("[ConnectionsImpl] Channel '" + channel + "' now has " + channelSubscriptions.get(channel).size() + " subscriber(s)");
    }

    @Override
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, Integer> channelSubscribers = channelSubscriptions.get(channel);
        if (channelSubscribers != null) {
            Integer removedSubId = channelSubscribers.remove(connectionId);
            if (removedSubId != null) {
                System.out.println("[ConnectionsImpl] Unsubscribed client " + connectionId + " from channel '" + channel + "' (subscription ID was " + removedSubId + ")");
                System.out.println("[ConnectionsImpl] Channel '" + channel + "' now has " + channelSubscribers.size() + " subscriber(s)");
            } else {
                System.out.println("[ConnectionsImpl] WARNING: Client " + connectionId + " was not subscribed to channel '" + channel + "'");
            }
        } else {
            System.out.println("[ConnectionsImpl] WARNING: Channel '" + channel + "' does not exist");
        }
    }
}
