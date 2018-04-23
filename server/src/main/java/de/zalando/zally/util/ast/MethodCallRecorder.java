package de.zalando.zally.util.ast;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static de.zalando.zally.util.ast.Util.getterNameToPointer;
import static de.zalando.zally.util.ast.Util.rfc6901Encode;

public final class MethodCallRecorder<T> {
    static class MethodCallRecorderException extends Throwable {
        MethodCallRecorderException(String message) {
            super(message);
        }

        MethodCallRecorderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Set<Class<?>> PRIMITIVES = new HashSet<>(Util.PRIMITIVES);

    static boolean isGetterMethod(Method m) {
        return m.getName().startsWith("get") && m.getReturnType() != null;
    }

    static boolean isPrimitive(Object o) {
        return isPrimitive(o.getClass());
    }

    static boolean isPrimitive(Class<?> c) {
        return PRIMITIVES.contains(c);
    }

    static boolean isGenericContainer(Object o) {
        return o instanceof Collection || o instanceof Map;
    }

    @SuppressWarnings("unchecked")
    static <T> T createInstance(Class<T> c) throws MethodCallRecorderException {
        if (c.isAssignableFrom(Map.class)) {
            return (T) new HashMap<>();
        }
        if (c.isAssignableFrom(Collection.class)) {
            return (T) new ArrayList<>();
        }
        try {
            Constructor<T> constructor = c.getConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new MethodCallRecorderException("Cannot create " + c.toString(), e);
        }
    }

    static Class<?> getGenericReturnValueType(Method m) throws MethodCallRecorderException {
        Type type = m.getGenericReturnType();
        if (type instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) type).getActualTypeArguments();
            return (Class<?>) typeArgs[typeArgs.length - 1];
        }
        throw new MethodCallRecorderException(m.getReturnType().toString());
    }

    static String toPointer(String s) {
        return "/".concat(rfc6901Encode(getterNameToPointer(s)));
    }

    static String toPointer(Method m, Object... arguments) {
        String s = m.getName();
        if (arguments.length > 0) {
            s = s.concat(arguments[0].toString());
        }
        return toPointer(s);
    }

    private final T proxy;
    private String currentPointer;
    private final Set<String> skipMethods;
    private final IdentityHashMap<Object, String> objectPointerCache;
    private final IdentityHashMap<Object, IdentityHashMap<Method, String>> methodPointerCache;

    public MethodCallRecorder(T object) {
        this.currentPointer = "#";
        this.skipMethods = new HashSet<>();
        this.objectPointerCache = new IdentityHashMap<>();
        this.methodPointerCache = new IdentityHashMap<>();
        this.proxy = createProxy(object, null);
    }

    @Nonnull
    public MethodCallRecorder<T> skipMethods(String... pointer) {
        this.skipMethods.addAll(Arrays.asList(pointer));
        return this;
    }

    @Nonnull
    public T getProxy() {
        return this.proxy;
    }

    @SuppressWarnings("unchecked")
    private <U> U createProxy(U object, Method parent) {
        MethodInterceptor interceptor = createMethodInterceptor(object, parent);
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(object);
        factory.addAdvice(interceptor);
        return (U) factory.getProxy();
    }

    private MethodInterceptor createMethodInterceptor(Object object, Method parent) {
        return invocation -> {
            Method m = invocation.getMethod();
            Object[] arguments = invocation.getArguments();
            updatePointer(object, m, arguments);

            if (!isGetterMethod(m)) {
                return invocation.proceed();
            }
            Object result = m.invoke(invocation.getThis(), arguments);

            if (result == null) {
                Class<?> returnType = m.getReturnType();
                if (isPrimitive(returnType)) {
                    return null;
                }
                if (isGenericContainer(object)) {
                    Class<?> genericReturnValueType = getGenericReturnValueType(parent);
                    return createProxy(createInstance(genericReturnValueType), m);
                }
                return createProxy(createInstance(returnType), m);
            }
            if (isPrimitive(result)) {
                return result;
            }
            return createProxy(result, m);
        };
    }

    private void updatePointer(Object object, Method method, Object[] arguments) {
        if (skipMethods.contains(method.getName())) {
            return;
        }
        final String objectPointer;
        if (objectPointerCache.containsKey(object)) {
            objectPointer = objectPointerCache.get(object);
        } else {
            objectPointerCache.put(object, currentPointer);
            objectPointer = currentPointer;
        }
        if (methodPointerCache.containsKey(object)) {
            Map<Method, String> methodMap = methodPointerCache.get(object);
            if (methodMap.containsKey(method)) {
                currentPointer = methodMap.get(method);
            } else {
                currentPointer = objectPointer.concat(toPointer(method, arguments));
                methodMap.put(method, currentPointer);
            }
        } else {
            IdentityHashMap<Method, String> methodMap = new IdentityHashMap<>();
            currentPointer = objectPointer.concat(toPointer(method, arguments));
            methodMap.put(method, currentPointer);
            methodPointerCache.put(object, methodMap);
        }
    }

    @Nonnull
    public String getPointer() {
        return this.currentPointer;
    }
}
