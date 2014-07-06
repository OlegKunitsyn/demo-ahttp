import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerTest {

	private Thread thread;
	private static int HTTP_PORT = 8080;
	private static final int CONCURRENT_CONNECTIONS = 200;
	
	private enum TestFile {
	    GIF("/resources/tiny.gif", "ad4b0f606e0f8465bc4c4c170b37e1a3"),
	    BMP("/resources/mid.bmp", "bc6305a1d0e92ab616f8dbbd5228235f");
	
		public String name;
		public String md5;
	    
	    private TestFile(String name, String md5) {
	        this.name = name;
	        this.md5 = md5;
	    }
	}
	
	class Download implements Runnable{
		private String fileName;
		public String md5;
		public int size;
		public Exception e;
		
		public Download(String fileName) {
			this.fileName = fileName;
		}
		
		public void run() {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				URL url;
				InputStream in;
				try {
					url = new URL("http://127.0.0.1:" + HTTP_PORT + this.fileName);
					URLConnection urlConnection = url.openConnection();
					urlConnection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
					urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");
					urlConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
					urlConnection.setRequestProperty("Connection", "keep-alive");
					urlConnection.setRequestProperty("Cache-Control", "max-age=0");
					urlConnection.setRequestProperty("Cookie", "__utma=109120263.671450273.1402996334.1404630384.1404637257.31; __utmz=109120263.1404374339.26.4.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=(not%20provided); __utmc=109120263; __utmb=109120263.2.10.1404637257");
					urlConnection.setRequestProperty("Host", "127.0.0.1:8080");
					urlConnection.setRequestProperty("If-Modified-Since", "Fri, 27 Jun 2014 09:24:25 GMT");
					urlConnection.setRequestProperty("Referer", "http://example.com/bla/bla/bla.html");
					urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:30.0) Gecko/20100101 Firefox/30.0");
					in = urlConnection.getInputStream();
				} catch (ConnectException e) {
					System.err.println(e.getMessage());
					return;
				}
				int b;
				while (true) {
					b = in.read();
					if (b != -1) {
						out.write(b);
					} else {
						break;
					}
				}
				in.close();
				
				MessageDigest md;
				md = MessageDigest.getInstance("MD5");
				md.update(out.toByteArray());
				this.size = out.size();
				this.md5 = new BigInteger(1, md.digest()).toString(16);
			} catch (Exception e) {
				this.e = e;
			}
		}
	}
	
	@Before
	public void setUp() throws InterruptedException {
		this.thread = new Thread(new Server(HTTP_PORT, CONCURRENT_CONNECTIONS));
		this.thread.start();
		Thread.sleep(500);
	}
	
	@After
	public void tearDown() {
		this.thread.interrupt();
	}

	@Test
	public void testGifImage() {
		Download file = new Download(TestFile.GIF.name);
		file.run();
		assertEquals(TestFile.GIF.md5, file.md5);
	}
		
	@Test
	public void testBmpImage() {
		Download file = new Download(TestFile.BMP.name);
		file.run();
		assertEquals(TestFile.BMP.md5, file.md5);
	}
}
