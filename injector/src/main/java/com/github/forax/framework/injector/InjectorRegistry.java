package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {

  // Pas de Map<K,V> car le champ est privé :)
  private final HashMap<Class<?>, Supplier<?>> registry = new HashMap<>();

  static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        // .filter(property -> !property.getName().equals("class"))
        .filter(property -> {
          var setter = property.getWriteMethod();
          if (setter == null) return false;
          return setter.isAnnotationPresent(Inject.class);
        })
        .toList();
  }

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    registerProvider(type, () -> instance);
  }

  private Constructor<?> findInjectableConstructor(Class<?> type) {
    var constructors = Arrays.stream(type.getConstructors())
      .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
      .toList();
    return switch (constructors.size()) {
      case 0 -> Utils.defaultConstructor(type);
      case 1 -> constructors.getFirst();
      default -> throw new IllegalStateException("multiple constructors annotated with @Inject");
    };
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);
    var constructor = findInjectableConstructor(providerClass);
    var injectableProperties = findInjectableProperties(providerClass);
    var parametersTypes = constructor.getParameterTypes();
    registerProvider(type, () -> {
      var args = Arrays.stream(parametersTypes)
          .map(this::lookupInstance)
          .toArray();
      var instance = Utils.newInstance(constructor, args);
      for (var property: injectableProperties) {
        var propertyType = property.getPropertyType();
        var value = lookupInstance(propertyType);
        Utils.invokeMethod(instance, property.getWriteMethod(), value);
      }
      return providerClass.cast(instance);
    });
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> provider) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(provider);
    var oldValue = registry.putIfAbsent(type, provider);
    if (oldValue != null) throw new IllegalStateException("injector for type " + type + " already exists");
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var result = registry.get(type);
    if (result == null) throw new IllegalStateException("injector for type " + type + " does not exist");
    return type.cast(result.get());
  }

  public void registerProviderClass(Class<?> providerClass) {
    registerProviderClassImpl(providerClass);
  }

  private <T> void registerProviderClassImpl(Class<T> providerClass) {
    registerProviderClass(providerClass, providerClass);
  }

}