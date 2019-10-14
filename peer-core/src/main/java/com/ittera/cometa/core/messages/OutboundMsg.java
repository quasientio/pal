package com.ittera.cometa.core.messages;

import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.common.util.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;

import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Stream;

public class OutboundMsg extends BaseMsg {

	/**
	 * FRAMES:
	 * -------
	 * 1. type of message    : int (MessageType)
	 * 2. headers to follow  : int
	 * 3. [headers]          : byte[]* (InternalHeader)
	 * 4. message uuid       : byte[]
	 * 5. followingUuid      : byte[]
	 * 6. message body       : byte[]
	 */

	// fields
	private MessageType messageType;
	private List<Wrappers.InternalHeader> headers = new ArrayList<>();
	private UUID messageUuid;
	@Nullable
	private UUID followingUuid;
	private byte[] body;

	private OutboundMsg() {
		zmsg = new ZMsg();
	}

	public OutboundMsg(MessageType messageType, @Nullable List<Wrappers.InternalHeader> headers, UUID messageUuid,
										 @Nullable UUID followingUuid, byte[] body) {
		this();
		Stream.of(messageType, messageUuid, body).forEach(Objects::requireNonNull);
		this.messageType = messageType;
		if (headers != null && !headers.isEmpty()) {
			this.headers = headers;
		}
		this.messageUuid = messageUuid;
		this.followingUuid = followingUuid;
		this.body = body;
		build();
	}

	@Override
	protected final void build() {
		// 1. type of message
		zmsg.add(Ints.toByteArray(messageType.ordinal()));

		// 2. headers to follow
		if (headers.isEmpty()) {
			zmsg.add(Ints.toByteArray(0));
		} else {
			zmsg.add(Ints.toByteArray(headers.size()));
		}

		// 3. headers
		for (Wrappers.InternalHeader header : headers) {
			zmsg.add(header.toByteArray());
		}

		// 4. message uuid
		zmsg.add(UUIDUtils.toBytes(messageUuid));

		// 5. followingUuid
		if (followingUuid != null) {
			zmsg.add(UUIDUtils.toBytes(followingUuid));
		} else {
			zmsg.add(Ints.toByteArray(0));
		}

		// 6. message body
		zmsg.add(body);
	}

	public static OutboundMsg from(ZMsg zMsg) throws InvalidProtocolBufferException {
		// duplicate ZMsg contents
		OutboundMsg msg = new OutboundMsg();

		// set fields
		Iterator<ZFrame> it = zMsg.iterator();
		byte[] data = it.next().getData();
		msg.messageType = MessageType.values[Ints.fromByteArray(data)];
		int headerCount = Ints.fromByteArray(it.next().getData());
		if (headerCount > 0) {
			for (int i = 0; i < headerCount; i++) {
				msg.headers.add(Wrappers.InternalHeader.parseFrom(it.next().getData()));
			}
		}
		msg.messageUuid = UUIDUtils.fromBytes(it.next().getData());
		byte[] followingUuidData = it.next().getData();
		if (Ints.fromByteArray(followingUuidData) != 0) {
			msg.followingUuid = UUIDUtils.fromBytes(followingUuidData);
		}
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
		OutboundMsg that = (OutboundMsg) o;
		return messageType == that.messageType &&
			headers.equals(that.headers) &&
			messageUuid.equals(that.messageUuid) &&
			Objects.equals(followingUuid, that.followingUuid) &&
			Arrays.equals(body, that.body);
	}

	/**
	 * BEWARE hashCode() does not take zmsg object into account
	 */
	@Override
	public int hashCode() {
		int result = Objects.hash(messageType, headers, messageUuid, followingUuid);
		result = 31 * result + Arrays.hashCode(body);
		return result;
	}

	@Override
	public String toString() {
		return "OutboundMsg{" +
			"messageType=" + messageType +
			", headers=" + headers +
			", messageUuid=" + messageUuid +
			", followingUuid=" + followingUuid +
			", body=" + Arrays.toString(body) +
			'}';
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public List<Wrappers.InternalHeader> getHeaders() {
		return headers;
	}

	public UUID getMessageUuid() {
		return messageUuid;
	}

	@Nullable
	public UUID getFollowingUuid() {
		return followingUuid;
	}

	public byte[] getBody() {
		return body;
	}
}
