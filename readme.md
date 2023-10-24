# Design

## Server Structure

Relevant Files:
- server/Dispatcher.java
- server/Worker.java
- server/UnusedChannelMonitor.java
- server/Server.java

The server is multithreaded following a Master-Worker design. The main execution thread in Server.java reads the configuration files and starts the dispatcher thread, which serves as the master thread.

The Dispatcher thread is responsible for managing the various types of helper threads, shared data structures, and accepting new connections. There are two main shared data structures: a hashmap for accepted but responded channels (UnusedChannelTable) and a queue of newly accepted channels (clientSocketQueue). Each of these is updated every time the server receives a new connection.

There are two types of helper threads (so far):
1. UnusedChannelMonitor -- the unused channel monitor keeps track of and shuts down channels which do not receive a valid request before timeout. It does so by iterating over the members of the UnusedChannelTable and closing channels which have been in the table for longer than the timeout time.
   
2. Worker -- the workers respond to events on the channels by parsing requests and writing messages. The workers accept new channels from the queue, and use a selector-loop to process new events.

## Request Handling

Relevant Files

- server/HTTP1ReadWriteHandler.java
- server/HTTP1ReadHandler.java

## Response Generation

Relevant Files

- server/HTTP1ReadWriteHandler.java
- server/HTTP1WriteHandler.java

## Write Handler

### CGI

CGI uses a Chunked Response Format. Response generation will do all necessary checks to ensure the requested resource is executable and then proceed to setup the process builder. The process builder will then start, and relevent headers will be set in the writeHandler. Any input bytes will also be written into STDIN during response generation.

The Writehandler will then send the header and respone-line as normal except it will not send the line return to end headers. The write handler will then be sent to the CGI pipeline in the write handler in the form of 3 states:

- ChunkCreation
- Sending Chunk
- Sending Chunk Termminator

The WriteHandler will ensure that chunked responses will only start after the remaining headers are sent in CGI. In chunkcreation, available bytes from the process are read and will determine whether to send a chunk, send a terminator, or do nothing depending on bytes available in the stream, and if the process is still alive or not.

Sending a chunk involves encoding the next length of bytes in hexadecimal and then sending out the rest of the data to the client.
