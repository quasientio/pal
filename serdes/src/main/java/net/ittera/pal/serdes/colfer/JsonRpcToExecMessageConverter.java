package net.ittera.pal.serdes.colfer;

import com.google.gson.Gson;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.*;
import net.ittera.pal.messages.colfer.Class;
import net.ittera.pal.messages.jsonrpc.InvalidJsonRpcRequestException;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.ExecMessageType;

public class JsonRpcToExecMessageConverter {
  private Gson gson = new Gson();
  private final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  /**
   * Regular expression for a valid class name. This regex ensures that the class name starts with a
   * letter, underscore, or dollar sign and is followed by any combination of letters, digits,
   * underscores, or dollar signs. This includes names with Unicode characters, making it compliant
   * with Java's naming rules for class identifiers.
   */
  private static final String VALID_CLASS_NAME_REGEX = "^[\\p{L}_$][\\p{L}\\p{N}_$]*$";

  private static final Pattern VALID_CLASS_NAME_PATTERN = Pattern.compile(VALID_CLASS_NAME_REGEX);

  private static final List<String> JAVA_RESERVED_KEYWORDS =
      Arrays.asList(
          "class",
          "null",
          "true",
          "false",
          "final",
          "public",
          "private",
          "protected",
          "static",
          "void",
          "int",
          "long",
          "float",
          "double",
          "byte",
          "short",
          "char",
          "boolean",
          "if",
          "else",
          "while",
          "for",
          "do",
          "switch",
          "case",
          "default",
          "break",
          "continue",
          "return",
          "try",
          "catch",
          "finally",
          "throw",
          "throws",
          "new",
          "this",
          "super",
          "extends",
          "implements",
          "interface",
          "package",
          "import",
          "instanceof",
          "enum",
          "assert",
          "abstract",
          "const",
          "goto",
          "native",
          "synchronized",
          "transient",
          "volatile");

  JsonRpcRequest parseAndValidateJsonRpcMessage(String jsonRpcMessage)
      throws InvalidJsonRpcRequestException {
    // Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = gson.fromJson(jsonRpcMessage, JsonRpcRequest.class);

    // Set the ExecMessageType and other fields based on the method field
    try {
      jsonRpcRequest.processMethodParts();
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcRequestException(e);
    }

    // Check for illegal characters in the class name
    if (!VALID_CLASS_NAME_PATTERN.matcher(jsonRpcRequest.getClassName()).matches()) {
      throw new InvalidJsonRpcRequestException(
          "Invalid characters in class name: " + jsonRpcRequest.getClassName());
    }

    // Check for Java reserved keywords in the class name
    if (JAVA_RESERVED_KEYWORDS.contains(jsonRpcRequest.getClassName())) {
      throw new InvalidJsonRpcRequestException(
          "Class name is a Java reserved keyword: " + jsonRpcRequest.getClassName());
    }

    // Check for parameter consistency in field operations: PUTs should have exactly one parameter
    if (jsonRpcRequest.getExecMessageType() == ExecMessageType.PUT_STATIC
        || jsonRpcRequest.getExecMessageType() == ExecMessageType.PUT_FIELD) {
      if (jsonRpcRequest.getParams().size() != 1) {
        throw new InvalidJsonRpcRequestException(
            "Field put must have exactly one parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)");
      }
    }

    // Check for parameter consistency in field operations: GETs should have no parameters
    if (jsonRpcRequest.getExecMessageType() == ExecMessageType.GET_STATIC
        || jsonRpcRequest.getExecMessageType() == ExecMessageType.GET_FIELD) {
      if (!jsonRpcRequest.getParams().isEmpty()) {
        throw new InvalidJsonRpcRequestException(
            "Field get cannot have any parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)");
      }
    }

    return jsonRpcRequest;
  }

  public ExecMessage convertJsonRpcRequestToExecMessage(String jsonRpcMessage, UUID fromPeerUuid)
      throws InvalidJsonRpcRequestException {
    // 1. Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);

    // 2. Create an instance of ExecMessage and initialize required common fields
    ExecMessage execMessage = new ExecMessage();
    execMessage.setPeerUuid(fromPeerUuid.toString());
    execMessage.setMessageUuid(jsonRpcRequest.getId());
    execMessage.setExecMessageType(jsonRpcRequest.getExecMessageType().toByte());

    // currentTime is meant for the client to indicate when then message is sent; as we don't have
    // it in a JSON-RPC request, we set it here to the time the message is received
    execMessage.setCurrentTime(dateTimeFormatter.format(ZonedDateTime.now()));

    // 3. Create the appropriate ExecMessage call object based on the ExecMessageType
    switch (jsonRpcRequest.getExecMessageType()) {
      case CONSTRUCTOR:
        execMessage.setConstructorCall(createConstructorCall(jsonRpcRequest));
        break;
      case GET_STATIC:
        execMessage.setStaticFieldGet(createStaticFieldGet(jsonRpcRequest));
        break;
      case GET_FIELD:
        execMessage.setInstanceFieldGet(createInstanceFieldGet(jsonRpcRequest));
        break;
      case PUT_STATIC:
        execMessage.setStaticFieldPut(createStaticFieldPut(jsonRpcRequest));
        break;
      case PUT_FIELD:
        execMessage.setInstanceFieldPut(createInstanceFieldPut(jsonRpcRequest));
        break;
      case CLASS_METHOD:
        execMessage.setClassMethodCall(createClassMethodCall(jsonRpcRequest));
        break;
      case INSTANCE_METHOD:
        execMessage.setInstanceMethodCall(createInstanceMethodCall(jsonRpcRequest));
        break;
      default:
        throw new InvalidJsonRpcRequestException(
            "Unexpected type: " + jsonRpcRequest.getExecMessageType().name());
    }
    return execMessage;
  }

  private InstanceMethodCall createInstanceMethodCall(JsonRpcRequest jsonRpcRequest) {
    InstanceMethodCall instanceMethodCall = new InstanceMethodCall();
    instanceMethodCall.setClazz(getWrappedClass(jsonRpcRequest.getClassName()));
    instanceMethodCall.setName(jsonRpcRequest.getMethodName());
    instanceMethodCall.setObjectRef(jsonRpcRequest.getObjectRef());
    instanceMethodCall.setParameters(convertJsonParamsToColferParams(jsonRpcRequest.getParams()));
    return instanceMethodCall;
  }

  private ClassMethodCall createClassMethodCall(JsonRpcRequest jsonRpcRequest) {
    ClassMethodCall classMethodCall = new ClassMethodCall();
    classMethodCall.setClazz(getWrappedClass(jsonRpcRequest.getClassName()));
    classMethodCall.setName(jsonRpcRequest.getMethodName());
    classMethodCall.setParameters(convertJsonParamsToColferParams(jsonRpcRequest.getParams()));
    return classMethodCall;
  }

  private InstanceFieldPut createInstanceFieldPut(JsonRpcRequest jsonRpcRequest) {
    InstanceFieldPut instanceFieldPut = new InstanceFieldPut();
    instanceFieldPut.setClazz(getWrappedClass(jsonRpcRequest.getClassName()));
    instanceFieldPut.setField(getWrappedField(jsonRpcRequest.getFieldName()));
    instanceFieldPut.setObjectRef(jsonRpcRequest.getObjectRef());
    JsonRpcParameter value = jsonRpcRequest.getParams().get(0);
    if ("objectRef".equals(value.getType())) { // value is an object reference
      instanceFieldPut.setValueObjectRef(value.getValue().toString());
    } else {
      instanceFieldPut.setValueObject(getWrappedObject(value.getValue(), value.getType(), null));
    }
    return instanceFieldPut;
  }

  private StaticFieldPut createStaticFieldPut(JsonRpcRequest jsonRpcRequest) {
    StaticFieldPut staticFieldPut = new StaticFieldPut();
    staticFieldPut.setClazz(getWrappedClass(jsonRpcRequest.getClassName()));
    staticFieldPut.setField(getWrappedField(jsonRpcRequest.getFieldName()));
    JsonRpcParameter value = jsonRpcRequest.getParams().get(0);
    if ("objectRef".equals(value.getType())) { // value is an object reference
      staticFieldPut.setValueObjectRef(value.getValue().toString());
    } else {
      staticFieldPut.setValueObject(getWrappedObject(value.getValue(), value.getType(), null));
    }
    return staticFieldPut;
  }

  private InstanceFieldGet createInstanceFieldGet(JsonRpcRequest jsonRpcRequest) {
    InstanceFieldGet instanceFieldGet = new InstanceFieldGet();
    instanceFieldGet.setClazz(new Class().withName(jsonRpcRequest.getClassName()));
    instanceFieldGet.setField(new Field().withName(jsonRpcRequest.getFieldName()));
    instanceFieldGet.setObjectRef(jsonRpcRequest.getObjectRef());
    return instanceFieldGet;
  }

  private StaticFieldGet createStaticFieldGet(JsonRpcRequest jsonRpcRequest) {
    StaticFieldGet staticFieldGet = new StaticFieldGet();
    staticFieldGet.setClazz(new Class().withName(jsonRpcRequest.getClassName()));
    staticFieldGet.setField(new Field().withName(jsonRpcRequest.getFieldName()));
    return staticFieldGet;
  }

  private ConstructorCall createConstructorCall(JsonRpcRequest jsonRpcRequest) {
    ConstructorCall constructorCall = new ConstructorCall();
    constructorCall.setClazz(new Class().withName(jsonRpcRequest.getClassName()));
    constructorCall.setParameters(convertJsonParamsToColferParams(jsonRpcRequest.getParams()));
    return constructorCall;
  }

  // TODO
  private Parameter[] convertJsonParamsToColferParams(List<JsonRpcParameter> jsonParams) {
    // Convert JSON-RPC parameters to Colfer Parameter objects
    // Loop through jsonParams and create Parameter objects
    return null;
  }

  /** Helper methods: copied from MessageBuilder.java TODO: extract to a common class */
  private net.ittera.pal.messages.colfer.Class getWrappedClass(String className) {
    return Wrapper.getWrappedClass(className);
  }

  private <T> Obj getWrappedObject(Object object, T t, ObjectRef objectRef) {
    return Wrapper.getWrappedObject(object, t, objectRef);
  }

  private net.ittera.pal.messages.colfer.Field getWrappedField(String fieldName) {
    return Wrapper.getWrappedField((String) null, fieldName);
  }

  private Parameter[] createNamedParameters(
      String[] parameterTypes, Object[] args, ObjectRef[] argObjRefs) {
    final int paramsTypesLength = parameterTypes == null ? 0 : parameterTypes.length;
    final int argsLength = args == null ? 0 : args.length;
    final int argsObjRefsLength = argObjRefs == null ? 0 : argObjRefs.length;
    if (paramsTypesLength < argsLength || paramsTypesLength < argsObjRefsLength) {
      throw new IllegalArgumentException(
          "parameterTypes must be of same length as args and argObjRefs");
    }
    final Parameter[] params = new Parameter[paramsTypesLength];
    for (int i = 0; i < paramsTypesLength; i++) {
      if (argObjRefs[i] != null) { // parameter is an objectref
        params[i] = createParameter(parameterTypes[i], null, argObjRefs[i]);
      } else if (args[i] != null) { // parameter is string, primitive or wrapper
        params[i] = createParameter(parameterTypes[i], args[i], null);
      } else { // parameter is null
        params[i] = createParameter(parameterTypes[i], null, null);
      }
    }

    return params;
  }

  private Parameter createParameter(String parameterType, Object arg, ObjectRef argObjRef) {
    Object argValue = arg instanceof Obj ? ((Obj) arg).getValue() : arg;
    return new Parameter()
        .withType(getWrappedClass(parameterType))
        .withValue(getWrappedObject(argValue, parameterType, argObjRef));
  }
}
