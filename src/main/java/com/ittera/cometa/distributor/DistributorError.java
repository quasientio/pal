package com.ittera.cometa.distributor;

class DistributorError extends Error {
  public DistributorError() {
    super();
  }

  public DistributorError(String message) {
    super(message);
  }

  public DistributorError(Throwable cause) {
    super(cause);
  }

  public DistributorError(String message, Throwable cause) {
    super(message, cause);
  }
}
