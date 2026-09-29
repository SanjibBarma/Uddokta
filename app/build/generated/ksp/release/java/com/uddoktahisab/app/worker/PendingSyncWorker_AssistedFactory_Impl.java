package com.uddoktahisab.app.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class PendingSyncWorker_AssistedFactory_Impl implements PendingSyncWorker_AssistedFactory {
  private final PendingSyncWorker_Factory delegateFactory;

  PendingSyncWorker_AssistedFactory_Impl(PendingSyncWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public PendingSyncWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<PendingSyncWorker_AssistedFactory> create(
      PendingSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PendingSyncWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<PendingSyncWorker_AssistedFactory> createFactoryProvider(
      PendingSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PendingSyncWorker_AssistedFactory_Impl(delegateFactory));
  }
}
