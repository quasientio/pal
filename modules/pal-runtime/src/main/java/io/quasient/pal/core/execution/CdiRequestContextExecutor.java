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
package io.quasient.pal.core.execution;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor that wraps invocations in a CDI request context and transaction.
 *
 * <p>Uses reflection exclusively to access CDI and transaction classes at runtime, avoiding a
 * compile-time dependency on any specific CDI implementation. Works with any CDI 2.0+ container
 * (Quarkus ArC, Weld, OpenWebBeans) that provides {@code jakarta.enterprise.inject.spi.CDI} and
 * {@code jakarta.enterprise.context.control.RequestContextController}.
 *
 * <p>Transactions are managed via the standard {@code jakarta.transaction.UserTransaction} API when
 * available. If no transaction manager is present, only the CDI request context is activated.
 */
public final class CdiRequestContextExecutor implements InvocationExecutor {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(CdiRequestContextExecutor.class);

  /** Cached reference to CDI.current(). */
  private final Method cdiCurrentMethod;

  /** Cached reference to CDI.select(Class). */
  private final Method cdiSelectMethod;

  /** Cached reference to Instance.get(). */
  private final Method instanceGetMethod;

  /** The RequestContextController class. */
  private final Class<?> requestContextControllerClass;

  /** Cached reference to RequestContextController.activate(). */
  private final Method activateMethod;

  /** Cached reference to RequestContextController.deactivate(). */
  private final Method deactivateMethod;

  /** The UserTransaction class, or null if not available. */
  private final Class<?> userTransactionClass;

  /** Cached reference to UserTransaction.begin(), or null. */
  private final Method txBeginMethod;

  /** Cached reference to UserTransaction.commit(), or null. */
  private final Method txCommitMethod;

  /** Cached reference to UserTransaction.rollback(), or null. */
  private final Method txRollbackMethod;

  /**
   * Creates a new executor using the given classloader to locate CDI classes.
   *
   * @param classLoader the classloader to use for loading CDI and transaction classes
   * @throws IllegalStateException if CDI is not on the classpath
   */
  public CdiRequestContextExecutor(ClassLoader classLoader) {
    try {
      // CDI.current() → CDI<Object>
      Class<?> cdiClass = Class.forName("jakarta.enterprise.inject.spi.CDI", true, classLoader);
      this.cdiCurrentMethod = cdiClass.getMethod("current");

      // CDI.select(Class) → Instance<T>
      this.cdiSelectMethod =
          cdiClass.getMethod("select", Class.class, java.lang.annotation.Annotation[].class);

      // Instance.get() → T
      Class<?> instanceClass =
          Class.forName("jakarta.enterprise.inject.Instance", true, classLoader);
      this.instanceGetMethod = instanceClass.getMethod("get");

      // RequestContextController
      this.requestContextControllerClass =
          Class.forName(
              "jakarta.enterprise.context.control.RequestContextController", true, classLoader);
      this.activateMethod = requestContextControllerClass.getMethod("activate");
      this.deactivateMethod = requestContextControllerClass.getMethod("deactivate");

    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "CDI not on classpath. Cannot use --service-thread without a CDI container.", e);
    }

    // UserTransaction is optional (not all apps use JTA)
    Method begin = null;
    Method commit = null;
    Method rollback = null;
    Class<?> utClass = null;
    try {
      utClass = Class.forName("jakarta.transaction.UserTransaction", true, classLoader);
      begin = utClass.getMethod("begin");
      commit = utClass.getMethod("commit");
      rollback = utClass.getMethod("rollback");
    } catch (ReflectiveOperationException e) {
      logger.info("UserTransaction not available — replay will run without transaction wrapping");
    }
    this.userTransactionClass = utClass;
    this.txBeginMethod = begin;
    this.txCommitMethod = commit;
    this.txRollbackMethod = rollback;
  }

  @Override
  public Object execute(Callable<Object> invocation) throws Exception {
    Object requestContextController = lookupCdiBean(requestContextControllerClass);
    activateMethod.invoke(requestContextController);
    try {
      Object userTransaction =
          (userTransactionClass != null) ? lookupCdiBean(userTransactionClass) : null;
      if (userTransaction != null) {
        txBeginMethod.invoke(userTransaction);
      }
      try {
        Object result = invocation.call();
        if (userTransaction != null) {
          txCommitMethod.invoke(userTransaction);
        }
        return result;
      } catch (Exception e) {
        if (userTransaction != null) {
          try {
            txRollbackMethod.invoke(userTransaction);
          } catch (Exception rollbackEx) {
            e.addSuppressed(rollbackEx);
          }
        }
        throw e;
      }
    } finally {
      deactivateMethod.invoke(requestContextController);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Activates the CDI request context only (no JTA transaction) and looks up the bean via {@code
   * CDI.current().select(declaringType).get()}. JTA is intentionally bypassed here — this method is
   * invoked by the replay dispatcher purely to resolve a managed-bean target before the main
   * invocation runs. The main invocation later goes through {@link #execute}, which starts a fresh
   * transaction; starting one here would trigger a {@code begin()} that JTA does not support
   * re-entering.
   *
   * <p>Returns {@code null} if the CDI container is not available, the request context cannot be
   * activated, or the bean cannot be resolved.
   */
  @Override
  public Object resolveTarget(Class<?> declaringType) {
    Object requestContextController;
    try {
      requestContextController = lookupCdiBean(requestContextControllerClass);
      activateMethod.invoke(requestContextController);
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "CDI request context activation failed while resolving {}: {}",
            declaringType.getName(),
            e.getMessage());
      }
      return null;
    }
    try {
      return lookupCdiBean(declaringType);
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "CDI target resolution failed for {}: {}", declaringType.getName(), e.getMessage());
      }
      return null;
    } finally {
      try {
        deactivateMethod.invoke(requestContextController);
      } catch (Exception e) {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "CDI request context deactivation failed after resolving {}: {}",
              declaringType.getName(),
              e.getMessage());
        }
      }
    }
  }

  /**
   * Looks up a CDI bean by type via {@code CDI.current().select(type).get()}.
   *
   * @param beanType the class to look up
   * @return the CDI bean instance
   * @throws Exception if lookup fails
   */
  private Object lookupCdiBean(Class<?> beanType) throws Exception {
    Object cdi = cdiCurrentMethod.invoke(null);
    Object instance = cdiSelectMethod.invoke(cdi, beanType, new java.lang.annotation.Annotation[0]);
    return instanceGetMethod.invoke(instance);
  }
}
