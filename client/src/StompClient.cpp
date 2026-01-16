#include <stdlib.h>
#include <iostream>
#include <thread>
#include <mutex>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

using namespace std;

// Synchronization flag? 
// Actually processKeyboardCommand logic handles disconnection which stops 'isConnected' flag.
// If socket closes, getFrameAscii returns false, thread should stop.
// If keyboard sees disconnect, it sends frame.

int main(int argc, char *argv[]) {
    if (argc < 3) {
        cerr << "Usage: " << argv[0] << " host port" << endl << endl;
        return -1;
    }
    string host = argv[1];
    short port = atoi(argv[2]);
    
    ConnectionHandler connectionHandler(host, port);
    cout << "Connecting..." << endl;
    if (!connectionHandler.connect()) {
        cerr << "Cannot connect to " << host << ":" << port << endl;
        return 1;
    }
    cout << "Connected!" << endl;

    StompProtocol protocol(&connectionHandler);
    cout << "Protocol initialized" << endl;

    // Thread 2: Socket Listener
    thread socketThread([&connectionHandler, &protocol]() {
        while (1) { 
            string frame;
            // Get frame until null character
            if (!connectionHandler.getFrameAscii(frame, '\0')) {
                cout << "Disconnected from server" << endl;
                break;
            }
            protocol.processServerFrame(frame);
            if (protocol.shouldTerminate()) break; 
        }
    });

    // Thread 1 (Main): Keyboard Listener
    // an infinite loop, using non-zero integer as a boolean
    while (1) {
        const short bufsize = 1024;
        char buf[bufsize];
        cin.getline(buf, bufsize);
        string line(buf);
        protocol.processKeyboardCommand(line);
        if (protocol.shouldTerminate()) break;
    }
    
    // Cleanup
    cout << "Joining socket thread..." << endl;
    if (socketThread.joinable()) socketThread.join();
    
    cout << "Exiting main..." << endl;
    return 0;
}