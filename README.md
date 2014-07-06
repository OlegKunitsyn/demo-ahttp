Demonstrates of the non-blocking HTTP server in asynchronous mode
==========

These 350 lines of Java code were inspired by Jacques Mattheij's [The Several Million Dollar Bug].
Please read the article to understand the background of the issue. 


In Java technical details are as follows. Since HTTP protocol is synchronous by design (RFC 1945) NIO keeps *OP_READ* mode of the channel even *OP_WRITE* operation set on the first byte received.
This is a standard implementation of non-blocking HTTP server you can find in Internet. 

The magic goes on line #118. Comment it out and run the test.

```
...
Requested: resources/tiny.gif
Received: 255
Received: 255
Received: 146
Transmitted: 43
...
```

In order to transmit 43 bytes we need to receive 656 bytes, huh. Now uncomment the line.

```
...
Requested: resources/tiny.gif
Received: 255
Transmitted: 43
Received: 255
...
```

As we can see Java HTTP client accepts the forced response correctly. As well as the browsers. 
In your project make the request handler smarter by cutting off GET line only.


Have a nice day and several million dollar in a wallet!

[The Several Million Dollar Bug]:http://jacquesmattheij.com/the-several-million-dollar-bug
