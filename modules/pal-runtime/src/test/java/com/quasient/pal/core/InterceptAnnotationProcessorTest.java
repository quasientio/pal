/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.Interceptable.InterceptableType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class InterceptAnnotationProcessorTest {

  private PalDirectory palDirectory;
  private InterceptAnnotationProcessor interceptAnnotationProcessor;
  private final UUID peerUuid = UUID.randomUUID();
  private List<InterceptRequest<?>> requests;

  @Before
  public void setUp() throws Exception {
    requests = new ArrayList<>();
    palDirectory = mock(PalDirectory.class);
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  requests.add((InterceptRequest<?>) args[0]);
                  return null;
                })
        .when(palDirectory)
        .createIntercept(any());

    DirectoryConnectionProvider directoryConnectionProvider =
        mock(DirectoryConnectionProvider.class);
    when(directoryConnectionProvider.get()).thenReturn(Optional.of(palDirectory));
    interceptAnnotationProcessor =
        new InterceptAnnotationProcessor(peerUuid, directoryConnectionProvider);
  }

  @Test
  public void processClassWithBeforeAnnotation() throws Exception {
    interceptAnnotationProcessor.process(App.class);

    // ensure register() called
    verify(palDirectory, times(1)).createIntercept(any());

    // verify request contents
    assertThat(requests.size(), is(1));
    var interceptRequest = requests.get(0);
    assertThat(interceptRequest.getType(), is(InterceptType.BEFORE));
    assertThat(interceptRequest.getClazz(), is("java.io.PrintStream"));
    assertThat(
        interceptRequest.getCallbackClass(),
        is("com.quasient.pal.core.InterceptAnnotationProcessorTest$App"));
    assertThat(interceptRequest.getCallbackMethod(), is("printlnAndStop"));
    assertThat(interceptRequest.getInterceptable().getType(), is(InterceptableType.METHOD_CALL));
    InterceptableMethodCall interceptable =
        (InterceptableMethodCall) interceptRequest.getInterceptable();
    assertThat(interceptable.getName(), is("println"));
    assertThat(
        interceptable.getParameterTypes(), is(Collections.singletonList("java.lang.String")));
  }

  @SuppressWarnings("unused")
  private static class App {
    @com.quasient.pal.common.lang.intercept.Before(
        clazz = "java.io.PrintStream",
        method = "println")
    public static void printlnAndStop(String line) {}
  }
}
