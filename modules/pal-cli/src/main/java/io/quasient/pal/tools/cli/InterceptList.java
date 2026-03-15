/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Lists intercepts registered in the PAL directory.
 *
 * <p>This is the intercept-specific list command for the {@code pal intercept ls} pattern. It
 * displays intercepts in short or long format with optional sorting by creation time, reversal, and
 * trimming control.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal intercept ls
 *   pal intercept ls -l
 *   pal intercept ls -l -c -r
 *   pal intercept ls --no-trim
 * </pre>
 */
@Command(
    name = "ls",
    description = "List intercepts",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class InterceptList extends AbstractPalSubcommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(InterceptList.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether to use long listing format. */
  @Option(
      names = {"-l", "--long"},
      description = "use long listing format")
  private boolean longListing;

  /** Flag indicating whether to sort by creation time, newest first. */
  @Option(
      names = {"-c", "--sort-by-ctime"},
      description = "sort by creation time, newest first")
  private boolean sortByCTime;

  /** Flag indicating whether to reverse the order while sorting. */
  @Option(
      names = {"-r", "--reverse"},
      description = "reverse order while sorting")
  private boolean reverseOrder;

  /** Flag indicating whether to disable trimming of long field values. */
  @Option(
      names = {"--no-trim"},
      description = "disable trimming of long field values")
  private boolean noTrimming;

  /** Flag indicating whether the help message is requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Maximum allowed length (in characters) for intercept class names. */
  private static final short MAX_INTERCEPT_CLASS_LEN = 30;

  /** Maximum allowed length (in characters) for intercept target (method/field) names. */
  private static final short MAX_INTERCEPT_TARGET_LEN = 25;

  /** Maximum allowed length (in characters) for intercept callback display. */
  private static final short MAX_INTERCEPT_CALLBACK_LEN = 30;

  /**
   * Format string for long listing of intercepts.
   *
   * <p>uuid peer type class target callback TTL CTime
   */
  private static final String INTERCEPTS_LONG_FORMAT =
      format(
          "%%-36s %%-36s %%-12s %%-%ds %%-%ds %%-%ds %%-8s %%-12s",
          MAX_INTERCEPT_CLASS_LEN, MAX_INTERCEPT_TARGET_LEN, MAX_INTERCEPT_CALLBACK_LEN);

  /** Constructs a new {@code InterceptList} instance. */
  public InterceptList() {}

  /** No validation needed; no mutual exclusion flags. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the subcommand by setting up the directory connection.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Lists intercepts from the PAL directory.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            """
            Error: pal intercept ls requires a PAL directory.
            Specify with --directory/-d option or PAL_DIRECTORY environment variable.
            Example: pal intercept ls -d localhost:2379""");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println(
          """
          Error: Cannot connect to PAL directory.
          Ensure etcd is running and accessible, then specify the directory:
            pal intercept ls -d localhost:2379
          Or set PAL_DIRECTORY environment variable.""");
      return 1;
    }

    Set<InterceptRequest<?>> intercepts = getPalDirectory().listAllIntercepts();
    logger.debug("{} intercepts found in directory", intercepts.size());
    if (longListing) {
      out.printf("total %d%n", intercepts.size());
      if (!intercepts.isEmpty()) {
        out.printf(
            INTERCEPTS_LONG_FORMAT + "%n",
            "UUID",
            "Peer",
            "Type",
            "Class",
            "Target",
            "Callback",
            "TTL",
            "Created");
      }
    }
    printIntercepts(intercepts);
    return 0;
  }

  /**
   * Formats the interceptable target for display.
   *
   * <p>For method calls, shows the method name and parameter types. For field operations, shows the
   * field name and operation type (GET/PUT).
   *
   * @param interceptable the interceptable to format
   * @return a human-readable target description
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static String formatInterceptTarget(Interceptable interceptable) {
    if (interceptable instanceof InterceptableMethodCall methodCall) {
      java.util.List<String> paramTypes = methodCall.getParameterTypes();
      if (paramTypes.isEmpty()) {
        return methodCall.getName() + "()";
      }
      return methodCall.getName()
          + "("
          + paramTypes.stream()
              .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
              .collect(Collectors.joining(", "))
          + ")";
    } else if (interceptable instanceof InterceptableFieldOp fieldOp) {
      return fieldOp.getName() + " [" + fieldOp.getFieldOpType() + "]";
    }
    return interceptable.getName();
  }

  /**
   * Prints the information of an intercept in the appropriate format.
   *
   * @param intercept the {@link InterceptRequest} object to print
   */
  private void print(InterceptRequest<?> intercept) {
    if (longListing) {
      String callbackDisplay =
          intercept.getCallbackClass().contains(".")
              ? intercept
                      .getCallbackClass()
                      .substring(intercept.getCallbackClass().lastIndexOf('.') + 1)
                  + "."
                  + intercept.getCallbackMethod()
              : intercept.getCallbackClass() + "." + intercept.getCallbackMethod();
      String classDisplay =
          intercept.getClazz().contains(".")
              ? intercept.getClazz().substring(intercept.getClazz().lastIndexOf('.') + 1)
              : intercept.getClazz();

      String ttlDisplay = intercept.getTtlSeconds() > 0 ? intercept.getTtlSeconds() + "s" : "-";

      out.printf(
          INTERCEPTS_LONG_FORMAT + "%n",
          intercept.getUuid(),
          intercept.getPeer(),
          intercept.getType(),
          optionallyTrim(classDisplay, MAX_INTERCEPT_CLASS_LEN),
          optionallyTrim(
              formatInterceptTarget(intercept.getInterceptable()), MAX_INTERCEPT_TARGET_LEN),
          optionallyTrim(callbackDisplay, MAX_INTERCEPT_CALLBACK_LEN),
          ttlDisplay,
          getFormattedDate(intercept.getCTime()));
    } else {
      out.printf("%s%n", intercept.getUuid());
    }
  }

  /**
   * Prints the set of intercepts in the specified order.
   *
   * @param intercepts the set of {@link InterceptRequest} objects to print
   */
  private void printIntercepts(Set<InterceptRequest<?>> intercepts) {
    final Comparator<InterceptRequest<?>> comparator;
    if (sortByCTime) {
      final Comparator<InterceptRequest<?>> cTimeComparator =
          Comparator.comparing(
              InterceptRequest::getCTime, Comparator.nullsLast(Comparator.naturalOrder()));
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<InterceptRequest<?>> classComparator =
          Comparator.comparing(InterceptRequest::getClazz);
      comparator = reverseOrder ? classComparator.reversed() : classComparator;
    }

    intercepts.stream().sorted(comparator).forEach(this::print);
  }

  /**
   * Optionally trims the given string to the specified maximum length, appending ".." if trimmed.
   *
   * @param astring the string to trim
   * @param maxLength the maximum allowed length
   * @return the trimmed string if necessary, otherwise the original string
   */
  private String optionallyTrim(String astring, int maxLength) {
    return ListFormatUtils.optionallyTrim(astring, maxLength, noTrimming);
  }

  /**
   * Formats the given date and time.
   *
   * @param dateTime the date and time to format
   * @return a formatted date string in "MMM dd HH:mm" format
   */
  private static String getFormattedDate(OffsetDateTime dateTime) {
    return ListFormatUtils.getFormattedDate(dateTime);
  }
}
