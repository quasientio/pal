package net.ittera.pal.messages.types;

import java.util.Locale;

public enum MessageFamily {
  CONTROL,
  EXEC,
  INTERCEPT,
  META;

  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
