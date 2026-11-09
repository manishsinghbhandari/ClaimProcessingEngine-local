package io.coredge.health.claims.config;

import io.coredge.health.claims.annotations.GlobalProperty;
import io.coredge.health.claims.annotations.impl.GlobalPropertyValue;
import io.coredge.health.claims.service.GlobalPropertyResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;

@Component
public class GlobalPropertyBeanPostProcessor implements BeanPostProcessor {

    private final GlobalPropertyResolver resolver;

    public GlobalPropertyBeanPostProcessor(GlobalPropertyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, @NotNull String beanName) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(GlobalProperty.class)) {
                GlobalProperty annotation = field.getAnnotation(GlobalProperty.class);
                field.setAccessible(true);
                try {
                    injectWrapper(bean, field, annotation.key());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to inject @GlobalProperty", e);
                }
            }
        }
        return bean;
    }

    private void injectWrapper(Object bean, Field field, String key) throws IllegalAccessException {
        Class<?> genericType = (Class<?>) ((java.lang.reflect.ParameterizedType) field.getGenericType())
                .getActualTypeArguments()[0];

        GlobalPropertyValue<?> wrapper = () -> {
            String value = resolver.resolve(key);
            return convert(value, genericType);
        };

        field.set(bean, wrapper);
    }

    private Object convert(String value, Class<?> type) {
        if (value == null) return null;
        if (type.equals(String.class)) return value;
        if (type.equals(Long.class) || type.equals(long.class)) return Long.valueOf(value);
        if (type.equals(Integer.class) || type.equals(int.class)) return Integer.valueOf(value);
        if (type.equals(Boolean.class) || type.equals(boolean.class)) return Boolean.valueOf(value);
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}