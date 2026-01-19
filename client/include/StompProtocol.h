#pragma once

#include <string>
#include <map>
#include <vector>
#include <mutex>
#include "../include/ConnectionHandler.h"
#include "../include/GameManager.h"

class StompProtocol
{
private:
    ConnectionHandler* handler;
    bool isConnected;
    std::map<std::string, int> subscriptions; // topic -> subscriptionId
    int receiptIdCounter;
    int subscriptionIdCounter;
    std::map<int, std::string> receiptActions; // receiptId -> action description (e.g., "disconnect")
    std::string currentUser;
    GameManager gameManager;
    std::string connectedHost;
    short connectedPort;
    std::mutex mtx;

public:
    StompProtocol(ConnectionHandler* handler, std::string host, short port);
    void processKeyboardCommand(std::string line);
    void processServerFrame(std::string frame);
    bool shouldTerminate();
};
