package bgu.spl.net.impl.stomp;

import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.util.HashMap;
import java.util.Map;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private boolean isLoggedIn = false;
    private String username;
    private Map<String, String> subscriptionIdToChannel = new HashMap<>(); // subId -> channel

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
        Map<String, String> headers = parseHeaders(lines);
        String body = extractBody(lines);

        switch (command) {
            case "CONNECT":
                handleConnect(headers);
                break;
            case "DISCONNECT":
                handleDisconnect(headers);
                break;
            case "SEND":
                handleSend(headers, body);
                break;
            case "SUBSCRIBE":
                handleSubscribe(headers);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(headers);
                break;
        }
    }

    private Map<String, String> parseHeaders(String[] lines) {
        Map<String, String> headers = new HashMap<>();
        int i = 1;
        while (i < lines.length && !lines[i].trim().isEmpty()) {
            String[] pair = lines[i].split(":", 2);
            if (pair.length == 2) {
                headers.put(pair[0].trim(), pair[1].trim());
            }
            i++;
        }
        return headers;
    }

    private String extractBody(String[] lines) {
        int i = 1;
        // Skip headers
        while (i < lines.length && !lines[i].trim().isEmpty()) {
            i++;
        }
        
        // Skip empty line separator
        if (i < lines.length && lines[i].trim().isEmpty()) {
             i++;
        }

        StringBuilder bodyBuilder = new StringBuilder();
        while (i < lines.length) {
            bodyBuilder.append(lines[i]).append("\n");
            i++;
        }
        
        if (bodyBuilder.length() > 0) {
            bodyBuilder.setLength(bodyBuilder.length() - 1); // remove last newline
            return bodyBuilder.toString();
        }
        return "";
    }

    private void handleConnect(Map<String, String> headers) {
        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String acceptVersion = headers.get("accept-version");
        
        // TODO: check accept-version?
        
        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);
        
        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || 
            status == LoginStatus.ADDED_NEW_USER) {
            
            isLoggedIn = true;
            username = login;
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        } else {
            String errorMsg = "Login failed";
            if (status == LoginStatus.WRONG_PASSWORD) errorMsg = "Wrong password";
            else if (status == LoginStatus.ALREADY_LOGGED_IN) errorMsg = "User already logged in";
            else if (status == LoginStatus.CLIENT_ALREADY_CONNECTED) errorMsg = "Client already connected";
            
            connections.send(connectionId, "ERROR\nmessage:" + errorMsg + "\n\n");
            shouldTerminate = true; 
            connections.disconnect(connectionId);
        }
    }

    private void handleDisconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");
        sendReceipt(receipt);
        Database.getInstance().logout(connectionId);
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void handleSend(Map<String, String> headers, String body) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }
        String destination = headers.get("destination");
        if (destination == null) {
            sendError("No destination header");
            return;
        }
        
        connections.send(destination, body);
        
        String receipt = headers.get("receipt");
        sendReceipt(receipt);
    }

    private void handleSubscribe(Map<String, String> headers) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }
        String destination = headers.get("destination");
        String id = headers.get("id");
        String receipt = headers.get("receipt");
        
        if (destination == null || id == null) {
            sendError("Missing headers");
            return;
        }
        
        subscriptionIdToChannel.put(id, destination);
        connections.subscribe(destination, connectionId, id);
        
        sendReceipt(receipt);
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }
        String id = headers.get("id");
        String receipt = headers.get("receipt");

        if (id == null) {
            sendError("Missing id header");
            return;
        }
        
        String channel = subscriptionIdToChannel.remove(id);
        if (channel != null) {
            connections.unsubscribe(channel, connectionId);
        }
        
        sendReceipt(receipt);
    }
    
    private void sendReceipt(String receiptId) {
        if (receiptId != null) {
            connections.send(connectionId, "RECEIPT\nreceipt-id:" + receiptId + "\n\n");
        }
    }
    
    private void sendError(String message) {
        connections.send(connectionId, "ERROR\nmessage:" + message + "\n\n");
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
