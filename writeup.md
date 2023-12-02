## Netty is Java async IO framework used by many; see for example use cases. Please read Netty user's guide and code and answer the following questions:

### Netty provides multiple event loop implementations. In a typical server channel setting, two event loop groups are created, with one typically called the boss group and the second worker group. What are they? How does Netty achieve synchronization among them?

The two groups serve two different roles. The `Boss` group is responsible for accepting incoming connections, and the `Worker` group handles request processing, includig i/o. Netty achieves synchronization by using a task queue. New Tasks are assigned to an `EventLoopGroup` and added to the task queue. When a calling thread executes a task, it checks if it is part of `EventLoopGroup` to which the task is assigned. If it is not, the thread adds the task back to the end of the queue.

### Netty event loop uses an ioRatio based scheduling algorithm. Please describe how it works.

In Netty, `ioRatio` represents the fraction of time spent performing IO tasks compared to non-io tasks. Netty always processes all of its I/O tasks first before processing non-io tasks. 

1. If IO `ioRatio` is 100, after Netty processes its io tasks, it handles all of its queued non-io tasks

2. Otherwise, if Netty spends $t$ handling io tasks, it will then spend up to $t\cdot \frac{(100 - ioRatio)}{ioRatio}$ handling non IO tasks.


###  A major novel, interesting feature of Netty is ChannelPipeline. A pipeline may consist of a list of ChannelHander. Please scan Netty implementation and give a high-level description of how ChannelPipeline is implemented. Compare HTTP Hello World Server and HTTP Snoop Server, what are the handlers that each includes?

In netty, a `ChannelPipeline` is an interface for an ordered sequeunce of handlers which process the events which may occurr on a specific channel. A Pipeline begins with a I/O request. Then, events on the channel are passed through the pipeline and writes / reads are performed. 

1. There is one handler in the `Hello World` server -- `HttpHelloWorldServerHandler`. The handler simply sends a OK response along with the message `Hello World`

2. There is 1 handler in the `Snoop` server -- `HttpSnoopServerHandler`. This handler sends a message with the contents of an HTTP request to the server.

### Method calls such as bind return ChannelFuture. Please describe how one may implement the sync method of a future.

A `Future` represents the result of an asynchronous operation. One way to impelemnt the `sync` method of a future object is to just return the object. This makes sense in the context of `bind`, as when the bind operation completes, we should have a channel object to work with.

###  Instead of using ByteBuffer, Netty introduces a data structure called ByteBuf. Please give one key difference between ByteBuffer and ByteBuf.

The key difference between `ByteBuffer` and `ByteBuf` is that `ByteBuf` maintains two separate indices. One for reading and one for writing.



## nginx is currently the most-widely used HTTP server. Please read nginx source code, and developer guide to answer the following questions. You can use the developer guide but need to add reference to the source code.

### Although nginx has both Master and Worker, the design is the symmetric design that we covered in class: multiple Workers compete on the shared welcome socket (accept). One issue about the design we said in class is that this design does not offer flexible control such as load balance. Please describe how nginx introduces mechanisms to allow certain load balancing among workers? Related with the shared accept, one issue is that when a new connection becomes acceptable, multiple workers can be notified, creating contention. Please read nginx event loop and describe how nginx tries to resolve the contention.


### The nginx event loop processes both io events and timers. If it were nginx, how would you implement the 3-second timeout requirement of this project?

To implement the 3-second timeout requirement, I would add a timer event with an event handler that closes the connection. This is because the nginx event loop begins its processing by finding the timer which is closest to expiring.

### nginx processes HTTP in 11 phases. What are the phases? Please list the checker functions of each phase.

The phases and their checker functions are
1. `NGX_HTTP_POST_READ_PHASE`
    1. ngx_http_core_generic_phase

2. `NGX_HTTP_SERVER_REWRITE_PHASE`
    1. ngx_http_core_rewrite_phase

3. `NGX_HTTP_FIND_CONFIG_PHASE`
    1. ngx_http_core_find_config_phase

4. `NGX_HTTP_REWRITE_PHASE `
    1. ngx_http_core_rewrite_phase

5. `NGX_HTTP_POST_REWRITE_PHASE`
    1. ngx_http_core_post_rewrite_phase

6. `NGX_HTTP_PREACCESS_PHASE`
    1. ngx_http_core_generic_phase

7. `NGX_HTTP_ACCESS_PHASE`
    1. ngx_http_core_access_phase

8. `NGX_HTTP_POST_ACCESS_PHASE`
    1. ngx_http_core_access_phase

9. `NGX_HTTP_PRECONTENT_PHASE`
    1. ngx_http_core_access_phase

10. `NGX_HTTP_CONTENT_PHASE`
    1. ngx_http_core_content_phase

11. `NGX_HTTP_LOG_PHASE`
    1. no checker

### A main design feature of nginx is efficient support of upstream; that is, forward request to an upstream server. Can you describe the high level design?

To forward requests to an upstream server, nginx first uses an upstream configuration file. This file may contain a list of servers which nginx can forward requests to as well as load balancing algorithm. 

### nginx introduces a buffer type ngx_buf_t. Please briefly compare ngx_buf_t vs ByteBuffer we covered for Java nio?

The nginx buffer and ByteBuffer are very similar. They both provide a buffer type for reading data received across or network or from a file. They both contain pointers to where their data starts. However, the Java ByteBuffer is slightly more generic. Specifically, it contains functions to view data as primitive types which are not bytes whereas the nginx buffer is limited to reading everything as bytes.