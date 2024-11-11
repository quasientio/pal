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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.ExecMessageType;

public class ExecMessageUtils {

  public static String getClassname(ExecMessage execMessage) {
    final ExecMessageType msgType = ExecMessageType.fromByte(execMessage.getExecMessageType());
    return switch (msgType) {
      case CONSTRUCTOR -> execMessage.getConstructorCall().getClazz().getName();
      case INSTANCE_METHOD -> execMessage.getInstanceMethodCall().getClazz().getName();
      case CLASS_METHOD -> execMessage.getClassMethodCall().getClazz().getName();
      case GET_STATIC -> execMessage.getStaticFieldGet().getClazz().getName();
      case GET_FIELD -> execMessage.getInstanceFieldGet().getClazz().getName();
      case PUT_STATIC -> execMessage.getStaticFieldPut().getClazz().getName();
      case PUT_FIELD -> execMessage.getInstanceFieldPut().getClazz().getName();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", msgType));
    };
  }

  public static String getExecutableName(ExecMessage execMessage) {
    final ExecMessageType execMessageType =
        ExecMessageType.fromByte(execMessage.getExecMessageType());
    return switch (execMessageType) {
      case CONSTRUCTOR -> "<init>";
      case INSTANCE_METHOD -> execMessage.getInstanceMethodCall().getName();
      case CLASS_METHOD -> execMessage.getClassMethodCall().getName();
      case GET_STATIC -> execMessage.getStaticFieldGet().getField().getName();
      case GET_FIELD -> execMessage.getInstanceFieldGet().getField().getName();
      case PUT_STATIC -> execMessage.getStaticFieldPut().getField().getName();
      case PUT_FIELD -> execMessage.getInstanceFieldPut().getField().getName();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported ExecMessage type: %s", execMessageType));
    };
  }

  /**
   * Get the parameter types of the given ExecMessage.
   *
   * @param execMessage the ExecMessage to get the parameter types from
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
