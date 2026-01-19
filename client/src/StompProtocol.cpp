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

        currentUser = args[2];
        string password = args[3];
        
        string frame = "CONNECT\n";
        frame += "accept-version:1.2\n";
        frame += "host:stomp.cs.bgu.ac.il\n";
        frame += "login:" + currentUser + "\n";
        frame += "passcode:" + password + "\n";
        frame += "\n";
        
        handler->sendFrameAscii(frame, '\0');
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
        
        // Extract filename from path (remove directory path)
        string filename = file;
        size_t lastSlash = file.find_last_of("/\\");
        if (lastSlash != string::npos) {
            filename = file.substr(lastSlash + 1);
        }
        
        string gameName = parsed.team_a_name + "_" + parsed.team_b_name;
        if (subscriptions.find(gameName) == subscriptions.end()) {
            cout << "Not subscribed to " << gameName << endl;
            return;
        }
        
        bool firstEvent = true;  // Track only on first event
        for (const auto& ev : parsed.events) {
            string frame = "SEND\n";
            frame += "destination:/" + gameName + "\n";
            if (firstEvent) {
                frame += "file name:" + filename + "\n";
                firstEvent = false;
            }
            frame += "\n";
            
            // Serialize Event to Body
            frame += "user:" + currentUser + "\n";
            frame += "team a:" + parsed.team_a_name + "\n";
            frame += "team b:" + parsed.team_b_name + "\n";
            frame += "event name:" + ev.get_name() + "\n";
            frame += "time:" + to_string(ev.get_time()) + "\n";

            
            frame += "general game updates:\n";
            for (auto const& pair : ev.get_game_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
            
            frame += "team a updates:\n";
             for (auto const& pair : ev.get_team_a_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
             
            frame += "team b updates:\n";
             for (auto const& pair : ev.get_team_b_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
            
            frame += "description:\n" + ev.get_discription() + "\n";
            
            handler->sendFrameAscii(frame, '\0');
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
        
        string user = "";
        istringstream bodyS(body);
        string l;
        while(getline(bodyS, l)) {
            if (!l.empty() && l.back() == '\r') l.pop_back();
            if (l.find("user:") == 0) {
                 user = l.substr(5);
                 break;
            }
        }
        
        gameManager.addEvent(ev, user, gameName);
        cout << "Received key press: " << ev.get_name() << endl; // Debug print or requirement?
        cout << "Received event: " << ev.get_name() << " in game: " << gameName << endl;
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
