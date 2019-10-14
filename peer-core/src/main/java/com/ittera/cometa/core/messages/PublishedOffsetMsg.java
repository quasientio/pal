package com.ittera.cometa.core.messages;

import com.ittera.cometa.common.util.UUIDUtils;

import com.google.common.primitives.Longs;

import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public class PublishedOffsetMsg extends BaseMsg {
	/**
	 * FRAMES:
	 * -------
	 * 1. offset             : long
	 * 2. message uuid       : byte[]
	 */

	// fields
	private long offset;
	private UUID messageUuid;

	private PublishedOffsetMsg() {
		zmsg = new ZMsg();
	}

	public PublishedOffsetMsg(long offset, UUID messageUuid) {
		this();
		Stream.of(offset, messageUuid).forEach(Objects::requireNonNull);
		this.offset = offset;
		this.messageUuid = messageUuid;
		build();
	}

	@Override
	protected final void build() {
		// 1. message offset
		zmsg.add(Longs.toByteArray(offset));
		// 2. message uuid
		zmsg.add(UUIDUtils.toBytes(messageUuid));
	}

	public static PublishedOffsetMsg from(ZMsg zMsg) {
		PublishedOffsetMsg msg = new PublishedOffsetMsg();
		// set fields
		Iterator<ZFrame> it = zMsg.iterator();
		msg.offset = Longs.fromByteArray(it.next().getData());
		msg.messageUuid = UUIDUtils.fromBytes(it.next().getData());
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
		PublishedOffsetMsg that = (PublishedOffsetMsg) o;
		return offset == that.offset &&
			messageUuid.equals(that.messageUuid);
	}

	/**
	 * BEWARE hashCode() does not take zmsg object into account
	 */
	@Override
	public int hashCode() {
		return Objects.hash(offset, messageUuid);
	}

	@Override
	public String toString() {
		return "PublishedOffsetMsg{" +
			"offset=" + offset +
			", messageUuid=" + messageUuid +
			'}';
	}

	public long getOffset() {
		return offset;
	}

	public UUID getMessageUuid() {
		return messageUuid;
	}
}
