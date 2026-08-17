package com.uddoktahisab.app;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class UddoktaApp_MembersInjector implements MembersInjector<UddoktaApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public UddoktaApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<UddoktaApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new UddoktaApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(UddoktaApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.uddoktahisab.app.UddoktaApp.workerFactory")
  public static void injectWorkerFactory(UddoktaApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
