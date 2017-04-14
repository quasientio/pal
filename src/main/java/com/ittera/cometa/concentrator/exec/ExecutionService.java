package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.Future;

public interface ExecutionService {

    Future<?> submit(Runnable task);

    void startCoreThreads();

}
