package com.uddoktahisab.app.data.remote;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class FirebaseDataSource_Factory implements Factory<FirebaseDataSource> {
  private final Provider<Context> contextProvider;

  public FirebaseDataSource_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public FirebaseDataSource get() {
    return newInstance(contextProvider.get());
  }

  public static FirebaseDataSource_Factory create(Provider<Context> contextProvider) {
    return new FirebaseDataSource_Factory(contextProvider);
  }

  public static FirebaseDataSource newInstance(Context context) {
    return new FirebaseDataSource(context);
  }
}
