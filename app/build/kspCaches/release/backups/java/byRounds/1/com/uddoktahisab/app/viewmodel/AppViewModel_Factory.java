package com.uddoktahisab.app.viewmodel;

import com.uddoktahisab.app.data.repository.AppRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AppViewModel_Factory implements Factory<AppViewModel> {
  private final Provider<AppRepository> repoProvider;

  public AppViewModel_Factory(Provider<AppRepository> repoProvider) {
    this.repoProvider = repoProvider;
  }

  @Override
  public AppViewModel get() {
    return newInstance(repoProvider.get());
  }

  public static AppViewModel_Factory create(Provider<AppRepository> repoProvider) {
    return new AppViewModel_Factory(repoProvider);
  }

  public static AppViewModel newInstance(AppRepository repo) {
    return new AppViewModel(repo);
  }
}
