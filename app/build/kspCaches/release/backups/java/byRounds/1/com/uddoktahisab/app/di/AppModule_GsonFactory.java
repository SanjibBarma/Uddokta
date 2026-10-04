package com.uddoktahisab.app.di;

import com.google.gson.Gson;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AppModule_GsonFactory implements Factory<Gson> {
  @Override
  public Gson get() {
    return gson();
  }

  public static AppModule_GsonFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Gson gson() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.gson());
  }

  private static final class InstanceHolder {
    private static final AppModule_GsonFactory INSTANCE = new AppModule_GsonFactory();
  }
}
