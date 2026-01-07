package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.srv.Reactor;
import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.api.MessageEncoderDecoder;

public class StompServer {

    public static void main(String[] args) {
        // if (args.length < 2) {
        //     System.err.println("Usage: java StompServer <port> <tpc|reactor> [numThreads]");
        //     System.exit(1);
        // }

        // try {
        //     int port = Integer.parseInt(args[0]);
        //     String serverType = args[1];
        //     Server<String> server;
        //     int numOfThreads = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        //     if (serverType.equals("tpc")) {
        //         server = new TPCServer<String>(
        //                 port,
        //                 () -> new StompMessagingProtocolImpl(),
        //                 () -> new StompEncoderDecoder());
        //         server.serve();
        //     } else if (serverType.equals("reactor")) {
        //         server = new Reactor<String>(
        //                 numOfThreads,
        //                 port,
        //                 () -> new StompMessagingProtocolImpl(),
        //                 () -> new StompEncoderDecoder());
        //         server.serve();
        //     } else {
        //         System.err.println("Invalid server type. Use 'tpc' or 'reactor'");
        //         System.exit(1);
        //     }
        // } catch (NumberFormatException e) {
        //     System.err.println("Invalid port or thread count: " + e.getMessage());
        //     System.exit(1);
        // }
    }
}
