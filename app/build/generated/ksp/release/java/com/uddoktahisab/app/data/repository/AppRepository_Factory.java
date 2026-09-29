package com.uddoktahisab.app.data.repository;

import android.content.Context;
import com.uddoktahisab.app.data.local.LocalStore;
import com.uddoktahisab.app.data.local.NetworkMonitor;
import com.uddoktahisab.app.data.local.SessionManager;
import com.uddoktahisab.app.data.remote.ApiService;
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
public final class AppRepository_Factory implements Factory<AppRepository> {
  private final Provider<ApiService> apiProvider;

  private final Provider<SessionManager> sessionProvider;

  private final Provider<LocalStore> localProvider;

  private final Provider<NetworkMonitor> networkProvider;

  private final Provider<Context> contextProvider;

  public AppRepository_Factory(Provider<ApiService> apiProvider,
      Provider<SessionManager> sessionProvider, Provider<LocalStore> localProvider,
      Provider<NetworkMonitor> networkProvider, Provider<Context> contextProvider) {
    this.apiProvider = apiProvider;
    this.sessionProvider = sessionProvider;
    this.localProvider = localProvider;
    this.networkProvider = networkProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public AppRepository get() {
    return newInstance(apiProvider.get(), sessionProvider.get(), localProvider.get(), networkProvider.get(), contextProvider.get());
  }

  public static AppRepository_Factory create(Provider<ApiService> apiProvider,
      Provider<SessionManager> sessionProvider, Provider<LocalStore> localProvider,
      Provider<NetworkMonitor> networkProvider, Provider<Context> contextProvider) {
    return new AppRepository_Factory(apiProvider, sessionProvider, localProvider, networkProvider, contextProvider);
  }

  public static AppRepository newInstance(ApiService api, SessionManager session, LocalStore local,
      NetworkMonitor network, Context context) {
    return new AppRepository(api, session, local, network, context);
  }
}
