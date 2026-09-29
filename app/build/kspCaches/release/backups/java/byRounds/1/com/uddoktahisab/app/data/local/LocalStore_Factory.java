package com.uddoktahisab.app.data.local;

import com.google.gson.Gson;
import com.uddoktahisab.app.data.local.db.LocalDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class LocalStore_Factory implements Factory<LocalStore> {
  private final Provider<LocalDao> daoProvider;

  private final Provider<Gson> gsonProvider;

  public LocalStore_Factory(Provider<LocalDao> daoProvider, Provider<Gson> gsonProvider) {
    this.daoProvider = daoProvider;
    this.gsonProvider = gsonProvider;
  }

  @Override
  public LocalStore get() {
    return newInstance(daoProvider.get(), gsonProvider.get());
  }

  public static LocalStore_Factory create(Provider<LocalDao> daoProvider,
      Provider<Gson> gsonProvider) {
    return new LocalStore_Factory(daoProvider, gsonProvider);
  }

  public static LocalStore newInstance(LocalDao dao, Gson gson) {
    return new LocalStore(dao, gson);
  }
}
