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

import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Option;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageFamily;
import io.quasient.pal.messages.types.MessageType;
import java.io.IOException;
import java.util.List;

/**
 * Shared base class for {@link LogPrint} and {@link PeerPrint} commands.
 *
 * <p>Contains the common output formatting logic (~400 lines) extracted from the former monolithic
 * {@code MessageStreamPrinter} class. This includes:
 *
 * <ul>
 *   <li>Output format selection ({@link OutputFormat}, {@link FormatOptions}, {@link #getFormat()})
 *   <li>Message filtering ({@link #shouldPrint(Long, String, LogMessage)})
 *   <li>Record formatting ({@link #printRecord(String, LogMessage, long)}, {@link
 *       #printTreeRecord(LogMessage, long)})
 *   <li>Pattern-based filtering ({@link #matchesFilters(LogMessage)})
 *   <li>Shared CLI options for filtering and output control
 * </ul>
 *
 * <p>Subclasses implement the source-specific logic: {@link LogPrint} for Kafka/Chronicle log
 * reading, and {@link PeerPrint} for ZMQ PUB/SUB socket streaming.
 *
 * @see LogPrint
 * @see PeerPrint
 */
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD"},
    justification = "CLI command - picocli help field is read by framework via reflection")
abstract class AbstractPrintCommand extends AbstractPalSubcommand {

  /**
   * Enum representing the output formats available for printing messages.
   *
   * <p>Supported formats include:
   *
   * <ul>
   *   <li>FULL - Detailed output with context and headers.
   *   <li>JSON - Output in JSON format.
   *   <li>COMPACT - Concise output with key information.
   *   <li>TREE - Tree view showing nested operation structure with indentation.
   * </ul>
   */
  enum OutputFormat {
    /** Fully detailed output, including context and headers. */
    FULL,

    /** Machine-readable output serialized as JSON. */
    JSON,

    /** Minimalist output showing only the essentials in a compact form. */
    COMPACT,

    /** Tree view showing nested operation structure with indentation. */
    TREE
  }

  /**
   * Output format options group.
   *
   * <p>These options are mutually exclusive. If none is specified, COMPACT is the default.
   */
  static class FormatOptions {
    /** Flag indicating compact output format should be used. */
    @Option(
        names = {"--compact"},
        description = "Compact output format (default)")
    boolean compact;

    /** Flag indicating JSON output format should be used. */
    @Option(
        names = {"--json"},
        description = "JSON output format")
    boolean json;

    /** Flag indicating full output format should be used. */
    @Option(
        names = {"--full"},
        description = "Full output format with all details")
    boolean full;

    /** Flag indicating tree output format should be used. */
    @Option(
        names = {"--tree"},
        description = "Tree output showing nested operation structure")
    boolean tree;
  }

  /**
   * Specifies the output format for the printed messages.
   *
   * <p>The format options are mutually exclusive. If none is specified, COMPACT is the default.
   */
  @ArgGroup FormatOptions formatOptions;

  /**
   * Comma-separated list of message formats to filter by.
   *
   * <p>Supported formats are BINARY and JSON. If specified, only messages matching the provided
   * formats will be displayed.
   */
  @Option(
      names = {"--formats"},
      arity = "0..*",
      split = ",",
      description = "comma-separated list of message formats to filter by (BINARY, JSON)")
  List<String> msgFormats;

  /**
   * Comma-separated list of message types to filter by.
   *
   * <p>Supported types include: CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD, GET_STATIC, GET_FIELD,
   * PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE, RETURN_VALUE, THROWABLE.
   */
  @Option(
      names = {"--types"},
      arity = "0..*",
      split = ",",
      description =
          "comma-separated list of message types to filter by ("
              + "CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD,"
              + " GET_STATIC, GET_FIELD,"
              + " PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE,"
              + " RETURN_VALUE, THROWABLE)")
  List<String> msgTypes;

  /**
   * Peer UUID to filter messages by.
   *
   * <p>If specified, only messages from the peer with this UUID will be displayed.
   */
  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "uuid",
      description = "Filter by peer uuid")
  String fromPeer;

  /**
   * Thread name to filter messages by.
   *
   * <p>If specified, only messages originating from the specified thread name will be displayed.
   */
  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "thread_name",
      description = "Filter by thread name")
  String threadName;

  /**
   * Message ID to filter messages by.
   *
   * <p>If specified, only messages with the given ID will be displayed.
   */
  @Option(
      names = {"--id"},
      paramLabel = "id",
      description = "Filter by message ID")
  String id;

  /**
   * Pattern-based filter for messages.
   *
   * <p>Supported patterns:
   *
   * <ul>
   *   <li>{@code class=com.example.OrderService} - filter by fully-qualified class name
   *   <li>{@code method=add} - filter by method name (constructors, instance methods, class
   *       methods)
   *   <li>{@code field=count} - filter by field name (get/put field operations)
   * </ul>
   *
   * <p>Multiple filters can be specified and all must match (AND logic).
   */
  @Option(
      names = {"--filter"},
      arity = "0..*",
      split = ",",
      paramLabel = "key=value",
      description =
          "Filter by pattern (e.g., class=com.example.OrderService, method=add, field=count). "
              + "Multiple comma-separated filters use AND logic.")
  List<String> filters;

  /** If set, the command will run in verbose mode, providing additional logging information. */
  @Option(names = "-v", description = "Run verbosely")
  boolean verbose;

  /** Displays the help message and exits. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display this help message")
  private boolean helpRequested = false;

  /** Tracks the current nesting depth for TREE output format. */
  private int treeDepth = 0;

  /** Constructs a new {@code AbstractPrintCommand} instance. */
  protected AbstractPrintCommand() {}

  /**
   * Gets the selected output format.
   *
   * @return the selected OutputFormat, defaulting to COMPACT if none specified
   */
  OutputFormat getFormat() {
    if (formatOptions == null) {
      return OutputFormat.COMPACT;
    }
    if (formatOptions.full) {
      return OutputFormat.FULL;
    }
    if (formatOptions.json) {
      return OutputFormat.JSON;
    }
    if (formatOptions.tree) {
      return OutputFormat.TREE;
    }
    // Default to COMPACT (this covers both explicit -c and no option selected)
    return OutputFormat.COMPACT;
  }

  /**
   * Determines whether a message should be printed based on the current filters and offset.
   *
   * @param recOffset the offset of the current record
   * @param key the key associated with the message
   * @param msg the log message to evaluate
   * @return {@code true} if the message meets the criteria and should be printed; {@code false}
   *     otherwise
   */
  boolean shouldPrint(Long recOffset, String key, LogMessage<?> msg) {

    // 1) Filter by msgFormats?
    if (msgFormats != null) {
      String format = getMessageFormat(msg);
      if (format == null || !msgFormats.contains(format)) {
        return false;
      }
    }
    // 2) Filter by msgTypes?
    if (msgTypes != null) {
      String type = getMessageTypeName(msg);
      if (type != null) {
        type = type.substring(5); // remove "EXEC_"
        if (!msgTypes.contains(type)) {
          return false;
        }
      } else {
        return false;
      }
    }
    // 3) fromPeer
    if (fromPeer != null) {
      // the message Key is the peer's UUID
      if (!fromPeer.equalsIgnoreCase(key)) {
        return false;
      }
    }
    // 4) fromThread
    if (threadName != null) {
      if (isColferMessage(msg)) {
        Message m = (Message) msg.getContent();
        if (m != null && m.getExecMessage() != null) {
          String t = m.getExecMessage().getThreadName();
          if (!threadName.equalsIgnoreCase(t)) {
            return false;
          }
        }
      }
    }
    // 5) messageId
    if (id != null) {
      String msgId = getId(msg);
      if (!id.equalsIgnoreCase(msgId)) {
        return false;
      }
    }
    // 6) pattern-based filters
    if (filters != null && !matchesFilters(msg)) {
      return false;
    }
    return true;
  }

  /**
   * Checks if a message matches all configured pattern-based filters.
   *
   * <p>Supported filter keys:
   *
   * <ul>
   *   <li>{@code class} - matches against the fully-qualified class name
   *   <li>{@code method} - matches against the method or field name
   *   <li>{@code field} - matches against the field name (field operations only)
   * </ul>
   *
   * @param msg the log message to check
   * @return {@code true} if the message matches all filters, {@code false} otherwise
   */
  private boolean matchesFilters(LogMessage<?> msg) {
    if (!isColferMessage(msg)) {
      return false;
    }
    Message m = (Message) msg.getContent();
    if (m == null || m.getExecMessage() == null) {
      return false;
    }
    ExecMessage execMsg = m.getExecMessage();
    MessageType msgType = getMessageTypeOf(execMsg);
    if (msgType.getFamily() != MessageFamily.EXEC) {
      return false;
    }
    for (String filter : filters) {
      int eqIdx = filter.indexOf('=');
      if (eqIdx <= 0 || eqIdx >= filter.length() - 1) {
        continue;
      }
      String key = filter.substring(0, eqIdx).trim();
      String value = filter.substring(eqIdx + 1).trim();
      if ("class".equals(key)) {
        String className = getClassname(execMsg);
        if (className == null || !className.contains(value)) {
          return false;
        }
      } else if ("method".equals(key)) {
        String methodName = getExecMethodName(execMsg, msgType);
        if (methodName == null || !methodName.contains(value)) {
          return false;
        }
      } else if ("field".equals(key)) {
        String fieldName = getExecFieldName(execMsg, msgType);
        if (fieldName == null || !fieldName.contains(value)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Extracts the method or field name from an execution message, returning {@code null} for message
   * types that do not have a method/field name (e.g., RETURN_VALUE, THROWABLE).
   *
   * <p>For field operations, delegates to {@link #getExecFieldName(ExecMessage, MessageType)}.
   *
   * @param execMsg the execution message
   * @param msgType the message type
   * @return the method or field name, or {@code null} if not applicable
   */
  static String getExecMethodName(ExecMessage execMsg, MessageType msgType) {
    return switch (msgType) {
      case EXEC_CONSTRUCTOR -> "new";
      case EXEC_INSTANCE_METHOD -> execMsg.getInstanceMethodCall().getName();
      case EXEC_CLASS_METHOD -> execMsg.getClassMethodCall().getName();
      default -> getExecFieldName(execMsg, msgType);
    };
  }

  /**
   * Extracts the field name from an execution message, returning {@code null} for message types
   * that are not field operations (e.g., CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD, RETURN_VALUE,
   * THROWABLE).
   *
   * @param execMsg the execution message
   * @param msgType the message type
   * @return the field name, or {@code null} if not a field operation
   */
  static String getExecFieldName(ExecMessage execMsg, MessageType msgType) {
    return switch (msgType) {
      case EXEC_GET_STATIC -> execMsg.getStaticFieldGet().getField().getName();
      case EXEC_GET_FIELD -> execMsg.getInstanceFieldGet().getField().getName();
      case EXEC_PUT_STATIC -> execMsg.getStaticFieldPut().getField().getName();
      case EXEC_PUT_FIELD -> execMsg.getInstanceFieldPut().getField().getName();
      case EXEC_PUT_STATIC_DONE -> execMsg.getStaticFieldPutDone().getField().getName();
      case EXEC_PUT_FIELD_DONE -> execMsg.getInstanceFieldPutDone().getField().getName();
      default -> null;
    };
  }

  /**
   * Prints a single record to the standard output in the specified format.
   *
   * @param key the key associated with the message
   * @param msg the log message to print
   * @param offset the offset of the message
   */
  void printRecord(String key, LogMessage<?> msg, long offset) {
    switch (getFormat()) {
      case FULL ->
          System.out.printf(
              "CONTEXT: offset: %d key: %s %nHEADERS: %s%n%s%n",
              offset, key, msg.getHeaders(), getMessageContentAsPrettyJson(msg));
      case JSON ->
          System.out.printf("offset: %d,%n%s%n", offset, getMessageContentAsPrettyJson(msg));
      case COMPACT ->
          System.out.printf(
              "offset=%d id=%s message=%s%n", offset, getId(msg), getMessageOneLiner(msg));
      case TREE -> printTreeRecord(msg, offset);
      default -> throw new IllegalStateException("Unexpected value: " + getFormat());
    }
  }

  /**
   * Prints a single record to the standard output without a key (for Chronicle messages).
   *
   * @param msg the log message to print
   * @param offset the offset/index of the message
   */
  void printRecord(LogMessage<?> msg, long offset) {
    printRecord(null, msg, offset);
  }

  /**
   * Prints a single record in TREE format with indentation based on nesting depth.
   *
   * <p>Operations (constructor calls, method calls, field access) increase depth, while return
   * values and exceptions decrease depth. This produces an indented tree view showing the nested
   * call structure.
   *
   * @param msg the log message to print
   * @param offset the offset of the message
   */
  void printTreeRecord(LogMessage<?> msg, long offset) {
    if (!isColferMessage(msg)) {
      System.out.printf("%s[%d] %s%n", indent(treeDepth), offset, getMessageOneLiner(msg));
      return;
    }

    Message m = (Message) msg.getContent();
    if (m == null || m.getExecMessage() == null) {
      System.out.printf("%s[%d] %s%n", indent(treeDepth), offset, getMessageOneLiner(msg));
      return;
    }

    MessageType msgType = MessageType.fromId(m.getMessageType());
    boolean isCompletion =
        msgType == MessageType.EXEC_RETURN_VALUE
            || msgType == MessageType.EXEC_THROWABLE
            || msgType == MessageType.EXEC_PUT_STATIC_DONE
            || msgType == MessageType.EXEC_PUT_FIELD_DONE;

    if (isCompletion && treeDepth > 0) {
      treeDepth--;
    }

    String summary = getMessageOneLiner(msg);
    System.out.printf("%s[%d] %s%n", indent(treeDepth), offset, summary);

    if (!isCompletion) {
      treeDepth++;
    }
  }

  /**
   * Creates an indentation string for the given depth level in tree output.
   *
   * @param depth the nesting depth
   * @return a string of spaces for indentation
   */
  static String indent(int depth) {
    if (depth <= 0) {
      return "";
    }
    return "  ".repeat(depth);
  }

  /**
   * Checks whether the given message is a return/done type (RETURN_VALUE, THROWABLE,
   * PUT_STATIC_DONE, or PUT_FIELD_DONE).
   *
   * <p>Used by the {@code --with-return} option to track nesting depth: operation messages increase
   * depth, return/done messages decrease it. When depth reaches zero, the matching return has been
   * found.
   *
   * @param msg the log message to check
   * @return {@code true} if the message is a return or done type
   */
  static boolean isReturnType(LogMessage<?> msg) {
    if (isColferMessage(msg)) {
      Message m = (Message) msg.getContent();
      if (m != null) {
        MessageType msgType = MessageType.fromId(m.getMessageType());
        return msgType == MessageType.EXEC_RETURN_VALUE
            || msgType == MessageType.EXEC_THROWABLE
            || msgType == MessageType.EXEC_PUT_STATIC_DONE
            || msgType == MessageType.EXEC_PUT_FIELD_DONE;
      }
    }
    return false;
  }

  /**
   * Prints the verbose header line and common filter details used in both Kafka and Chronicle
   * paths.
   *
   * @param headerLine the first line describing the source/context (already formatted)
   * @param offsetDescriptor the label to use for the offset line (e.g., "offset id",
   *     "offset/index")
   * @param offset the offset value to print (may be null)
   * @param withReturn whether the --with-return flag is set
   */
  void printVerboseFilters(
      String headerLine, String offsetDescriptor, Long offset, boolean withReturn) {
    System.out.println(headerLine);
    if (msgFormats != null) {
      System.out.printf("Filtering by format(s): %s%n", String.join(",", msgFormats));
    }
    if (msgTypes != null) {
      System.out.printf("Filtering by type(s): %s%n", String.join(",", msgTypes));
    }
    if (fromPeer != null) {
      System.out.printf("Filtering by peer: %s%n", fromPeer);
    }
    if (threadName != null) {
      System.out.printf("Filtering by thread: %s%n", threadName);
    }
    if (id != null) {
      System.out.printf("Filtering by message id: %s%n", id);
    }
    if (filters != null) {
      System.out.printf("Filtering by pattern(s): %s%n", String.join(",", filters));
    }
    if (offset != null) {
      System.out.printf("Will print message with %s: %s and then exit%n", offsetDescriptor, offset);
      if (withReturn) {
        System.out.println("Will also show return value for the operation");
      }
    }
  }

  /**
   * Closes resources. Overridden to handle the case where no directory connection was established,
   * which occurs when print commands are used without a PAL directory (e.g., direct Chronicle or
   * Kafka mode).
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    if (directoryConnectionProvider != null) {
      super.closeResources();
    }
  }
}
