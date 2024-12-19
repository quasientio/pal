package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Field;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.RpcMessageSummaryUtil;

public class ExecMessageSummaryUtil extends RpcMessageSummaryUtil {

  private static String getObjRepr(Obj obj, String objectRef) {
    return getObjRepr(
        obj == null ? null : obj.isNull,
        obj == null ? null : obj.value == null ? null : obj.value,
        objectRef);
  }

  private static String getObjRepr(Obj obj) {
    return getObjRepr(obj.isNull, obj.value, obj.ref);
  }

  // Helper method to get the class name based on the message type
  private static String classname(ExecMessage msg) {
    return shortClassname(getClassname(msg));
  }

  public static String getOneLinerSummary(ExecMessage msg) {
    final MessageType execMessageType = getMessageTypeOf(msg);
    switch (execMessageType) {
      case EXEC_CONSTRUCTOR:
        return "new " + classname(msg);
      case EXEC_INSTANCE_METHOD:
        return String.format(
            "call %s.%s@%s",
            classname(msg), msg.instanceMethodCall.name, msg.instanceMethodCall.objectRef);
      case EXEC_CLASS_METHOD:
        return String.format("call %s.%s", classname(msg), msg.classMethodCall.name);
      case EXEC_GET_STATIC:
        return String.format("get %s.%s", classname(msg), msg.staticFieldGet.field.name);
      case EXEC_GET_FIELD:
        return String.format(
            "get %s.%s@%s",
            classname(msg), msg.instanceFieldGet.field.name, msg.instanceFieldGet.objectRef);
      case EXEC_PUT_STATIC:
        return String.format(
            "put %s.%s ⇦ %s",
            classname(msg),
            msg.staticFieldPut.field.name,
            getObjRepr(msg.staticFieldPut.valueObject, msg.staticFieldPut.valueObjectRef));
      case EXEC_PUT_FIELD:
        return String.format(
            "put %s.%s@%s ⇦ %s",
            classname(msg),
            msg.instanceFieldPut.field.name,
            msg.instanceFieldPut.objectRef,
            getObjRepr(msg.instanceFieldPut.valueObject, msg.instanceFieldPut.valueObjectRef));
      case EXEC_PUT_STATIC_DONE:
        return String.format("put_done %s.%s", classname(msg), msg.staticFieldPutDone.field.name);
      case EXEC_PUT_FIELD_DONE:
        return String.format("put_done %s.%s", classname(msg), msg.instanceFieldPutDone.field.name);
      case EXEC_THROWABLE:
        return String.format(
            "throw %s: \"%s\"", classname(msg), msg.raisedThrowable.throwable.message);
      case EXEC_RETURN_VALUE:
        if (msg.returnValue.isVoid) {
          return "return void";
        } else {
          if (msg.getReturnValue().getFrom().getConstructor() != null) {
            return String.format(
                "return new %s%s", classname(msg), getObjRepr(msg.returnValue.object));
          } else if (msg.getReturnValue().getFrom().getField() != null) {
            Field field = msg.getReturnValue().getFrom().getField();
            String fieldName = null;
            if (field != null && field.name != null && !field.name.isEmpty()) {
              fieldName = "(" + msg.getReturnValue().getFrom().getField().name + ")";
            }
            return String.format(
                    "return %s%s %s",
                    classname(msg),
                    getObjRepr(msg.returnValue.object),
                    fieldName != null ? fieldName : "")
                .trim();
          } else {
            return String.format("return %s%s", classname(msg), getObjRepr(msg.returnValue.object));
          }
        }
      default:
        return "UNKNOWN MESSAGE TYPE";
    }
  }
}
