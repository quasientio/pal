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
package io.quasient.pal.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Help section renderer that groups {@code pal run} options by their {@link OptionSpec#order()}
 * value and inserts section headings between groups.
 *
 * <p>Options are partitioned into groups based on order boundaries. The first group (core options)
 * appears under the command's default {@code optionListHeading}. Each subsequent group gets its own
 * heading line.
 *
 * <p>Register this renderer for {@link
 * picocli.CommandLine.Model.UsageMessageSpec#SECTION_KEY_OPTION_LIST} on the {@code pal run}
 * subcommand.
 */
public final class GroupedOptionListRenderer implements CommandLine.IHelpSectionRenderer {

  /** Minimum {@code order} value that starts each group (sorted ascending). */
  private static final int[] GROUP_MIN_ORDER = {10, 30, 40, 60, 70, 90};

  /** Heading text for each group (parallel array with {@link #GROUP_MIN_ORDER}). */
  private static final String[] GROUP_HEADING = {
    "Logs (WAL)", "Recording Scope", "Replay", "Interception", "RPC", "Configuration"
  };

  /** Renders the option list with group headings inserted between sections. */
  @Override
  public String render(Help help) {
    List<OptionSpec> options = new ArrayList<>();
    for (OptionSpec opt : help.commandSpec().options()) {
      if (!opt.hidden()) {
        options.add(opt);
      }
    }
    options.sort(Comparator.comparingInt(OptionSpec::order));

    Help.IParamLabelRenderer paramRenderer = help.createDefaultParamLabelRenderer();
    StringBuilder sb = new StringBuilder();
    Help.Layout layout = help.createDefaultLayout();
    int boundaryIdx = 0;

    for (OptionSpec opt : options) {
      while (boundaryIdx < GROUP_MIN_ORDER.length && opt.order() >= GROUP_MIN_ORDER[boundaryIdx]) {
        sb.append(layout);
        layout = help.createDefaultLayout();
        sb.append(String.format("%n%s:%n", GROUP_HEADING[boundaryIdx]));
        boundaryIdx++;
      }
      layout.addOption(opt, paramRenderer);
    }
    sb.append(layout);

    return sb.toString();
  }
}
