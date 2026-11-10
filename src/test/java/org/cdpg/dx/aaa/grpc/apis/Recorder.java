package org.cdpg.dx.aaa.grpc.apis;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Captures one unary gRPC answer: a response or a status. */
final class Recorder<T> implements StreamObserver<T> {

  private final CompletableFuture<T> result = new CompletableFuture<>();

  @Override
  public void onNext(T value) {
    result.complete(value);
  }

  @Override
  public void onError(Throwable t) {
    result.completeExceptionally(t);
  }

  @Override
  public void onCompleted() {}

  T value() throws Exception {
    return result.get(5, TimeUnit.SECONDS);
  }

  Status.Code code() throws Exception {
    try {
      result.get(5, TimeUnit.SECONDS);
      return Status.Code.OK;
    } catch (java.util.concurrent.ExecutionException e) {
      return Status.fromThrowable(e.getCause()).getCode();
    }
  }
}
