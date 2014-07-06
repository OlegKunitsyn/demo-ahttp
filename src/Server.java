import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.StringTokenizer;

public class Server implements Runnable {
	private static final String HTTP_SERVER = "async-server";
	private static final String HTTP_FOUND = "HTTP/1.1 200 OK";
	private static final String HTTP_NOT_FOUND = "HTTP/1.1 404 Not Found";
	private static final String HTTP_BAD_REQUEST = "HTTP/1.1 400 Bad Request";
	private static final int INPUT_BUFFER_SIZE = 255;
	private static final int OUTPUT_BUFFER_SIZE = 20480;

	private static class Attachment {
		RandomAccessFile file;
		FileChannel fileChannel;
		ByteBuffer httpResponse;
	}

	private Selector selector;
	private ServerSocketChannel server;
	private ByteBuffer httpRequest = ByteBuffer.allocate(INPUT_BUFFER_SIZE);

	/**
	 * Initialize HTTP server
	 * 
	 * @param httpPort
	 * @param concurrentConnections
	 */
	public Server(int httpPort, int concurrentConnections) {
		try {
			this.selector = SelectorProvider.provider().openSelector();
			this.server = ServerSocketChannel.open();
			this.server.configureBlocking(false);
			this.server.socket().bind(new InetSocketAddress(httpPort), concurrentConnections);
			this.server.register(this.selector, server.validOps());
			System.out.println("Listening on *:" + httpPort);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Accepted
	 * 
	 * @param key
	 * @throws IOException
	 */
	private void accept(SelectionKey key) throws IOException {
		SocketChannel newChannel = ((ServerSocketChannel) key.channel()).accept();
		newChannel.configureBlocking(false);
		newChannel.register(key.selector(), SelectionKey.OP_READ);
	}

	/**
	 * Read httpRequest
	 * 
	 * @param key
	 * @throws IOException
	 */
	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		
		// receiving
		this.httpRequest.clear();
		int bytes = channel.read(this.httpRequest) ;
		if (bytes < 1) {
			close(key);
			return;
		}
		
		// new request
		Attachment attachment = ((Attachment) key.attachment());
		if (attachment == null) {
			key.attach(attachment = new Attachment());
		}
		if (attachment.httpResponse == null) {
			StringTokenizer tokenizer = new StringTokenizer(new String(this.httpRequest.array(), "UTF-8"));
			String httpMethod = tokenizer.nextToken().toUpperCase();
			String fileName = tokenizer.nextToken().substring(1);
			System.out.println("Requested: " + fileName);
			
			attachment.httpResponse = ByteBuffer.allocate(OUTPUT_BUFFER_SIZE);
			if (httpMethod.equals("GET")) {
				try {
					attachment.file = new RandomAccessFile(fileName, "r");
					attachment.fileChannel = attachment.file.getChannel();
					writeHeader(key, HTTP_FOUND);
					writeHeader(key, "Content-Type: binary");
					writeHeader(key, "Content-Length: " + attachment.file.length());
					writeHeader(key, "Cache-Control: must-revalidate, post-check=0, pre-check=0");
					writeHeader(key, "Expires: 0");
					writeHeader(key, "Pragma: public");
				} catch (FileNotFoundException ex) {
					writeHeader(key, HTTP_NOT_FOUND);
				}
			} else {
				writeHeader(key, HTTP_BAD_REQUEST);
			}
			writeHeader(key, "Server: " + HTTP_SERVER);
			writeHeader(key, "Connection: close");
			writeHeader(key, "");
			attachment.httpResponse.flip();
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
		}
		System.out.println("Received: " + bytes);
		
		// force async response
		write(key);
	}

	/**
	 * Connected
	 * 
	 * @param key
	 * @throws IOException
	 */
	private void connect(SelectionKey key) throws IOException {
		key.interestOps(0);
		SocketChannel channel = ((SocketChannel) key.channel());
		channel.finishConnect();
	}

	/**
	 * Write header
	 * 
	 * @param key
	 * @param line
	 * @throws IOException
	 */
	private void writeHeader(SelectionKey key, String line) throws IOException {
		Attachment attachment = ((Attachment) key.attachment());
		attachment.httpResponse.put(ByteBuffer.wrap((line + "\r\n").getBytes()));
	}

	/**
	 * Write response
	 * 
	 * @param key
	 * @throws IOException
	 */
	private void write(SelectionKey key) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		Attachment attachment = ((Attachment) key.attachment());
		
		// transmitting
		channel.write(attachment.httpResponse);
		if (attachment.httpResponse.remaining()  > 0) {
			return;
		}
		
		// take new chunk
		if (attachment.file != null) {
			attachment.httpResponse.clear();
			int bytes = attachment.fileChannel.read(attachment.httpResponse);
			attachment.httpResponse.flip();
			if (bytes > 0) {
				System.out.println("Transmitted: " + bytes);
			} else {
				attachment.fileChannel.close();
				attachment.file.close();
				attachment.fileChannel = null;
				attachment.file = null;
				close(key);
			}
		} else {
			close(key);
		}
	}

	/**
	 * Close connection
	 * 
	 * @param key
	 * @throws IOException
	 */
	private void close(SelectionKey key) throws IOException {
		key.cancel();
		key.channel().close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				this.selector.select();
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while (it.hasNext()) {
					SelectionKey key = it.next();
					it.remove();
					if (key.isValid()) {
						try {
							if (key.isAcceptable()) {
								accept(key);
							} else if (key.isConnectable()) {
								connect(key);
							} else if (key.isReadable()) {
								read(key);
							} else if (key.isWritable()) {
								write(key);
							}
						} catch (IOException e) {
							// e.printStackTrace();
							close(key);
						}
					}
				}
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			this.server.close();
			this.selector.close();
		} catch (IOException e) {}

		System.out.println("Server stopped");
	}
}
