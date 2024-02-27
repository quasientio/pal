/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.jsonrpc.InvalidJsonRpcRequestException;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.ExecMessageType;

public class MessageUtils {

  private static Gson gson = new Gson();

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

  public static JsonRpcRequest parseAndValidateJsonRpcMessage(String jsonRpcMessage)
      throws InvalidJsonRpcRequestException {
    // Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = gson.fromJson(jsonRpcMessage, JsonRpcRequest.class);

    // Set the ExecMessageType and other fields based on the method field
    try {
      jsonRpcRequest.processMethodParts();
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcRequestException(e);
    }

    // Check for empty/illegal RPC version
    if (!("2.0").equals(jsonRpcRequest.getJsonrpc())) {
      throw new InvalidJsonRpcRequestException(
          "Invalid JSON-RPC version: " + jsonRpcRequest.getJsonrpc());
    }

    // Check for empty id
    if (jsonRpcRequest.getId() == null || jsonRpcRequest.getId().isEmpty()) {
      throw new InvalidJsonRpcRequestException("Missing or blank JSON-RPC id)");
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

  public static String getClassname(ExecMessage execMessage) {
    final ExecMessageType msgType = ExecMessageType.fromByte(execMessage.getExecMessageType());
    switch (msgType) {
      case CONSTRUCTOR:
        return execMessage.getConstructorCall().getClazz().getName();
      case INSTANCE_METHOD:
        return execMessage.getInstanceMethodCall().getClazz().getName();
      case CLASS_METHOD:
        return execMessage.getClassMethodCall().getClazz().getName();
      case GET_STATIC:
        return execMessage.getStaticFieldGet().getClazz().getName();
      case GET_FIELD:
        return execMessage.getInstanceFieldGet().getClazz().getName();
      case PUT_STATIC:
        return execMessage.getStaticFieldPut().getClazz().getName();
      case PUT_FIELD:
        return execMessage.getInstanceFieldPut().getClazz().getName();
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported ExecMessage type: %s", msgType));
    }
  }

  public static String getExecutableName(ExecMessage execMessage) {
    final ExecMessageType execMessageType =
        ExecMessageType.fromByte(execMessage.getExecMessageType());
    switch (execMessageType) {
      case CONSTRUCTOR:
        return "new";
      case INSTANCE_METHOD:
        return execMessage.getInstanceMethodCall().getName();
      case CLASS_METHOD:
        return execMessage.getClassMethodCall().getName();
      case GET_STATIC:
        return execMessage.getStaticFieldGet().getField().getName();
      case GET_FIELD:
        return execMessage.getInstanceFieldGet().getField().getName();
      case PUT_STATIC:
        return execMessage.getStaticFieldPut().getField().getName();
      case PUT_FIELD:
        return execMessage.getInstanceFieldPut().getField().getName();
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported ExecMessage type: %s", execMessageType));
    }
  }

  /**
   * @return null if not a constructor/method call, possibly empty list of parameter class names
   *     otherwise
   */
  public static List<String> getParameterTypes(ExecMessage execMessage) {
    final ExecMessageType execMessageType =
        ExecMessageType.fromByte(execMessage.getExecMessageType());
    Parameter[] params;
    switch (execMessageType) {
      case CONSTRUCTOR:
        params = execMessage.getConstructorCall().getParameters();
        break;
      case INSTANCE_METHOD:
        params = execMessage.getInstanceMethodCall().getParameters();
        break;
      case CLASS_METHOD:
        params = execMessage.getClassMethodCall().getParameters();
        break;
      default:
        return null;
    }

    if (params != null && params.length > 0) {
      return Arrays.stream(params).map(p -> p.getType().getName()).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
