package tlschannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import java.nio.channels.ByteChannel;
import javax.net.ssl.SSLSession;

import java.util.Optional;
import java.util.function.Consumer;

public class TlsClientSocketChannel implements TlsSocketChannel {

	public static class Builder {
		
		private final ByteChannel wrapped;
		private final SSLEngine engine;
		private Consumer<SSLSession> sessionInitCallback = session -> {};
		
		public Builder(ByteChannel wrapped, SSLEngine engine) {
			this.wrapped = wrapped;
			this.engine = engine;
		}
		
		public Builder withSessionInitCallback(Consumer<SSLSession> sessionInitCallback) {
			this.sessionInitCallback = sessionInitCallback;
			return this;
		}
		
		public TlsClientSocketChannel build() {
			return new TlsClientSocketChannel(wrapped, engine, sessionInitCallback);
		}
	}
	
	private final ByteChannel wrapped;
	private final SSLEngine engine;
	private final TlsSocketChannelImpl impl;

	private TlsClientSocketChannel(ByteChannel wrapped, SSLEngine engine, Consumer<SSLSession> sessionInitCallback) {
		if (!engine.getUseClientMode())
			throw new IllegalArgumentException("SSLEngine must be in client mode");
		this.wrapped = wrapped;
		this.engine = engine;
		impl = new TlsSocketChannelImpl(wrapped, wrapped, engine, Optional.empty(), sessionInitCallback);
	}

	@Override
	public SSLSession getSession() {
		return engine.getSession();
	}

	@Override
	public ByteChannel getWrapped() {
		return wrapped;
	}

	@Override
	public long read(ByteBuffer[] dstBuffers, int offset, int length) throws IOException {
		TlsSocketChannelImpl.checkReadBuffer(dstBuffers, offset, length);
		return impl.read(dstBuffers, offset, length);
	}

	@Override
	public long read(ByteBuffer[] dstBuffers) throws IOException {
		return read(dstBuffers, 0, dstBuffers.length);
	}

	@Override
	public int read(ByteBuffer dstBuffer) throws IOException {
		return (int) read(new ByteBuffer[] { dstBuffer });
	}
	
	@Override
	public long write(ByteBuffer[] srcBuffers, int offset, int length) throws IOException {
		TlsSocketChannelImpl.checkWriteBuffer(srcBuffers, offset, length);
		return impl.write(srcBuffers, offset, length);
	}
	
	@Override
	public long write(ByteBuffer[] outs) throws IOException {
		return write(outs, 0, outs.length);
	}

	@Override
	public int write(ByteBuffer srcBuffer) throws IOException {
		return (int) write(new ByteBuffer[] { srcBuffer });
	}

	@Override
	public void renegotiate() throws IOException {
		impl.renegotiate();
	}

	@Override
	public void doPassiveHandshake() throws IOException {
		impl.doPassiveHandshake();
	}

	@Override
	public void doHandshake() throws IOException {
		impl.doHandshake();
	}

	@Override
	public void close() {
		impl.close();
	}

	@Override
	public boolean isOpen() {
		return impl.isOpen();
	}


}