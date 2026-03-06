/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.replay;

import java.util.concurrent.CountDownLatch;

/**
 * Test application that creates objects via a factory class, then calls methods on them.
 *
 * <p>Used by {@code PhantomCascadeReplayIT} to verify phantom object cascading: when the factory
 * constructor or creation method is stubbed, the resulting object becomes a phantom. All subsequent
 * method calls and field accesses on that phantom are automatically stubbed from the WAL.
 *
 * <p>The {@link DataService} class is the "stubbable" part — its constructor and methods should be
 * stubbed during replay. The main application logic in {@link #main(String[])} should be
 * re-executed.
 *
 * <p>Supports a 2-thread RPC variant: launch with {@code "service"} argument and call {@link
 * #queryViaService(String[])} via RPC. Call {@link #shutdown(String[])} to release the latch.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * // Single-thread
 * ObjectCreatorApp.main(new String[]{"hello"});
 * // Output: "ObjectCreator: query=HELLO transform=hello_transformed length=5"
 * }</pre>
 */
public class ObjectCreatorApp {

  /**
   * Latch that keeps the peer alive in service mode until {@link #shutdown(String[])} is called.
   */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * A service class whose constructor and methods should be stubbed during phantom cascade replay.
   *
   * <p>Instances created by the constructor are registered as phantoms when the constructor is
   * stubbed. All subsequent method calls on the phantom are auto-stubbed from the WAL.
   */
  public static class DataService {

    /** The prefix used by this service instance. */
    private final String prefix;

    /**
     * Creates a new data service with the given prefix.
     *
     * @param prefix the prefix to use in query and transform operations
     */
    public DataService(String prefix) {
      this.prefix = prefix;
    }

    /**
     * Queries data by converting the input to uppercase and prepending the prefix.
     *
     * @param input the input string
     * @return the uppercased input
     */
    public String query(String input) {
      return input.toUpperCase();
    }

    /**
     * Transforms the input by appending a suffix.
     *
     * @param input the input string
     * @return the transformed string
     */
    public String transform(String input) {
      return input + "_transformed";
    }

    /**
     * Returns the length of the input string.
     *
     * @param input the input string
     * @return the length
     */
    public int length(String input) {
      return input.length();
    }
  }

  /**
   * Entry point that creates a {@link DataService} and calls methods on it.
   *
   * <p>If the first argument is {@code "service"}, waits for RPC calls. Otherwise, performs
   * operations using the first argument as input.
   *
   * @param args command-line arguments; first arg is "service" for RPC mode, or input data
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "service".equals(args[0])) {
      SHUTDOWN_LATCH.await();
      return;
    }

    String input = args.length > 0 ? args[0] : "default";
    DataService service = new DataService("svc");
    String queryResult = service.query(input);
    String transformResult = service.transform(input);
    int lengthResult = service.length(input);

    System.out.println(
        "ObjectCreator: query="
            + queryResult
            + " transform="
            + transformResult
            + " length="
            + lengthResult);
  }

  /**
   * RPC-callable method that creates a DataService and queries it.
   *
   * <p>Called via {@code pal call -m queryViaService} in multi-threaded tests.
   *
   * @param args single-element array with the input string
   * @return the query result from the DataService
   */
  public static String queryViaService(String[] args) {
    String input = args.length > 0 ? args[0] : "default";
    DataService service = new DataService("rpc");
    String queryResult = service.query(input);
    String transformResult = service.transform(input);
    return "query=" + queryResult + " transform=" + transformResult;
  }

  /**
   * Signals the peer to shut down by releasing the latch in {@link #main(String[])}.
   *
   * @param args ignored (required for {@code pal call -m} compatibility)
   */
  public static void shutdown(String[] args) {
    SHUTDOWN_LATCH.countDown();
  }
}
