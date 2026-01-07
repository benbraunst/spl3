# Server-Side Implementation Roadmap

Based on the provided files and your requirement not to change the interfaces, here is the roadmap for the Server-side implementation. This plan focuses on implementing the logic within the provided constraints by modifying the implementation classes (BaseServer, Reactor, etc.) rather than the interfaces.

---

## 1. New Class: ConnectionsImpl ✅

<details>
<summary><b>Click to expand</b></summary>

**File:** `spl3/server/src/main/java/bgu/spl/net/srv/ConnectionsImpl.java`

Since the Connections interface is purely for sending/disconnecting, you need a concrete implementation that also handles subscription management.

**Implements:** `Connections<T>`

**Fields:**
- `ConcurrentHashMap<Integer, ConnectionHandler<T>> connIdToHandler`: Maps connection ID to the client's handler (for sending).
- `ConcurrentHashMap<String, List<Integer>> channelToSubscribers`: Maps a channel name (topic) to a list of subscribed connection IDs.
- `ConcurrentHashMap<Integer, User> connIdToUser`: Maps connection ID to a User object (for login status).

**Interface Methods (from Connections.java):**
- `boolean send(int connectionId, T msg)`: Retrieve handler from connIdToHandler and call handler.send(msg).
- `void send(String channel, T msg)`: Look up channelToSubscribers, iterate through IDs, and send to each.
- `void disconnect(int connectionId)`: Remove from all maps.

**Additional Methods (Crucial):**
- `void addConnection(int connectionId, ConnectionHandler<T> handler)`: To register a new client.
- `void subscribe(String channel, int connectionId)`: Add ID to the channel's list.
- `void unsubscribe(String channel, int connectionId)`: Remove ID from the channel's list.

</details>

---

## 2. Update Implementation: BlockingConnectionHandler & NonBlockingConnectionHandler ✅

<details>
<summary><b>Click to expand</b></summary>

**Files:** 
- `spl3/server/src/main/java/bgu/spl/net/srv/BlockingConnectionHandler.java`
- `spl3/server/src/main/java/bgu/spl/net/srv/NonBlockingConnectionHandler.java`

You must implement the `send()` method defined in the ConnectionHandler interface.

**Refactoring:**
- Implement `void send(T msg)`.
- **For BlockingConnectionHandler:** Encode msg and write it to the out stream (thread-safe).
- **For NonBlockingConnectionHandler:** Add msg to a write queue and request a write operation from the reactor.

**BlockingConnectionHandler Implementation:**
```java
@Override
public void send(T msg) {
    synchronized (out) {
        try {
            out.write(encdec.encode(msg));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

**NonBlockingConnectionHandler Implementation:**
```java
@Override
public void send(T msg) {
    writeQueue.add(ByteBuffer.wrap(encdec.encode(msg)));
    reactor.updateInterestedOps(chan, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
}
```

</details>

---

## 3. New Class: StompMessagingProtocolImpl

<details>
<summary><b>Click to expand</b></summary>

**File:** `spl3/server/src/main/java/bgu/spl/net/api/StompMessagingProtocolImpl.java`

This class implements the logic for STOMP frames.

**Implements:** `StompMessagingProtocol<String>`

**Fields:**
- `int connectionId`: Set in `start()`.
- `Connections<String> connections`: Set in `start()`.
- `boolean shouldTerminate`: To control connection lifecycle.
- `User currentUser`: To track login state.

**Methods:**

- **`start(int connectionId, Connections<String> connections)`**: Initialize fields.

- **`process(String message)`**: Parse the raw string (STOMP frame) and handle commands:
  - **CONNECT:** Verify user/pass. Call `connections.send(id, "CONNECTED...")`.
  - **SUBSCRIBE:** Cast connections to `ConnectionsImpl` and call `subscribe(...)`. *(Casting necessary because interface lacks subscribe method)*
  - **UNSUBSCRIBE:** Cast and call `unsubscribe(...)`.
  - **SEND:** Call `connections.send(channel, msg)`.
  - **DISCONNECT:** Call `connections.disconnect(id)` and set `shouldTerminate = true`.

- **`shouldTerminate()`**: Return the flag.

</details>

---

## 4. Refactor: BaseServer and Reactor

<details>
<summary><b>Click to expand</b></summary>

**Files:**
- `spl3/server/src/main/java/bgu/spl/net/srv/BaseServer.java`
- `spl3/server/src/main/java/bgu/spl/net/srv/Reactor.java`

You cannot change `Server.java` (the interface/factory), so you must modify the abstract/concrete classes to inject the Connections object.

**Changes in BaseServer (TPC):**
- Add a field `ConnectionsImpl<T> connections`.
- Initialize it in the constructor or declaration.
- In the `serve()` loop, before calling `execute(handler)`:
  - Cast the protocol from the handler to `StompMessagingProtocol<T>`.
  - Call `protocol.start(connectionId, connections)`.
  - Call `connections.addConnection(connectionId, handler)`.
  - *(This ensures the protocol is ready before the thread starts)*

**Changes in Reactor:**
- Add a field `ConnectionsImpl<T> connections`.
- When a new `NonBlockingConnectionHandler` is created (in `accept()` or similar):
  - Cast protocol to `StompMessagingProtocol`.
  - Call `protocol.start(id, connections)`.
  - Call `connections.addConnection(id, handler)`.

</details>

---

## 5. Implementation: StompServer

<details>
<summary><b>Click to expand</b></summary>

**File:** `spl3/server/src/main/java/bgu/spl/net/impl/stomp/StompServer.java`

**Main Method:**
- Parse `args[1]` ("tpc" or "reactor").
- Use `Server.threadPerClient(...)` or `Server.reactor(...)`.
- Provide a `Supplier` for `StompMessagingProtocolImpl`.
- Provide a `Supplier` for `LineMessageEncoderDecoder` (since STOMP is text-based and frame-delimited by null char `\0`). 
  - Check if `LineEncoderDecoder` supports the specific STOMP delimiter or write a new `StompEncoderDecoder`.

</details>

---

## Summary of Class Relationships

<details>
<summary><b>Click to expand</b></summary>

- **StompServer** initializes BaseServer/Reactor.
- **BaseServer/Reactor** owns the `ConnectionsImpl`.
- **BaseServer/Reactor** creates `ConnectionHandler` and injects it into `ConnectionsImpl`.
- **BaseServer/Reactor** initializes `StompMessagingProtocolImpl` via `start()`.
- **StompMessagingProtocolImpl** uses `ConnectionsImpl` (via casting) to manage subscriptions and send messages.

</details>