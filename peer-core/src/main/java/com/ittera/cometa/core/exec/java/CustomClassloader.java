package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.core.InterceptProcessor;

import java.net.URL;
import java.net.URLClassLoader;

public class CustomClassloader extends URLClassLoader {

	private InterceptProcessor interceptProcessor;

	public CustomClassloader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class clazz = super.findClass(name);
		interceptProcessor.process(clazz);
		return clazz;
	}

	public void setInterceptProcessor(InterceptProcessor interceptProcessor) {
		this.interceptProcessor = interceptProcessor;
	}
}
