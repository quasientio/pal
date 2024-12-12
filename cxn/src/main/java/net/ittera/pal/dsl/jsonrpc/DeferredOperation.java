package net.ittera.pal.dsl.jsonrpc;

import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;

public class DeferredOperation {
  public enum OpType {
    NEW_INSTANCE,
    STATIC_METHOD,
    INSTANCE_METHOD,
    STATIC_FIELD_GET,
    STATIC_FIELD_PUT,
    INSTANCE_FIELD_GET,
    INSTANCE_FIELD_PUT
  }

  private OpType opType;
  private String className;
  private String methodName;
  private String fieldName;
  private Object[] args;
  private String instanceVarName; // Used if we defer instance references by varName
  private ObjectRef directInstanceRef; // If we have a known ref
  private String resultVarName;

  public static DeferredOperation newInstance(
      String className, @Nullable String varName, @Nullable Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.NEW_INSTANCE;
    op.className = className;
    op.args = args;
    op.resultVarName = varName;
    return op;
  }

  public static DeferredOperation staticMethod(
      String className, String methodName, @Nullable String resultVarName, Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_METHOD;
    op.className = className;
    op.methodName = methodName;
    op.args = args;
    op.resultVarName = resultVarName;
    return op;
  }

  public static DeferredOperation instanceMethod(
      String className,
      String methodName,
      @Nullable String resultVarName,
      String instanceVarName,
      ObjectRef directRef,
      Object[] args) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_METHOD;
    op.className = className;
    op.methodName = methodName;
    op.resultVarName = resultVarName;
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    op.args = args;
    return op;
  }

  public static DeferredOperation staticFieldGet(
      String className, String fieldName, @Nullable String resultVarName) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_FIELD_GET;
    op.className = className;
    op.fieldName = fieldName;
    op.resultVarName = resultVarName;
    return op;
  }

  public static DeferredOperation staticFieldPut(String className, String fieldName, Object value) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.STATIC_FIELD_PUT;
    op.className = className;
    op.fieldName = fieldName;
    op.args = new Object[] {value};
    return op;
  }

  public static DeferredOperation instanceFieldGet(
      String className,
      String fieldName,
      @Nullable String resultVarName,
      String instanceVarName,
      ObjectRef directRef) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_FIELD_GET;
    op.className = className;
    op.fieldName = fieldName;
    op.resultVarName = resultVarName;
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    return op;
  }

  public static DeferredOperation instanceFieldPut(
      String className,
      String fieldName,
      Object value,
      String instanceVarName,
      ObjectRef directRef) {
    DeferredOperation op = new DeferredOperation();
    op.opType = OpType.INSTANCE_FIELD_PUT;
    op.className = className;
    op.fieldName = fieldName;
    op.args = new Object[] {value};
    op.instanceVarName = instanceVarName;
    op.directInstanceRef = directRef;
    return op;
  }

  public OpType getOpType() {
    return opType;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getFieldName() {
    return fieldName;
  }

  public Object[] getArgs() {
    return args;
  }

  public String getInstanceVarName() {
    return instanceVarName;
  }

  public ObjectRef getDirectInstanceRef() {
    return directInstanceRef;
  }

  public String getResultVarName() {
    return resultVarName;
  }
}
