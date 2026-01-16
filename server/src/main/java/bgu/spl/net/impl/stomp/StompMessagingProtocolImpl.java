package bgu.spl.net.impl.stomp;

import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private boolean isLoggedIn = false;
    private Map<Integer, String> subscriptionIdToChannel;

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        subscriptionIdToChannel = new ConcurrentHashMap<>();
    }

    @Override
    public void process(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) return;

        // Parse command
        String command = lines[0].trim();

        // Parse headers
        Map<String, String> parsedMessage = new HashMap<>();
        int i = 1;
        while (i < lines.length && !lines[i].trim().isEmpty()) {
            String[] pair = lines[i].split(":", 2);
            if (pair.length == 2) {
                parsedMessage.put(pair[0].trim(), pair[1].trim());
            }
            i++;
        }

        // Skip empty line separator
        if (i < lines.length && lines[i].trim().isEmpty()) {
            i++;
        }

        // Parse body
        StringBuilder bodyBuilder = new StringBuilder();
        while (i < lines.length) {
            bodyBuilder.append(lines[i]).append("\n");
            i++;
        }

        if(bodyBuilder.length() > 0) {
            bodyBuilder.setLength(bodyBuilder.length() - 1); // remove last newline
        }
        
        parsedMessage.put("body", bodyBuilder.toString());
        
        switch (command) {
            case "CONNECT":
                Connect(parsedMessage);
                break;
            case "DISCONNECT":
                Disconnect(parsedMessage);
                break;
            case "SEND":
                Send(parsedMessage);
                break;
            case "SUBSCRIBE":
                Subscribe(parsedMessage);
                break;
            case "UNSUBSCRIBE":
                Unsubscribe(parsedMessage);
                break;
        }
    }

    private void Connect(Map<String, String> headers) {
        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String acceptVersion = headers.get("accept-version");
        
        // Validate STOMP version
        if(acceptVersion == null || !acceptVersion.contains("1.2")) {
            connections.send(connectionId, "ERROR\nmessage:Unsupported STOMP version\n\n");
            shouldTerminate = true;
            connections.disconnect(connectionId);
            return;
        }
        
        System.out.println("Login attempt: " + login + ", pass: " + passcode); // debug
        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);
        
        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || 
            status == LoginStatus.ADDED_NEW_USER) {
            isLoggedIn = true;
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
            System.out.println("Login successful for: " + login); // debug
        } else if (status == LoginStatus.CLIENT_ALREADY_CONNECTED) {
            // Send appropriate error and don't disconnect
            connections.send(connectionId, "ERROR\nmessage:Client already connected\n\n");
            System.out.println("Login failed: Client already connected"); // debug
        } else {
            // Send appropriate error and disconnect
            if (status == LoginStatus.WRONG_PASSWORD){
                connections.send(connectionId, "ERROR\nmessage:Wrong password\n\n");
                System.out.println("Login failed: Wrong password"); // debug
            }
            else if (status == LoginStatus.ALREADY_LOGGED_IN){
                connections.send(connectionId, "ERROR\nmessage:User already logged in\n\n");
                System.out.println("Login failed: User already logged in"); // debug
            }
            shouldTerminate = true;
            connections.disconnect(connectionId);
        }
    }

    private void Disconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");
        if(receipt == null) {
            sendError("Missing receipt header in DISCONNECT");
            return;
        }
        
        // Send receipt before terminating
        connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
        
        Database.getInstance().logout(connectionId);
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void Send(Map<String, String> parsedMessage) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }

        String destination = parsedMessage.get("destination");
        if (destination == null) {
            sendError("No destination header");
            return;
        }
        
        // Check if client is subscribed to the channel
        if (!subscriptionIdToChannel.containsValue(destination)) {
            sendError("Not subscribed to destination: " + destination);
            return;
        }
        connections.send(destination, parsedMessage.get("body"));
        
        // Send receipt if requested
        String receipt = parsedMessage.get("receipt");
        if (receipt != null) {
            connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
        }
    }

    private void Subscribe(Map<String, String> headers) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }
        String destination = headers.get("destination");
        String id = headers.get("id");
        
        if (destination == null) {
            sendError("Missing destination header");
            return;
        }
        
        try {
            int subscriptionId = Integer.parseInt(id);
            subscriptionIdToChannel.put(subscriptionId, destination);
            connections.subscribe(destination, connectionId, subscriptionId);
            
            // Send receipt if requested
            String receipt = headers.get("receipt");
            if (receipt != null) {
                connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
            }
        } catch (NumberFormatException | NullPointerException e) {
            sendError("Invalid or missing subscription id");
        }
    }

    private void Unsubscribe(Map<String, String> headers) {
        if (!isLoggedIn) {
            sendError("Not logged in");
            return;
        }
        String id = headers.get("id");
        
        try {
            int subscriptionId = Integer.parseInt(id);
            String channel = subscriptionIdToChannel.remove(subscriptionId);
            if (channel != null) {
                connections.unsubscribe(channel, connectionId);
            }
            
            // Send receipt if requested
            String receipt = headers.get("receipt");
            if (receipt != null) {
                connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
            }
        } catch (NumberFormatException | NullPointerException e) {
            sendError("Invalid or missing subscription id");
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
