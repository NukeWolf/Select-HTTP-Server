# Design

## Server Structure

## Request Handling

Relevent Files

- Server/HTTP1ReadWriteHandler.java
- Server/HTTP1ReadHandler.java

## Response Generation

Relevent Files

- Server/HTTP1ReadWriteHandler.java
- Server/HTTP1WriteHandler.java

## Write Handler

### CGI

CGI uses a Chunked Response Format. Response generation will do all necessary checks to ensure the requested resource is executable and then proceed to setup the process builder. The process builder will then start, and relevent headers will be set in the writeHandler. Any input bytes will also be written into STDIN during response generation.

The Writehandler will then send the header and respone-line as normal except it will not send the line return to end headers. The write handler will then be sent to the CGI pipeline in the write handler in the form of 3 states:

- ChunkCreation
- Sending Chunk
- Sending Chunk Termminator

The WriteHandler will ensure that chunked responses will only start after the remaining headers are sent in CGI. In chunkcreation, available bytes from the process are read and will determine whether to send a chunk, send a terminator, or do nothing depending on bytes available in the stream, and if the process is still alive or not.

Sending a chunk involves encoding the next length of bytes in hexadecimal and then sending out the rest of the data to the client.
