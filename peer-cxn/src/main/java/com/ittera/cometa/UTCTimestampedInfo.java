package com.ittera.cometa;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class UTCTimestampedInfo {

	protected OffsetDateTime ctime;
	protected OffsetDateTime mtime;

	public void setCtime(long ctime) {
		Instant instant = Instant.ofEpochMilli(ctime);
		this.ctime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public void setMtime(long mtime) {
		Instant instant = Instant.ofEpochMilli(mtime);
		this.mtime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public OffsetDateTime getCTime() {
		return ctime;
	}

	public OffsetDateTime getMTime() {
		return mtime;
	}
}
