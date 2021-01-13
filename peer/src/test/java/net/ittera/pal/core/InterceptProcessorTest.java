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

package net.ittera.pal.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.Interceptable.InterceptableType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.cxn.DirectoryConnectionFactory;
import net.ittera.pal.cxn.PALDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class App {
  @net.ittera.pal.common.lang.intercept.Before(clazz = "java.io.PrintStream", method = "println")
  public static void printlnAndStop(String line) {}
}

public class InterceptProcessorTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private PALDirectory palDirectory;
  private InterceptProcessor interceptProcessor;
  private final UUID peerUuid = UUID.randomUUID();
  private List<InterceptRequest> requests;

  @Before
  public void setUp() throws Exception {
    requests = new ArrayList<>();
    palDirectory = mock(PALDirectory.class);
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  requests.add((InterceptRequest) args[0]);
                  return null;
                })
        .when(palDirectory)
        .registerInterceptAsync(any(), any());

    DirectoryConnectionFactory directoryConnectionFactory = mock(DirectoryConnectionFactory.class);
    when(directoryConnectionFactory.getConnection()).thenReturn(Optional.of(palDirectory));
    interceptProcessor = new InterceptProcessor(peerUuid, directoryConnectionFactory);
  }

  @After
  public void tearDown() throws Exception {
    palDirectory.close();
  }

  @Test
  public void processClassWithBeforeAnnotation() throws Exception {
    interceptProcessor.process(App.class);

    // ensure register() called
    verify(palDirectory, times(1)).registerInterceptAsync(any(), any());

    // verify request contents
    assertThat(requests.size(), is(1));
    InterceptRequest interceptRequest = requests.get(0);
    assertThat(interceptRequest.getType(), is(InterceptType.BEFORE));
    assertThat(interceptRequest.getClazz(), is("java.io.PrintStream"));
    assertThat(interceptRequest.getCallbackClass(), is("net.ittera.pal.core.App"));
    assertThat(interceptRequest.getCallbackMethod(), is("printlnAndStop"));
    assertThat(interceptRequest.getInterceptable().getType(), is(InterceptableType.METHOD_CALL));
    InterceptableMethodCall interceptable =
        (InterceptableMethodCall) interceptRequest.getInterceptable();
    assertThat(interceptable.getName(), is("println"));
    assertThat(
        interceptable.getParameterTypes(), is(Collections.singletonList("java.lang.String")));
  }
}
