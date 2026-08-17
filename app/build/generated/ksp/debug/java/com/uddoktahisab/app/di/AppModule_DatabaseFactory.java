package com.uddoktahisab.app.di;

import android.content.Context;
import com.uddoktahisab.app.data.local.db.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_DatabaseFactory implements Factory<AppDatabase> {
  private final Provider<Context> contextProvider;

  public AppModule_DatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AppDatabase get() {
    return database(contextProvider.get());
  }

  public static AppModule_DatabaseFactory create(Provider<Context> contextProvider) {
    return new AppModule_DatabaseFactory(contextProvider);
  }

  public static AppDatabase database(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.database(context));
  }
}
