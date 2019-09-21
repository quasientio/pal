package com.ittera.cometa;

import java.util.Objects;
import java.util.UUID;

public class PeerInfo extends UTCTimestampedInfo implements Comparable {

	// name of node in zk
	private UUID uuid;

	// in zk node data
	private String listenAddress;

	private String name;

	public PeerInfo(UUID uuid) {
		setUuid(uuid);
	}

	public PeerInfo(UUID uuid, String name) {
		setUuid(uuid);
		setName(name);
	}

	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public String getListenAddress() {
		return listenAddress;
	}

	public void setListenAddress(String listenAddress) {
		this.listenAddress = listenAddress;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int compareTo(Object o) {
		return getUuid().compareTo(((PeerInfo) o).getUuid());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PeerInfo peerInfo = (PeerInfo) o;
		return uuid.equals(peerInfo.uuid) &&
			listenAddress.equals(peerInfo.listenAddress);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uuid, listenAddress);
	}

	@Override
	public String toString() {
		return "peer uuid: " + getUuid() + " name: " + (getName() == null ? "<undefined>" : getName())
			+ " listen-addr: " + getListenAddress();
	}
}
