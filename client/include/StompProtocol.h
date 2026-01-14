#pragma once

#include <string>
#include <map>
#include <vector>
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

public:
    StompProtocol(ConnectionHandler* handler);
    void processKeyboardCommand(std::string line);
    void processServerFrame(std::string frame);
    bool shouldTerminate();
};
