package tlschannel

import org.scalatest.FunSuite
import org.scalatest.Matchers
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.handler.ssl.SslContextBuilder
import io.netty.buffer.ByteBufAllocator
import io.netty.handler.ssl.OpenSslSessionContext
import io.netty.handler.ssl.OpenSslApplicationProtocolNegotiator
import io.netty.handler.ssl.OpenSslEngineMap
import io.netty.handler.ssl.OpenSslEngine
import io.netty.handler.ssl.OpenSslContext
import io.netty.handler.ssl.CipherSuiteFilter
import io.netty.handler.ssl.OpenSslServerContext
import java.io.File
import io.netty.buffer.PooledByteBufAllocator
import java.nio.channels.ServerSocketChannel
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel
import java.net.InetAddress
import io.netty.handler.ssl.OpenSslClientContext
import javax.net.ssl.SSLContext
import java.nio.channels.ByteChannel
import TestUtil.StreamWithTakeWhileInclusive
import java.nio.ByteBuffer
import scala.util.Random
import TestUtil.functionToRunnable
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.Slf4JLoggerFactory

class OpenSslEngineTest extends FunSuite with Matchers with StrictLogging {

  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory)
  
  //  val constructor = classOf[OpenSslContext].getDeclaredConstructor(
  //    classOf[Iterable[String]],
  //    classOf[CipherSuiteFilter],
  //    classOf[OpenSslApplicationProtocolNegotiator],
  //    classOf[Long],
  //    classOf[Long],
  //    classOf[Int])
  //  constructor.setAccessible(true)
  //  val foo = constructor.newInstance(
  //    Seq(""),
  //    null,
  //    null,
  //    Long.box(0),
  //    Long.box(0),
  //    Long.box(0));

  val certFile = new File(getClass.getClassLoader.getResource("openssl/rsa.cert.pem").toURI())
  val keyFile = new File(getClass.getClassLoader.getResource("openssl/rsa.key.pkcs8.pem").toURI())
  
  val serverCtx = new OpenSslServerContext(certFile, keyFile, null)
  val serverEngine = serverCtx.newEngine(new PooledByteBufAllocator)

  val clientCtx = new OpenSslClientContext(certFile, InsecureTrustManagerFactory.INSTANCE)
  

  val localhost = InetAddress.getByName(null)
  val (cipher, sslContext) = SslContextFactory.standardCipher

  def nioNio(cipher: String): ((TlsClientSocketChannel, SocketChannel), (TlsServerSocketChannel, SocketChannel)) = {
    val serverSocket = ServerSocketChannel.open()
    //serverSocket.bind(new InetSocketAddress(localhost, 0 /* find free port */ ))
    serverSocket.bind(new InetSocketAddress(localhost, 7777 /* find free port */ ))
    val chosenPort = serverSocket.getLocalAddress.asInstanceOf[InetSocketAddress].getPort
    
    
    val clientEngine = clientCtx.newEngine(new PooledByteBufAllocator, "name", chosenPort)
    
    val address = new InetSocketAddress(localhost, chosenPort)
    val rawClient = SocketChannel.open(address)
    val rawServer = serverSocket.accept()
    serverSocket.close()
    val clientChannel = new TlsClientSocketChannel(rawClient, clientEngine)
    val serverChannel = new TlsServerSocketChannel(rawServer, null.asInstanceOf[SSLContext], serverEngine)
    ((clientChannel, rawClient), (serverChannel, rawServer))
  }

  val dataSize = TlsSocketChannelImpl.tlsMaxDataSize * 3

  val data = Array.ofDim[Byte](dataSize)
  Random.nextBytes(data)

  /**
   * Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
   */
  test("half duplex (with renegotiations)") {
    val (cipher, sslContext) = SslContextFactory.standardCipher
    val ((client, clientChannel), (server, serverChannel)) = nioNio(cipher)
    val (_, elapsed) = TestUtil.time {
      val clientWriterThread = new Thread(() => BlockingTest.writerLoop(data, client, client, renegotiate = false), "client-writer")
      val serverWriterThread = new Thread(() => BlockingTest.writerLoop(data, server, server, renegotiate = false), "server-writer")
      val clientReaderThread = new Thread(() => BlockingTest.readerLoop(data, client), "client-reader")
      val serverReaderThread = new Thread(() => BlockingTest.readerLoop(data, server), "server-reader")
      Seq(serverReaderThread, clientWriterThread).foreach(_.start())
      Seq(serverReaderThread, clientWriterThread).foreach(_.join())
      clientReaderThread.start()
      // renegotiate three times, to test idempotency
      for (i <- 1 to 3) {
        logger.debug(s"Renegotiating: $i of 3")
        //server.renegotiate()
      }
      serverWriterThread.start()
      Seq(clientReaderThread, serverWriterThread).foreach(_.join())
      server.close()
      client.close()
    }
    info(f"${elapsed / 1000}%5d ms")
  }

  /**
   * Test a full-duplex interaction, without any renegotiation
   */
//  test("full duplex") {
//    val ((client, clientChannel), (server, serverChannel)) = nioNio(cipher)
//    val (_, elapsed) = TestUtil.time {
//      val clientWriterThread = new Thread(() => BlockingTest.writerLoop(data, client, client), "client-writer")
//      val serverWriterThread = new Thread(() => BlockingTest.writerLoop(data, server, server), "server-write")
//      val clientReaderThread = new Thread(() => BlockingTest.readerLoop(data, client), "client-reader")
//      val serverReaderThread = new Thread(() => BlockingTest.readerLoop(data, server), "server-reader")
//      Seq(serverReaderThread, clientWriterThread, clientReaderThread, serverWriterThread).foreach(_.start())
//      Seq(serverReaderThread, clientWriterThread, clientReaderThread, serverWriterThread).foreach(_.join())
//      client.close()
//      server.close()
//    }
//    info(f"${elapsed / 1000}%5d ms")
//  }

}