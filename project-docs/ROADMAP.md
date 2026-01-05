Based on the assignment specification (Assignment 3-SPL.pdf) and the provided skeleton, here is a comprehensive roadmap of the classes and functions you need to implement.

This is divided into Server Side (Java) and Client Side (C++).

Part 1: Server Side (Java)
The server requires refactoring the provided generic "Net" library to support the Connections interface and implementing the specific STOMP protocol logic.

1. Infrastructure (Refactoring & New Implementation)
These changes allow the server to send messages to clients proactively (not just request-response).

bgu.spl.net.srv.ConnectionsImpl (New Class)

Purpose: Implements the Connections<T> interface. Holds the map of active clients and manages topic subscriptions.

Key Fields:

ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections: Maps connection ID to handler.

ConcurrentHashMap<String, List<Integer>> channelSubscriptions: Maps channel names (topics) to subscribed user IDs.

ConcurrentHashMap<Integer, User>: (Optional) To map connection IDs to authenticated users.

Functions to Implement:

boolean send(int connectionId, T msg): Send a message to a specific client.

void send(String channel, T msg): Iterate over subscribers of channel and send the message to them.

void disconnect(int connectionId): Remove the client from active connections and subscriptions.

void connect(int connectionId, ConnectionHandler<T> handler): (Helper) Store a new connection.

bgu.spl.net.srv.ConnectionHandler (Interface - Modify)


Change: Add void send(T msg) to the interface.

bgu.spl.net.srv.BlockingConnectionHandler & NonBlockingConnectionHandler (Modify)

Change: Implement the new send(T msg) method. This should write the message to the output stream (Blocking) or send queue (NonBlocking).

bgu.spl.net.srv.BaseServer (TPC) & Reactor (Modify)

Change: Both need to hold an instance of ConnectionsImpl.


Change: Pass the Connections object to the protocol when initializing it via protocol.start(...).

2. Protocol Logic (The Brain)
bgu.spl.net.api.StompMessagingProtocolImpl (New Class)

Purpose: Implements StompMessagingProtocol<String>. Handles STOMP frames (CONNECT, SEND, SUBSCRIBE, etc.).

Key Fields:

int connectionId: Assigned in start.

Connections<String> connections: Reference to the global connections object.

boolean shouldTerminate: Flag for the main loop.

User currentUser: To track if the client is logged in.

Functions to Implement:

void start(int connectionId, Connections<String> connections): Save the ID and connections reference.

void process(String message): The main switch-case logic. Parse the raw STOMP frame (String) and handle:

CONNECT: Validate user/pass, check if already logged in. Send CONNECTED or ERROR.

SUBSCRIBE: Add client ID to the topic in Connections. Send RECEIPT.

UNSUBSCRIBE: Remove client ID from the topic. Send RECEIPT.

SEND: Check subscription. Construct a MESSAGE frame and use connections.send(channel, msg) to broadcast.

DISCONNECT: clear user data, set shouldTerminate = true, send RECEIPT.

boolean shouldTerminate(): Return the termination flag.

3. Entry Point & Database
bgu.spl.net.impl.stomp.StompServer (Implement Main)


Purpose: Parses command line arguments to run either TPC or Reactor .

Logic:

If args[1] == "tpc", create a ThreadPerClient server.

If args[1] == "reactor", create a Reactor server.

Initialize the chosen server with a StompMessagingProtocolImpl factory.

Database Integration (Phase 3)

Create a class (e.g., DatabaseManager) to communicate with the provided Python SQL server.

Implement methods for: registerUser, loginUser, logoutUser, addFileReport.

Part 2: Client Side (C++)
The client needs to handle two tasks simultaneously: reading user input (keyboard) and reading server responses (socket).

1. Core Structure
src/StompClient.cpp (Implement Main)

Purpose: Setup the connection and threads.

Logic:

Parse host:port from arguments.

Create ConnectionHandler.

Create a StompProtocol instance.

Thread 1 (Socket): Loop calling connectionHandler.getLine(). Pass result to protocol.processServerFrame().

Thread 2 (Keyboard/Main): Loop calling std::getline(cin). Pass result to protocol.processKeyboardCommand().

2. Protocol & Logic
include/StompProtocol.h & src/StompProtocol.cpp (Implement)

Purpose: Handle the logic for translating user commands to STOMP frames and processing server responses.

Key Fields:

bool isConnected: Track login status.

std::map<string, int> subscriptions: Map Topic Name -> Subscription ID.

std::map<string, string> gameEvents: Store events for the Summary command.

int receiptIdCounter, int subscriptionIdCounter.

Functions:

processKeyboardCommand(string line):

login: Parse host/port/user/pass. Send CONNECT frame.

join: Generate Sub ID, save to map. Send SUBSCRIBE frame.

exit: Find Sub ID from map. Send UNSUBSCRIBE frame.

report: Parse JSON file (using provided parser), store events in memory. Send SEND frames for each event.

summary: Don't send anything. Look up stored events for game_name + user and write formatted output to file.

logout: Send DISCONNECT frame.

processServerFrame(string frame):

CONNECTED: Mark client as logged in. Print "Login successful".

MESSAGE: Parse body. Store event data for "Summary" command. Print "Received message...".

RECEIPT: Handle specific logic (e.g., if receipt for disconnect, close socket; if receipt for join, print "Joined...").

ERROR: Print error message and/or close connection.

3. Helper Classes
include/GameManager.h (Recommended)

Since the summary command requires complex sorting and grouping (stats vs events, by time, etc.), it is highly recommended to create a helper class to store the parsed game updates received via MESSAGE frames.

Summary Checklist
Java: ConnectionsImpl.java (Logic for mapping Users <-> Topics).

Java: Refactor ConnectionHandler interface and implementations (add send).

Java: StompMessagingProtocolImpl.java (Handle CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT).

Java: StompServer.java (Main entry).

C++: StompClient.cpp (2 threads: 1 for IO, 1 for Socket).

C++: StompProtocol.cpp (Keyboard -> Frame, Frame -> Logic).