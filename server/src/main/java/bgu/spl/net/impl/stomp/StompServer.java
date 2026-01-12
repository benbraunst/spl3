package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.srv.Reactor;


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

        if (serverType.equals("tpc")) {
            new TPCServer<>(
                    port,
                    () -> new StompMessagingProtocolImpl(), // Protocol factory
                    () -> new StompEncoderDecoder()         // Encoder factory
            ).serve();
        } 
        
        else if (serverType.equals("reactor")) {
            new Reactor<>(
                    2, 
                    port,
                    () -> new StompMessagingProtocolImpl(), // Protocol factory
                    () -> new StompEncoderDecoder()         // Encoder factory
            ).serve();
        } 
        
        else {
            System.out.println("Unknown server type: " + serverType);
        }
    }
}
