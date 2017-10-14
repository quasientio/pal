package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.Future;

public interface LogExecutor {
    Future<?> submit(Runnable task);
}
