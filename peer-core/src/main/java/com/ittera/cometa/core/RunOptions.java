package com.ittera.cometa.core;

public enum RunOptions {
  LOGLESS, // Run without log IO
  REQLESS, // Don't listen to requests over TCP
  INLOG_SAME_AS_OUTLOG; // Read and write to same log
}
