package com.ittera.cometa.core.messages;

import com.ittera.cometa.messages.MessageType;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

public class InboundLogMsg extends BaseMsg {
	/**
	 * FRAMES:
	 * -------
	 * 0 [empty REQ envelope]: ""  NOTE: ONLY ON SEND (i.e. build())
	 * 1. type of message    : int (MessageType)
	 * 2. offset             : long
	 * 3. message body       : byte[]
	 */

	private static final int ACTUAL_FRAMES = 3;

	// fields
	private MessageType messageType;
	private long offset;
	private byte[] body;


	private InboundLogMsg() {
		zmsg = new ZMsg();
	}

	public InboundLogMsg(MessageType messageType, long offset, byte[] body) {
		this();
		Stream.of(messageType, offset, body).forEach(Objects::requireNonNull);
		this.messageType = messageType;
		this.offset = offset;
		this.body = body;
		build();
	}

	@Override
	protected final void build() {
		// for safety
		if (!zmsg.isEmpty()) {
			zmsg.destroy();
		}
		// 0. emulate empty REQ envelope since this message is sent directly by a DEALER
		zmsg.add(new ZFrame(""));

		// 1. type of message
		zmsg.add(Ints.toByteArray(messageType.ordinal()));

		// 2. message offset
		zmsg.add(Longs.toByteArray(offset));

		// 3. message body
		zmsg.add(body);
	}

	public static InboundLogMsg from(ZMsg zMsg) {
		InboundLogMsg msg = new InboundLogMsg();
		assert zMsg.size() <= ACTUAL_FRAMES + 1;
		// set fields
		Iterator<ZFrame> it = zMsg.iterator();
		// if from() is called not having sent the message, the empty REQ envelope must be discarded
		if (zMsg.size() > ACTUAL_FRAMES) {
			it.next();
		}
		byte[] data = it.next().getData();
		msg.messageType = MessageType.values[Ints.fromByteArray(data)];
		msg.offset = Longs.fromByteArray(it.next().getData());
		msg.body = it.next().getData();
		msg.build();
		return msg;
	}

	/**
	 * BEWARE equals() does not take zmsg object into account
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		InboundLogMsg that = (InboundLogMsg) o;
		return offset == that.offset &&
			messageType == that.messageType &&
			Arrays.equals(body, that.body);
	}

	/**
	 * BEWARE hashCode() does not take zmsg object into account
	 */
	@Override
	public int hashCode() {
		int result = Objects.hash(messageType, offset);
		result = 31 * result + Arrays.hashCode(body);
		return result;
	}

	@Override
	public String toString() {
		return "InboundLogMsg{" +
			"messageType=" + messageType +
			", offset=" + offset +
			", body=" + Arrays.toString(body) +
			'}';
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public long getOffset() {
		return offset;
	}

	public byte[] getBody() {
		return body;
	}
}
