package com.ittera.cometa.cxn;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class DataMessageFuture implements Future<DataMessage> {
  private final CountDownLatch latch = new CountDownLatch(1);
  private DataMessage value;

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    //TODO (shouldn't we countDown and return true?)
    return false;
  }

  @Override
  public boolean isDone() {
    return latch.getCount() == 0;
  }

  @Override
  public DataMessage get() throws InterruptedException {
    latch.await();
    return value;
  }

  @Override
  public DataMessage get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    if (latch.await(timeout, unit)) {
      return value;
    } else {
      throw new TimeoutException();
    }
  }

  /**
   * To be called just once
   * @param result
   */
  void put(DataMessage result) {
    value = result;
    latch.countDown();
  }
}
