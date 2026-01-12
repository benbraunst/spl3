package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Reactor;
import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc|reactor>");
            return;
        }
        
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number: " + args[0]);
            return;
        }
        
        String serverType = args[1];
        Server<String> server;

        if (serverType.equals("tpc")) {
            server = new TPCServer<>(
                    port,
                    () -> new StompMessagingProtocolImpl(), // Protocol factory
                    () -> new StompEncoderDecoder()         // Encoder factory
            );
            server.serve();
        } 
        
        else if (serverType.equals("reactor")) {
            server = new Reactor<>(
                    2, 
                    port,
                    () -> new StompMessagingProtocolImpl(), // Protocol factory
                    () -> new StompEncoderDecoder()         // Encoder factory
            );
            server.serve();
        } 
        
        else {
            System.out.println("Unknown server type: " + serverType);
        }
    }
}
