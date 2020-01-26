package net.ittera.pal.core;

public enum RunOptions {
  LOGLESS, // Run without log IO
  REQLESS, // Don't listen to requests over TCP
  NO_PUBLISHING, // Don't publish messages
  NO_INTERCEPTS, // Don't allow message interception
  INLOG_SAME_AS_OUTLOG; // Read and write to same log
}
