#include <stdlib.h>
#include <iostream>
#include <thread>
#include <mutex>
#include <vector>
#include <sstream>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

using namespace std;

int main(int argc, char *argv[]) {
    // No arguments required start
    
    ConnectionHandler* connectionHandler = nullptr;
    StompProtocol* protocol = nullptr;
    thread* socketThread = nullptr;
    bool isConnected = false;

    // Thread 1 (Main): Keyboard Listener
    while (1) {
        const short bufsize = 1024;
        char buf[bufsize];
        cin.getline(buf, bufsize);
        string line(buf);
        
        // Parse command
        stringstream ss(line);
        string command;
        ss >> command;

        if (command == "login") {
            if (isConnected) {
                cout << "The client is already logged in, log out before trying to log in again" << endl;
                continue;
            }
            
            string hostPort;
            string username;
            string password;
            
            ss >> hostPort >> username >> password;
            
            // Parse host:port
            size_t colonPos = hostPort.find(':');
            if (colonPos == string::npos) {
                cout << "Invalid host:port format" << endl;
                continue;
            }
            
            string host = hostPort.substr(0, colonPos);
            short port = (short)stoi(hostPort.substr(colonPos + 1));
            
            connectionHandler = new ConnectionHandler(host, port);
            if (!connectionHandler->connect()) {
                cout << "Could not connect to server" << endl;
                delete connectionHandler;
                connectionHandler = nullptr;
                continue;
            }
            
            protocol = new StompProtocol(connectionHandler, host, port);
            
            // Start Socket Thread
            socketThread = new thread([connectionHandler, protocol, &isConnected]() {
                while (1) { 
                    string frame;
                    // Get frame until null character
                    if (!connectionHandler->getFrameAscii(frame, '\0')) {
                        cout << "Disconnected from server" << endl;
                        isConnected = false;
                        break;
                    }
                    protocol->processServerFrame(frame);
                    if (protocol->shouldTerminate()) {
                        isConnected = false;
                        connectionHandler->close();
                        break; 
                    }
                }
            });
            
            isConnected = true;
            protocol->processKeyboardCommand(line);
            
        } else if (command == "logout") {
            if (!isConnected) {
                cout << "Not connected" << endl;
                continue;
            }
            protocol->processKeyboardCommand(line);

            // Wait for socket thread to finish
            if (socketThread && socketThread->joinable()) {
                socketThread->join();
                delete socketThread;
                socketThread = nullptr;
            }
            delete protocol;
            protocol = nullptr;
            delete connectionHandler;
            connectionHandler = nullptr;
            isConnected = false;
            
        } else {
             if (!isConnected) {
                cout << "Not connected. Please login first." << endl;
                continue;
            }
            protocol->processKeyboardCommand(line);
        }
    }
    
    return 0;
}