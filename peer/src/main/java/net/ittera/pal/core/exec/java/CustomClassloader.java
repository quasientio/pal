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
import java.util.Arrays;
import net.ittera.pal.core.InterceptAnnotationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomClassloader extends URLClassLoader {

  private static final Logger logger = LoggerFactory.getLogger(CustomClassloader.class);
  private InterceptAnnotationProcessor interceptAnnotationProcessor;

  public CustomClassloader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
    logger.info("Initialized custom classloader with paths: {}", Arrays.toString(urls));
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    Class clazz = super.findClass(name);
    interceptAnnotationProcessor.process(clazz);
    return clazz;
  }

  public void setInterceptProcessor(InterceptAnnotationProcessor interceptAnnotationProcessor) {
    this.interceptAnnotationProcessor = interceptAnnotationProcessor;
  }
}
