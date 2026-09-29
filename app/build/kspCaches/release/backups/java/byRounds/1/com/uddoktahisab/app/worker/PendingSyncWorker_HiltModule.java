package com.uddoktahisab.app.worker;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = PendingSyncWorker.class
)
public interface PendingSyncWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.uddoktahisab.app.worker.PendingSyncWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(PendingSyncWorker_AssistedFactory factory);
}
