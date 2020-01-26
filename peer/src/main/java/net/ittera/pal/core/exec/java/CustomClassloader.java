package net.ittera.pal.core.exec.java;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import net.ittera.pal.core.InterceptProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomClassloader extends URLClassLoader {

  private static final Logger logger = LoggerFactory.getLogger(CustomClassloader.class);
  private InterceptProcessor interceptProcessor;

  public CustomClassloader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
    logger.info("Initialized custom classloader with paths: {}", Arrays.toString(urls));
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    Class clazz = super.findClass(name);
    interceptProcessor.process(clazz);
    return clazz;
  }

  public void setInterceptProcessor(InterceptProcessor interceptProcessor) {
    this.interceptProcessor = interceptProcessor;
  }
}
