package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

public class JsonRpcErrorData {

  public JsonRpcErrorData() {}

  @SerializedName("throwable_type")
  @Nullable
  private String throwableType;

  private String message;

  @SerializedName("stack_trace")
  @Nullable
  private String[] stackTrace;

  @Nullable private JsonRpcErrorData cause;

  @Nullable private String requestId;

  private Executable from;

  @Nullable
  public JsonRpcErrorData getCause() {
    return cause;
  }

  public void setCause(@Nullable JsonRpcErrorData cause) {
    this.cause = cause;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String[] getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(@Nullable String[] stackTrace) {
    this.stackTrace = stackTrace;
  }

  @Nullable
  public String getThrowableType() {
    return throwableType;
  }

  public void setThrowableType(@Nullable String throwableType) {
    this.throwableType = throwableType;
  }

  @Nullable
  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(@Nullable String requestId) {
    this.requestId = requestId;
  }

  public Executable getFrom() {
    return from;
  }

  public void setFrom(Executable from) {
    this.from = from;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcErrorData that)) {
      return false;
    }
    return Objects.equals(throwableType, that.throwableType)
        && Objects.equals(message, that.message)
        && Objects.equals(requestId, that.requestId)
        && Objects.deepEquals(stackTrace, that.stackTrace)
        && Objects.equals(cause, that.cause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(throwableType, message, requestId, Arrays.hashCode(stackTrace), cause);
  }

  @Override
  public String toString() {
    return "JsonRpcErrorData{"
        + "cause="
        + cause
        + ", throwableType='"
        + throwableType
        + '\''
        + ", message='"
        + message
        + '\''
        + ", from="
        + from
        + ", requestId="
        + requestId
        + ", stackTrace="
        + Arrays.deepToString(stackTrace)
        + '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final JsonRpcErrorData errorData = new JsonRpcErrorData();

    public Builder withThrowableType(String throwableType) {
      errorData.setThrowableType(throwableType);
      return this;
    }

    public Builder withMessage(String message) {
      errorData.setMessage(message);
      return this;
    }

    public Builder withFrom(Executable from) {
      errorData.setFrom(from);
      return this;
    }

    public Builder withRequestId(String requestId) {
      errorData.setRequestId(requestId);
      return this;
    }

    public Builder withStackTrace(String[] stackTrace) {
      errorData.setStackTrace(stackTrace);
      return this;
    }

    public Builder withCause(@Nullable JsonRpcErrorData cause) {
      errorData.setCause(cause);
      return this;
    }

    public JsonRpcErrorData build() {
      return errorData;
    }
  }
}
