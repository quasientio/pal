package com.ittera.cometa.concentrator;

import junit.framework.TestCase;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Random;


public class WeakValuedCacheTest extends TestCase {

  protected final static Logger logger = LogManager.getLogger("tests");

  private WeakValuedCache<Long, Object> objectCache;
  private Random rand = new Random();

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
      logger.info("Objects in: values map=" + objectCache.values.size() + " keys map:" + objectCache.keys.size());
    }

    logger.info("Done adding!");

    //now just sleep without adding
    for (int i = 0; i < 5; i++) {
      Thread.sleep(100);
      logger.info("Objects in: values map=" + objectCache.values.size() + " keys map:" + objectCache.keys.size());
    }

  }
}
