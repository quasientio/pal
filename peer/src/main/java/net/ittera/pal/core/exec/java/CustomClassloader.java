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

package net.ittera.pal.core.exec.java;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
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
  private final List<ClassLoaderListener> listeners = new ArrayList<>();
  private final ExecutorService executorService;
  private static final int TIMEOUT_MS = 100;
  private static final String ASYNC_THREAD_NAME = "CustomClassLoader-Async-Thread";
  private static final Logger logger = LoggerFactory.getLogger(CustomClassloader.class);

  public CustomClassloader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
    executorService =
        Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, ASYNC_THREAD_NAME));
    logger.info(
        "Custom class loader created with parent: {}, and URLs: {} ",
        parent,
        Arrays.stream(urls).map(URL::toString).collect(Collectors.joining(",")));
  }

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

      classLoadedFuture.thenRunAsync(
          () -> notifyListeners(classLoadedFuture.join()), executorService);

      return classLoadedFuture.join();
    }
  }

  public void addClassLoadListener(ClassLoaderListener listener) {
    listeners.add(listener);
  }

  private void notifyListeners(Class<?> clazz) {
    for (ClassLoaderListener listener : listeners) {
      listener.classLoaded(clazz);
    }
  }

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
