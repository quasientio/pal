/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class loader that notifies ClassLoaderListeners when a class is loaded. The notification is done
 * asynchronously using CompletableFuture and a thread pool. It synchronizes the completion of class
 * loading with the start of the annotation processing (or whatever else task listeners may do)
 * while still leveraging the benefits of asynchronous execution for the listener processing step.
 * The use of join() here is crucial for maintaining the correct order of operations in a concurrent
 * and asynchronous environment.
 */
public class CustomClassloader extends URLClassLoader {
  /** List of registered listeners to be notified after a class is loaded. */
  private final List<ClassLoaderListener> listeners = new ArrayList<>();

  /** ExecutorService responsible for executing listener callbacks asynchronously. */
  private final ExecutorService executorService;

  /** Timeout, in milliseconds, for waiting for the executor service termination during shutdown. */
  private static final int TIMEOUT_MS = 100;

  /** The name assigned to the thread used by the asynchronous executor. */
  private static final String ASYNC_THREAD_NAME = "CustomClassLoader-Async-Thread";

  /** Logger for recording class loading activities and errors. */
  private static final Logger logger = LoggerFactory.getLogger(CustomClassloader.class);

  /**
   * Constructs a new CustomClassloader using the specified URL array and parent class loader.
   *
   * <p>This constructor initializes a single-thread executor for asynchronous processing of class
   * load notifications. It also logs the initial configuration including the parent class loader
   * and the URLs.
   *
   * @param urls array of URLs from which classes and resources will be loaded; must not be null
   * @param parent the parent class loader to delegate to if necessary; must not be null
   */
  public CustomClassloader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
    executorService =
        Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, ASYNC_THREAD_NAME));
    logger.info(
        "Custom class loader created with parent: {}, and URLs: {} ",
        parent,
        Arrays.stream(urls).map(URL::toString).collect(Collectors.joining(",")));
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method attempts to load the class with the specified binary name. It first checks if
   * the class was already loaded. If not, it delegates loading to the parent class loader and falls
   * back to its own {@code findClass} implementation if needed. Once loaded (and optionally
   * resolved), it asynchronously notifies all registered {@code ClassLoaderListeners} about the new
   * class.
   *
   * @param name the binary name of the class
   * @param resolve if {@code true} then resolve the class
   * @return the resulting {@code Class} object
   * @throws ClassNotFoundException if the class could not be located by both the parent and custom
   *     loader
   */
  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    CompletableFuture<Class<?>> classLoadedFuture = new CompletableFuture<>();

    synchronized (getClassLoadingLock(name)) {
      Class<?> clazz = findLoadedClass(name);
      if (clazz != null && logger.isTraceEnabled()) {
        logger.trace("Found cached class {}", name);
      }

      if (clazz == null) {
        try {
          clazz = getParent().loadClass(name);
          if (logger.isTraceEnabled()) {
            logger.trace("Loaded class {} using parent class loader", name);
          }
        } catch (ClassNotFoundException e) {
          clazz = findClass(name);
          if (logger.isTraceEnabled()) {
            logger.trace("Loaded class {} using custom class loader", name);
          }
        }
      }

      if (resolve) {
        resolveClass(clazz);
      }

      classLoadedFuture.complete(clazz);

      CompletableFuture<?> notifyFuture =
          classLoadedFuture.thenRunAsync(
              () -> notifyListeners(classLoadedFuture.join()), executorService);

      if (notifyFuture.isCompletedExceptionally()) {
        logger.error("Failed to notify listeners for class {}", name);
      }

      return classLoadedFuture.join();
    }
  }

  /**
   * Registers a {@code ClassLoaderListener} to receive notifications after successful class
   * loading.
   *
   * <p>Registered listeners can perform additional processing (such as annotation handling) once
   * the class has been loaded.
   *
   * @param listener the listener instance to register; must not be null
   */
  public void addClassLoadListener(ClassLoaderListener listener) {
    listeners.add(listener);
  }

  /**
   * Notifies all registered {@code ClassLoaderListeners} that the specified class has been loaded.
   *
   * <p>This method is called as part of an asynchronous callback once the class loading is
   * complete.
   *
   * @param clazz the {@code Class} object that was loaded; expected to be non-null
   */
  private void notifyListeners(Class<?> clazz) {
    for (ClassLoaderListener listener : listeners) {
      listener.classLoaded(clazz);
    }
  }

  /**
   * Shuts down the asynchronous executor service used for notifying listeners.
   *
   * <p>The shutdown process first attempts a graceful termination within a timeout of {@code
   * TIMEOUT_MS} milliseconds. If the executor does not terminate in time or if interrupted, a
   * forced shutdown is initiated.
   */
  public void shutdown() {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
    }
  }
}
