package net.ittera.pal.core.rpc.exec.java;

/**
 * Interface for receiving notifications when a class is loaded.
 *
 * <p>This interface should be implemented by classes that need to take action immediately after a
 * class is loaded, such as for logging, monitoring, or further processing of the loaded class.
 */
public interface ClassLoaderListener {

  /**
   * Invoked when a class has been successfully loaded by the class loader.
   *
   * <p>This method acts as a callback, providing the loaded class to the listener so that it may
   * perform operations such as inspection or registration. The parameter should not be null.
   *
   * @param clazz the class that has been loaded
   */
  void classLoaded(Class<?> clazz);
}
