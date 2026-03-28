/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli.init;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Pre-configured {@link PromptProvider} for unit tests.
 *
 * <p>Answers are enqueued before running the wizard and dequeued in order as prompts are issued.
 * This allows deterministic testing of wizard flows without a real terminal.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * TestPromptProvider provider = new TestPromptProvider();
 * provider.enqueueText("com.example");   // answer to groupId prompt
 * provider.enqueueText("my-app");        // answer to artifactId prompt
 * provider.enqueueYesNo(true);           // answer to yes/no prompt
 * provider.enqueueSelect(BuildTool.MAVEN); // answer to select prompt
 *
 * InitWizard wizard = new InitWizard(provider, targetDir);
 * InitConfig config = wizard.run();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class TestPromptProvider implements PromptProvider {

  /** Queue of pre-configured text answers. */
  private final Queue<String> textAnswers = new ArrayDeque<>();

  /** Queue of pre-configured yes/no answers. */
  private final Queue<Boolean> yesNoAnswers = new ArrayDeque<>();

  /** Queue of pre-configured select answers. */
  private final Queue<Object> selectAnswers = new ArrayDeque<>();

  /** Collected output messages for verification. */
  private final List<String> outputMessages = new ArrayList<>();

  /**
   * Enqueues a text answer to be returned by the next {@link #promptText} call.
   *
   * <p>Pass {@code null} or empty string to accept the default value.
   *
   * @param answer the text answer
   */
  public void enqueueText(String answer) {
    textAnswers.add(answer);
  }

  /**
   * Enqueues a yes/no answer to be returned by the next {@link #promptYesNo} call.
   *
   * @param answer the yes/no answer
   */
  public void enqueueYesNo(boolean answer) {
    yesNoAnswers.add(answer);
  }

  /**
   * Enqueues a select answer to be returned by the next {@link #promptSelect} call.
   *
   * @param answer the select answer (must match one of the offered options)
   */
  public void enqueueSelect(Object answer) {
    selectAnswers.add(answer);
  }

  /**
   * Returns all output messages printed during the wizard run.
   *
   * @return the list of output messages
   */
  public List<String> getOutputMessages() {
    return outputMessages;
  }

  /** {@inheritDoc} */
  @Override
  public String promptText(String prompt, String defaultValue) {
    if (textAnswers.isEmpty()) {
      return defaultValue;
    }
    String answer = textAnswers.poll();
    if (answer == null || answer.isEmpty()) {
      return defaultValue;
    }
    return answer;
  }

  /** {@inheritDoc} */
  @Override
  public boolean promptYesNo(String prompt, boolean defaultValue) {
    if (yesNoAnswers.isEmpty()) {
      return defaultValue;
    }
    return yesNoAnswers.poll();
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T promptSelect(String prompt, List<T> options, T defaultValue) {
    if (selectAnswers.isEmpty()) {
      return defaultValue;
    }
    Object answer = selectAnswers.poll();
    if (options.contains(answer)) {
      return (T) answer;
    }
    return defaultValue;
  }

  /** {@inheritDoc} */
  @Override
  public void println(String message) {
    outputMessages.add(message);
  }
}
