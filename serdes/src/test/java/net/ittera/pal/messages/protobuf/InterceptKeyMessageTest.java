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

package net.ittera.pal.messages.protobuf;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import org.junit.Test;

public class InterceptKeyMessageTest {

  @Test
  public void equals() {

    final String classname = "java.io.PrintStream";
    final String executableName = "println";
    final List<String> paramTypes = Arrays.asList("java.lang.String", "java.lang.String");

    final InterceptKeyMessage keyMessage1 =
        InterceptKeyMessage.newBuilder()
            .setClazz(classname)
            .setExecutableName(executableName)
            .setMsgType(ExecMessageType.CONSTRUCTOR)
            .addAllParameterType(paramTypes)
            .build();

    final InterceptKeyMessage keyMessage2 =
        InterceptKeyMessage.newBuilder()
            .setClazz(classname)
            .setExecutableName(executableName)
            .setMsgType(ExecMessageType.CONSTRUCTOR)
            .addAllParameterType(paramTypes)
            .build();

    assertThat(keyMessage1, is(keyMessage2));
  }
}
