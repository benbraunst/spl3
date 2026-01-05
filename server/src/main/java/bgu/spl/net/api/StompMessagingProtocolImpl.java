package bgu.spl.net.api;

import bgu.spl.net.impl.data.User;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    int connectionId;
    Connections<String> connections;
    boolean shouldTerminate = false;
    User currentUser;

    // TODO: Constructors
    public StompMessagingProtocolImpl(int connectionId, Connections<String> connections, boolean shouldTerminate,
            User currentUser) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = shouldTerminate;
        this.currentUser = currentUser;
    }

    @Override
    public void start(int connectionId, Connections<String> connections) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public boolean shouldTerminate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }
    
}
