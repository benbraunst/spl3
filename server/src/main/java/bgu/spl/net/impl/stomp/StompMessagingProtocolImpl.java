package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.util.HashMap;
import java.util.Map;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) return;

        String command = lines[0].trim();
        Map<String, String> headers = new HashMap<>();
        int i = 1;
        while (i < lines.length && !lines[i].trim().isEmpty()) {
            String[] pair = lines[i].split(":", 2);
            if (pair.length == 2) {
                headers.put(pair[0].trim(), pair[1].trim());
            }
            i++;
        }

        if (command.equals("CONNECT")) {
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        } else if (command.equals("DISCONNECT")) {
            String receipt = headers.get("receipt");
            if (receipt != null) {
                connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
            }
            shouldTerminate = true;
        } else if (command.equals("SEND")) {
            // TODO: Implement SEND logic
            System.out.println("SEND command received");
        } else if (command.equals("SUBSCRIBE")) {
            // TODO: Implement SUBSCRIBE logic
            System.out.println("SUBSCRIBE command received");
        } else if (command.equals("UNSUBSCRIBE")) {
            // TODO: Implement UNSUBSCRIBE logic
            System.out.println("UNSUBSCRIBE command received");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
