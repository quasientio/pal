package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getClassname;

import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Field;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.types.ExecMessageType;

public class ExecMessageSummaryUtil {

  // Helper method to get the short class name
  private static String shortClassname(String className) {
    if (className.contains(".")) {
      String prefix = "";
      if (className.startsWith("[L")) {
        prefix = "[L";
      } else if (className.startsWith("[")) {
        prefix = "[";
      }
      return prefix + className.substring(className.lastIndexOf('.') + 1);
    } else {
      return className;
    }
  }

  // Helper method to get value or reference
  private static String getObjRepr(Obj obj, String objectRef) {
    if (obj != null && obj.isNull) {
      return "=NULL";
    }

    String repr = "";
    if (objectRef != null && !objectRef.isEmpty()) {
      repr = "@" + objectRef;
    }
    if (obj != null && obj.value != null && !obj.value.isEmpty()) {
      repr += "(=" + obj.value + ")";
    }
    return repr;
  }

  private static String getObjRepr(Obj obj) {
    if (obj.isNull) {
      return "=NULL";
    }
    String repr = "@" + obj.ref;
    if (obj.value != null && !obj.value.isEmpty()) {
      repr += "(=" + obj.value + ")";
    }
    return repr;
  }

  // Helper method to get the class name based on the message type
  private static String classname(ExecMessage msg) {
    return shortClassname(getClassname(msg));
  }

  public static String getOneLinerSummary(ExecMessage msg) {
    ExecMessageType execMessageType = ExecMessageType.fromByte(msg.execMessageType);
    switch (execMessageType) {
      case CONSTRUCTOR:
        return "new " + classname(msg);
      case INSTANCE_METHOD:
        return String.format(
            "call %s.%s@%s",
            classname(msg), msg.instanceMethodCall.name, msg.instanceMethodCall.objectRef);
      case CLASS_METHOD:
        return String.format("call %s.%s", classname(msg), msg.classMethodCall.name);
      case GET_STATIC:
        return String.format("get %s.%s", classname(msg), msg.staticFieldGet.field.name);
      case GET_FIELD:
        return String.format(
            "get %s.%s@%s",
            classname(msg), msg.instanceFieldGet.field.name, msg.instanceFieldGet.objectRef);
      case PUT_STATIC:
        return String.format(
            "put %s.%s ⇦ %s",
            classname(msg),
            msg.staticFieldPut.field.name,
            getObjRepr(msg.staticFieldPut.valueObject, msg.staticFieldPut.valueObjectRef));
      case PUT_FIELD:
        return String.format(
            "put %s.%s@%s ⇦ %s",
            classname(msg),
            msg.instanceFieldPut.field.name,
            msg.instanceFieldPut.objectRef,
            getObjRepr(msg.instanceFieldPut.valueObject, msg.instanceFieldPut.valueObjectRef));
      case PUT_STATIC_DONE:
        return String.format("put_done %s.%s", classname(msg), msg.staticFieldPutDone.field.name);
      case PUT_FIELD_DONE:
        return String.format("put_done %s.%s", classname(msg), msg.instanceFieldPutDone.field.name);
      case THROWABLE:
        return String.format(
            "throw %s: \"%s\"", classname(msg), msg.raisedThrowable.throwable.message);
      case RETURN_VALUE:
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
