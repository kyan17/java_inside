package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class InterceptorRegistry {

//  private final HashMap<Class<?>, List<AroundAdvice>> adviceMap = new HashMap<>();
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();
  private final ConcurrentHashMap<Method, Invocation> cache = new ConcurrentHashMap<>();

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice advice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(advice);
    addInterceptor(annotationClass, ((instance, method, args, invocation) -> {
      advice.before(instance, method, args);
      Object result = null;
      try {
        result = invocation.proceed(instance, method, args);
      } finally {
        advice.after(instance, method, args, result);
      }
      return result;
    }));
  }

//  List<AroundAdvice> findAdvices(Method method) {
//    return Arrays.stream(method.getAnnotations())
//      .flatMap(annotation -> adviceMap.getOrDefault(annotation.annotationType(), List.of()).stream())
//      .toList();
//  }

//  public <T> T createProxy(Class<T> interfaceType, T instance) {
//    Objects.requireNonNull(interfaceType);
//    Objects.requireNonNull(instance);
//    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] {interfaceType},
//      (proxy, method, args) -> {
//        var advices = findAdvices(method);
//        for (var advice : advices) {
//          advice.before(instance, method, args);
//        }
//        Object result = null;
//        try {
//          result = Utils.invokeMethod(instance, method, args);
//        } finally {
//          for (var advice : advices) {
//            advice.after(instance, method, args, result);
//          }
//        }
//        return result;
//      }));
//  }

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.computeIfAbsent(annotationClass, _ -> new ArrayList<>()).add(interceptor);
    cache.clear();
  }

//  List<Interceptor> findInterceptors(Method method) {
//    return Arrays.stream(method.getAnnotations())
//      .flatMap(annotation -> interceptorMap.getOrDefault(annotation.annotationType(), List.of()).stream())
//      .toList();
//  }

  List<Interceptor> findInterceptors(Method method) {
    return Stream.of(
        Arrays.stream(method.getDeclaringClass().getAnnotations()),
        Arrays.stream(method.getAnnotations()),
        Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
      .flatMap(s -> s)
      .map(Annotation::annotationType)
      .distinct()
      .flatMap(annotationType -> interceptorMap.getOrDefault(annotationType, List.of()).stream())
      .toList();
  }

  static Invocation getInvocation(List<Interceptor> interceptors) {
    Invocation invocation = Utils::invokeMethod;
    for (var interceptor : interceptors.reversed()) {
      var oldInvocation = invocation;
      invocation = ((instance, method, args) ->
        interceptor.intercept(instance, method, args, oldInvocation
      ));
    }
    return invocation;
  }

//  public <T> T createProxy(Class<T> interfaceType, T instance) {
//    Objects.requireNonNull(interfaceType);
//    Objects.requireNonNull(instance);
//    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] {interfaceType},
//            ((proxy, method, args) -> {
//              var interceptors = findInterceptors(method);
//              var invocation = getInvocation(interceptors);
//              return invocation.proceed(instance, method, args);
//            }))
//    );
//  }

  public <T> T createProxy(Class<T> interfaceType, T instance) {
    Objects.requireNonNull(interfaceType);
    Objects.requireNonNull(instance);
    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] {interfaceType},
      ((proxy, method, args) -> {
        var invocation = cache.computeIfAbsent(method, m -> getInvocation(findInterceptors(m)));
        return invocation.proceed(instance, method, args);
      }))
    );
  }

}
