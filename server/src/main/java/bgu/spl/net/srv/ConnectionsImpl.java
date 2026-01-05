package bgu.spl.net.srv;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.impl.data.User;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    ConcurrentHashMap<String, List<Integer>> channelSubscriptions;
    ConcurrentHashMap<Integer, User> connectionIdToAuthUser;


    // TODO: Constructors
    public ConnectionsImpl(ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections,
            ConcurrentHashMap<String, List<Integer>> channelSubscriptions,
            ConcurrentHashMap<Integer, User> connectionIdToAuthUser) {
        this.activeConnections = activeConnections;
        this.channelSubscriptions = channelSubscriptions;
        this.connectionIdToAuthUser = connectionIdToAuthUser;
    }

    public ConnectionsImpl() {
    }

    @Override
    public boolean send(int connectionId, T msg) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'send'");
    }

    @Override
    public void send(String channel, T msg) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'send'");
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'disconnect'");
    }

}
