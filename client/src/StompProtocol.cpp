#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <sstream>
#include <vector>

using namespace std;

StompProtocol::StompProtocol(ConnectionHandler* handler) : 
    handler(handler), isConnected(false), subscriptions(), 
    receiptIdCounter(0), subscriptionIdCounter(0), receiptActions(), currentUser(""), gameManager() 
{}

bool StompProtocol::shouldTerminate() {
    return !handler->isConnected;
}

vector<string> split(const string& str, char delimiter) {
    vector<string> tokens;
    string token;
    istringstream tokenStream(str);
    while (getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

void StompProtocol::processKeyboardCommand(string line) {
    if (line.empty()) return;
    vector<string> args = split(line, ' ');
    string command = args[0];

    if (command == "login") {
        if (isConnected) {
            cout << "The client is already logged in, log out before trying again" << endl;
            return;
        }
        if (args.size() < 4) {
            cout << "Usage: login {host:port} {user} {password}" << endl;
            return;
        }
        
        string hostPort = args[1];
        size_t colonPos = hostPort.find(':');
        if (colonPos == string::npos) {
             cout << "Invalid host:port format" << endl;
             return;
        }
        // Host and port are handled by StompClient main loop for reconnection / connection
        // Protocol just assumes handler is ready or will be ready
        // Wait, StompClient creates the protocol. If we are reconnecting, we might need a new connection logic?
        // The assignment usually implies we connect once? Or we can reconnect?
        // "The client should support running the client, connecting, disconnecting and reconnecting."
        // But StompClient.cpp sets up ConnectionHandler.
        // If "login" initiates the connection, then StompClient should wait for "login" before connect().
        // BUT the assignment says "The client needs to handle two tasks simultaneously... reading user input... reading server responses".
        // If we are not connected, we can't read server responses.
        // Let's assume StompClient main handles the TCP connection, OR `processKeyboardCommand` triggers it.
        // Given the signature `processKeyboardCommand(string line)`, it doesn't take the handler reference again if it changed.
        
        // Actually, the standard way in these assignments is that the Main loop checks if connected.
        // If the user types "login", we might expect the TCP connection to exist?
        // No, usually "login" *Command* initiates the STOMP CONNECT.
        // But we need a TCP connection first.
        // If the Main creates ConnectionHandler with parsed arguments, it might already be connected.
        
        // Let's look at StompClient main loop plan again.
        // Main connects TCP.
        // Then loop.
        // Wait, if I logout, I send DISCONNECT.
        // STOMP DISCONNECT usually closes the socket?
        // If so, "login" again would need to re-establish TCP.
        // If ConnectionHandler is passed to Protocol, Protocol can use it.
        // But ConnectionHandler takes host/port in constructor.
        
        // Let's assume for now the TCP is managed outside or persisting.
        // We will just send CONNECT frame.
        
        currentUser = args[2];
        string password = args[3];
        
        string frame = "CONNECT\n";
        frame += "accept-version:1.2\n";
        frame += "host:stomp.cs.bgu.ac.il\n";
        frame += "login:" + currentUser + "\n";
        frame += "passcode:" + password + "\n";
        frame += "\n";
        
        handler->sendFrameAscii(frame, '\0'); // Assuming specific send method or just sendLine with delimiters?
        // ConnectionHandler usually sends lines. We need to implement sendFrame.
        // We'll trust ConnectionHandler::sendFrameAscii (implied needs check) or use sendBytes.
        // I will use `sendFrameAscii` placeholder and check ConnectionHandler.
    }
    else if (command == "join") {
        if (!isConnected) { cout << "Not connected" << endl; return; }
        string game = args[1];
        int subId = subscriptionIdCounter++;
        subscriptions[game] = subId;
        int receipt = receiptIdCounter++;
        
        string frame = "SUBSCRIBE\n";
        frame += "destination:/" + game + "\n";
        frame += "id:" + to_string(subId) + "\n";
        frame += "receipt:" + to_string(receipt) + "\n";
        frame += "\n";
        
        receiptActions[receipt] = "joined channel " + game;
        handler->sendFrameAscii(frame, '\0');
    }
    else if (command == "exit") {
        if (!isConnected) { cout << "Not connected" << endl; return; }
        string game = args[1];
        if (subscriptions.find(game) == subscriptions.end()) {
             cout << "Not subscribed to " << game << endl;
             return;
        }
        int subId = subscriptions[game];
        subscriptions.erase(game);
        int receipt = receiptIdCounter++;

        string frame = "UNSUBSCRIBE\n";
        frame += "id:" + to_string(subId) + "\n";
        frame += "receipt:" + to_string(receipt) + "\n";
        frame += "\n";

        receiptActions[receipt] = "exited channel " + game;
        handler->sendFrameAscii(frame, '\0');
    }
    else if (command == "logout") {
        if (!isConnected) { cout << "Not connected" << endl; return; }
        int receipt = receiptIdCounter++;
        
        string frame = "DISCONNECT\n";
        frame += "receipt:" + to_string(receipt) + "\n";
        frame += "\n";
        
        receiptActions[receipt] = "disconnect";
        handler->sendFrameAscii(frame, '\0');
    }
    else if (command == "report") {
        if (!isConnected) { cout << "Not connected" << endl; return; }
        string file = args[1];
        names_and_events parsed = parseEventsFile(file);
        // parsed.team_a_name, team_b_name, events...
        
        // Allow spaces in game name? The assignment usually assumes single word or handled?
        // "join {game_name}" -> one arg.
        // parseEventsFile returns parsed objects.
        
        // Check "team a_team b" topic or assumed?
        // Usually, the topic is not part of the file, but implicit?
        // Wait, "report {file}" -> where do we send it?
        // "Send SEND frames for each event."
        // To which destination?
        // "note that the topic name is not passed as an argument... The client should verify that it is subscribed to the channel..." 
        // "The channel name is the concatenation of team a name and team b name separated by underscore"
        // Example: "Germany_Spain"
        
        string gameName = parsed.team_a_name + "_" + parsed.team_b_name;
        if (subscriptions.find(gameName) == subscriptions.end()) {
            cout << "Not subscribed to " << gameName << endl;
            return; // Or continue? Best to return.
        }
        
        for (const auto& ev : parsed.events) {
            string frame = "SEND\n";
            frame += "destination:/" + gameName + "\n";
            frame += "\n";
            
            // Serialize Event to Body
            frame += "user:" + currentUser + "\n";
            frame += "team a:" + parsed.team_a_name + "\n";
            frame += "team b:" + parsed.team_b_name + "\n";
            frame += "event name:" + ev.get_name() + "\n";
            frame += "time:" + to_string(ev.get_time()) + "\n";
            
            frame += "general game updates:\n";
            for (auto const& pair : ev.get_game_updates()) {
                frame += "\t" + pair.first + ":" + pair.second + "\n";
            }
            
            frame += "team a updates:\n";
             for (auto const& pair : ev.get_team_a_updates()) {
                frame += "\t" + pair.first + ":" + pair.second + "\n";
            }
             
            frame += "team b updates:\n";
             for (auto const& pair : ev.get_team_b_updates()) {
                frame += "\t" + pair.first + ":" + pair.second + "\n";
            }
            
            frame += "description:\n" + ev.get_discription() + "\n";
            
            handler->sendFrameAscii(frame, '\0');
            
            // Add to local storage too? No, usually we only store on MESSAGE.
            // But if we report it, we are the source.
            // "The client should print the event to the screen..."
            // Usually we rely on the server reflecting it back?
            // "If the server echoes the messages back to the sender... yes."
            // But STOMP usually broadcasts to "subscribers". 
            // If we are subscribed, we get it back.
        }
    }
    else if (command == "summary") {
        string gameName = args[1];
        string user = args[2];
        string file = args[3];
        gameManager.writeSummaryToFile(gameName, user, file);
    }
}

void StompProtocol::processServerFrame(string frame) {
    istringstream stream(frame);
    string command;
    getline(stream, command);
    
    // Parse headers
    map<string, string> headers;
    string line;
    while (getline(stream, line) && !line.empty()) {
        if (line == "\n" || line == "\r") break; // should detect empty line
        // careful with CRLF
        if (line.back() == '\r') line.pop_back();
        if (line.empty()) break;
        
        size_t colon = line.find(':');
        if (colon != string::npos) {
            headers[line.substr(0, colon)] = line.substr(colon + 1);
        }
    }
    
    // Parse body
    string body;
    // The rest of the stream is body.
    // getline consumes delimiter, so we read until end?
    // Using stringstream iterator might be better.
    // Or just appending lines?
    // The body might contain newlines.
    
    // NOTE: current pos is after the empty line.
    // getline(stream, body, '\0'); // read until null char?
    // frame string passed here usually contains everything up to \0.
    // But processServerFrame likely receives the string WITHOUT the \0 (handled by ConnectionHandler).
    // Let's assume remaining stream content is body.
     stringstream bodyStream;
     bodyStream << stream.rdbuf();
     body = bodyStream.str();

    if (command == "CONNECTED") {
        isConnected = true;
        cout << "Login successful" << endl;
    }
    else if (command == "MESSAGE") {
        // Parse body to Event
        Event ev(body); 
        string gameName = "";
        // Extract game name from destination header logic? 
        // destination:/topic/Germany_Spain -> gameName = Germany_Spain
        if (headers.count("destination")) {
            string dest = headers["destination"];
            // remove prefix /topic/ or /
            size_t lastSlash = dest.rfind('/');
            if (lastSlash != string::npos) {
                gameName = dest.substr(lastSlash + 1);
            }
        }
        
        // Also need "user" who sent it?
        // It's in the body "user:..." field I serialized.
        // We can parse it from body in Event(body) or do it here.
        // Let's assume Event helps or we parse manually.
        // Re-parsing known fields from Event object?
        // Actually, the Event object doesn't have "user" field in the starter code.
        // But GameManager needs it.
        // We'll parse "user" from the body manually before passing to Event.
        
        string user = "";
        istringstream bodyS(body);
        string l;
        while(getline(bodyS, l)) {
            if (l.find("user:") == 0) {
                 user = l.substr(5);
                 break;
            }
        }
        
        gameManager.addEvent(ev, user);
        // Don't print "Received message" unless asked.
    }
    else if (command == "RECEIPT") {
        if (headers.count("receipt-id")) {
            int id = stoi(headers["receipt-id"]);
            if (receiptActions.count(id)) {
                string action = receiptActions[id];
                if (action == "disconnect") {
                    isConnected = false;
                    handler->close();
                    cout << "Disconnected" << endl; // Or separate logic?
                } else {
                    cout << action << endl; // "joined channel...", "exited channel..."
                }
                receiptActions.erase(id);
            }
        }
    }
    else if (command == "ERROR") {
        cout << "Error received: " << body << endl;
        isConnected = false;
        handler->close();
    }
}
