package com.ittera.cometa.common.lang.intercept;

public enum InterceptType {
  BEFORE,
  AFTER,
  AROUND,
  BEFORE_ASYNC,
  AFTER_ASYNC;

  public static final InterceptType[] values = values();
}
