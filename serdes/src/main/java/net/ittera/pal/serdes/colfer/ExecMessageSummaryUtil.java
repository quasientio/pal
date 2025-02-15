package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Field;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.RpcMessageSummaryUtil;

/**
 * Utility class for generating concise one-line summaries of {@link ExecMessage} instances.
 *
 * <p>This class provides functionality to interpret different types of execution messages and
 * produce a standardized summary string suitable for logging or display purposes.
 */
public class ExecMessageSummaryUtil extends RpcMessageSummaryUtil {

  /** Maximum allowed length for summary strings. */
  private static final short SUMMARY_MAX_LENGTH = 100;

  /**
   * Generates a string representation of the given object and its reference.
   *
   * @param obj the object to represent, may be {@code null}.
   * @param objectRef the reference identifier of the object, may be {@code null}.
   * @return a string representation based on the object's state and reference.
   */
  private static String getObjRepr(Obj obj, String objectRef) {
    return getObjRepr(
        obj == null ? null : obj.isNull,
        obj == null ? null : obj.value == null ? null : obj.value,
        objectRef);
  }

  /**
   * Generates a string representation of the given object using its own reference.
   *
   * @param obj the object to represent, may be {@code null}.
   * @return a string representation based on the object's state and internal reference.
   */
  private static String getObjRepr(Obj obj) {
    return getObjRepr(obj.isNull, obj.value, obj.ref);
  }

  /**
   * Retrieves the short class name based on the message type of the given execution message.
   *
   * @param msg the execution message from which to extract the class name.
   * @return the short class name associated with the message type.
   */
  private static String classname(ExecMessage msg) {
    return shortClassname(getClassname(msg));
  }

  /**
   * Generates a one-line summary for the specified execution message.
   *
   * <p>The summary provides a concise description of the message's action, such as method calls,
   * field accesses, or returned values.
   *
   * @param msg the execution message to summarize.
   * @return a one-line summary string representing the execution message.
   */
  public static String getOneLinerSummary(ExecMessage msg) {
    final MessageType execMessageType = getMessageTypeOf(msg);
    final String summary;
    switch (execMessageType) {
      case EXEC_CONSTRUCTOR:
        summary = "new " + classname(msg);
        break;
      case EXEC_INSTANCE_METHOD:
        summary =
            String.format(
                "call %s.%s@%s",
                classname(msg), msg.instanceMethodCall.name, msg.instanceMethodCall.objectRef);
        break;
      case EXEC_CLASS_METHOD:
        summary = String.format("call %s.%s", classname(msg), msg.classMethodCall.name);
        break;
      case EXEC_GET_STATIC:
        summary = String.format("get %s.%s", classname(msg), msg.staticFieldGet.field.name);
        break;
      case EXEC_GET_FIELD:
        summary =
            String.format(
                "get %s.%s@%s",
                classname(msg), msg.instanceFieldGet.field.name, msg.instanceFieldGet.objectRef);
        break;
      case EXEC_PUT_STATIC:
        summary =
            String.format(
                "put %s.%s ⇦ %s",
                classname(msg),
                msg.staticFieldPut.field.name,
                getObjRepr(msg.staticFieldPut.valueObject, msg.staticFieldPut.valueObjectRef));
        break;
      case EXEC_PUT_FIELD:
        summary =
            String.format(
                "put %s.%s@%s ⇦ %s",
                classname(msg),
                msg.instanceFieldPut.field.name,
                msg.instanceFieldPut.objectRef,
                getObjRepr(msg.instanceFieldPut.valueObject, msg.instanceFieldPut.valueObjectRef));
        break;
      case EXEC_PUT_STATIC_DONE:
        summary =
            String.format("put_done %s.%s", classname(msg), msg.staticFieldPutDone.field.name);
        break;
      case EXEC_PUT_FIELD_DONE:
        summary =
            String.format("put_done %s.%s", classname(msg), msg.instanceFieldPutDone.field.name);
        break;
      case EXEC_THROWABLE:
        summary =
            String.format(
                "throw %s: \"%s\"", classname(msg), msg.raisedThrowable.throwable.message);
        break;
      case EXEC_RETURN_VALUE:
        if (msg.returnValue.isVoid) {
          summary = "return void";
        } else {
          if (msg.getReturnValue().getFrom().getConstructor() != null) {
            summary =
                String.format(
                    "return new %s%s", classname(msg), getObjRepr(msg.returnValue.object));
          } else if (msg.getReturnValue().getFrom().getField() != null) {
            Field field = msg.getReturnValue().getFrom().getField();
            String fieldName = null;
            if (field != null && field.name != null && !field.name.isEmpty()) {
              fieldName = "(" + msg.getReturnValue().getFrom().getField().name + ")";
            }
            summary =
                String.format(
                        "return %s%s %s",
                        classname(msg),
                        getObjRepr(msg.returnValue.object),
                        fieldName != null ? fieldName : "")
                    .trim();
          } else {
            summary =
                String.format("return %s%s", classname(msg), getObjRepr(msg.returnValue.object));
          }
        }
        break;
      default:
        summary = "UNKNOWN MESSAGE TYPE";
    }

    // escape EOL chars
    String eolEscaped = summary.replaceAll("\\r?\\n", "\\\\n");

    // return, trimming to max length
    return trimToMaxLength(eolEscaped);
  }

  /**
   * Trims the provided string to the maximum allowed summary length.
   *
   * @param str the string to trim.
   * @return the trimmed string if it exceeds {@code SUMMARY_MAX_LENGTH}, otherwise the original
   *     string.
   */
  private static String trimToMaxLength(String str) {
    return str.length() > SUMMARY_MAX_LENGTH ? str.substring(0, SUMMARY_MAX_LENGTH) : str;
  }
}
