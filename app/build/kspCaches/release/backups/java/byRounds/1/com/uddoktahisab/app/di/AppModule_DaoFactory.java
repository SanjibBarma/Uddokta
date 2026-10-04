package com.uddoktahisab.app.di;

import com.uddoktahisab.app.data.local.db.AppDatabase;
import com.uddoktahisab.app.data.local.db.LocalDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_DaoFactory implements Factory<LocalDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_DaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public LocalDao get() {
    return dao(dbProvider.get());
  }

  public static AppModule_DaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_DaoFactory(dbProvider);
  }

  public static LocalDao dao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.dao(db));
  }
}
