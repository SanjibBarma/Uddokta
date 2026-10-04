package com.uddoktahisab.app.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.uddoktahisab.app.data.repository.AppRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class PendingSyncWorker_Factory {
  private final Provider<AppRepository> repoProvider;

  public PendingSyncWorker_Factory(Provider<AppRepository> repoProvider) {
    this.repoProvider = repoProvider;
  }

  public PendingSyncWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, repoProvider.get());
  }

  public static PendingSyncWorker_Factory create(Provider<AppRepository> repoProvider) {
    return new PendingSyncWorker_Factory(repoProvider);
  }

  public static PendingSyncWorker newInstance(Context context, WorkerParameters params,
      AppRepository repo) {
    return new PendingSyncWorker(context, params, repo);
  }
}
