package com.ittera.cometa.concentrator;

import junit.framework.TestCase;

import java.util.Random;


public class WeakValuedCacheTest extends TestCase {

  WeakValuedCache<Long, Object> objectCache;
  Random rand = new Random();

  public WeakValuedCacheTest() {

  }

  private void addStrings(int numberOfStrings) {
    for (int i = 0; i < numberOfStrings; i++) {
      Long now = System.currentTimeMillis();
      Long key = rand.nextLong();
      String anObj = new StringBuilder("Obj created @").append(now).toString();
      objectCache.put(key, anObj);
    }
  }

  public void testCaching() throws InterruptedException {
    objectCache = new WeakValuedCache<>();

    //add some objects
    for (int i = 0; i < 5; i++) {
      addStrings(10);
      Thread.sleep(100);
      System.gc();
      System.out.println("Objects in: values map=" + objectCache.values.size() + " keys map:" + objectCache.keys.size());
    }

    System.out.println("Done adding!");

    //now just sleep without adding
    for (int i = 0; i < 5; i++) {
      Thread.sleep(100);
      System.out.println("Objects in: values map=" + objectCache.values.size() + " keys map:" + objectCache.keys.size());
    }

  }
}
