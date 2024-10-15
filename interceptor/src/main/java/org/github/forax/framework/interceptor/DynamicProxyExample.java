package org.github.forax.framework.interceptor;

import java.lang.reflect.*;

interface Service {
  void performAction();
}

class RealService implements Service {
  public void performAction() {
    System.out.println("Action effectuée par le service réel");
  }
}

class ProxyHandler implements InvocationHandler {
  private Object target;

  public ProxyHandler(Object target) {
    this.target = target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    System.out.println("Avant l'appel de la méthode...");
    Object result = method.invoke(target, args);  // Appel de la méthode réelle
    System.out.println("Après l'appel de la méthode...");
    return result;
  }
}

public class DynamicProxyExample {
  public static void main(String[] args) {
    var realService = new RealService();
    var proxyInstance = (Service) Proxy.newProxyInstance(
            realService.getClass().getClassLoader(),
            new Class[]{Service.class},
            new ProxyHandler(realService)
    );
    proxyInstance.performAction();
  }
}
