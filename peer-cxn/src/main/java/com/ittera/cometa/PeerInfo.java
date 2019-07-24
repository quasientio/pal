package com.ittera.cometa;

import java.util.Objects;
import java.util.UUID;

public class PeerInfo implements Comparable {

	// name of node in zk
	private UUID uuid;

	// in zk node stat
	private long zk_ctime;

	// in zk node data
	private String listenAddress;

	public PeerInfo(UUID uuid) {
		setUuid(uuid);
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

	public void setZk_ctime(long ctime) {
		this.zk_ctime = ctime;
	}

	public long getZk_ctime() {
		return zk_ctime;
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
		return "peer uuid: " + getUuid() + " listen-addr: " + getListenAddress();
	}
}
