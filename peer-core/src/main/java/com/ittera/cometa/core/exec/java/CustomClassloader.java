package com.ittera.cometa.core.exec.java;

import java.net.URL;
import java.net.URLClassLoader;

public class CustomClassloader extends URLClassLoader {
	public CustomClassloader(URL[] urls) {
		super(urls);
	}
	public CustomClassloader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}
}
