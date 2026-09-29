package com.uddoktahisab.app.di;

import com.google.gson.Gson;
import com.uddoktahisab.app.data.remote.ApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ApiFactory implements Factory<ApiService> {
  private final Provider<Gson> gsonProvider;

  public AppModule_ApiFactory(Provider<Gson> gsonProvider) {
    this.gsonProvider = gsonProvider;
  }

  @Override
  public ApiService get() {
    return api(gsonProvider.get());
  }

  public static AppModule_ApiFactory create(Provider<Gson> gsonProvider) {
    return new AppModule_ApiFactory(gsonProvider);
  }

  public static ApiService api(Gson gson) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.api(gson));
  }
}
