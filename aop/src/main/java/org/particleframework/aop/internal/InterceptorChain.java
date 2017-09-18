/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.aop.internal;

import org.particleframework.aop.Interceptor;
import org.particleframework.aop.InvocationContext;
import org.particleframework.context.BeanLocator;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.MutableConvertibleMultiValues;
import org.particleframework.core.convert.MutableConvertibleMultiValuesMap;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ArgumentValue;
import org.particleframework.inject.ExecutionHandle;
import org.particleframework.inject.MutableArgumentValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.IntFunction;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<R> implements InvocationContext<R> {
    private final Interceptor<R>[] interceptors;
    private final Object target;
    private final ExecutionHandle<R> executionHandle;
    private final MutableConvertibleMultiValues<Object> attributes = new MutableConvertibleMultiValuesMap<>();
    private final Map<String, MutableArgumentValue<?>> parameters = new LinkedHashMap<>();
    private int index = 0;

    public InterceptorChain(Interceptor<R>[] interceptors,
                            Object target,
                            ExecutionHandle<R> executionHandle,
                            Object...originalParameters) {
        this.target = target;
        this.executionHandle = executionHandle;
        OrderUtil.sort(interceptors);
        this.interceptors = new Interceptor[interceptors.length+1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        this.interceptors[this.interceptors.length-1] = context -> executionHandle.invoke(
                getParameterValues()
        );
        Argument[] arguments = executionHandle.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = executionHandle.getArguments()[i];
            parameters.put(argument.getName(), MutableArgumentValue.create(argument, originalParameters[i]));
        }
    }

    public InterceptorChain(Class<Interceptor<R>>[] interceptorTypes,
                            BeanLocator beanLocator,
                            ExecutionHandleLocator handleLocator,
                            Object target,
                            Method method,
                            Object...originalParameters) {
        this(InterceptorChain.findInterceptors(interceptorTypes, beanLocator),
                target,
                handleLocator.<R>findExecutionHandle(target, method.getName(), method.getParameterTypes())
                             .orElseThrow(()-> new IllegalStateException("No interceptable method found: " + method)),
                originalParameters
        );
    }

    private static <T> Interceptor<T>[] findInterceptors(Class<Interceptor<T>>[] interceptorTypes, BeanLocator beanLocator) {
        return Arrays.stream(interceptorTypes)
                     .map(beanLocator::getBean)
                     .toArray((IntFunction<Interceptor<T>[]>) Interceptor[]::new);
    }

    @Override
    public Class getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return attributes.get(name, requiredType);
    }

    @Override
    public List<Object> getAll(CharSequence name) {
        return attributes.getAll(name);
    }

    @Override
    public Set<String> getNames() {
        return attributes.getNames();
    }

    @Override
    public Argument[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    public Object get(CharSequence name) {
        return attributes.getAll(name);
    }

    @Override
    public R invoke(Object... arguments) {
        return proceed();
    }

    @Override
    public Map<String, MutableArgumentValue<?>> getParameters() {
        return parameters;
    }

    @Override
    public Object[] getParameterValues() {
        return getParameters()
                .values()
                .stream()
                .map(ArgumentValue::getValue)
                .toArray();
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<R> interceptor;
        int len = this.interceptors.length;
        if(index == len) {
            interceptor = this.interceptors[len -1];
        }
        else {

            interceptor = this.interceptors[index++];
        }
        return interceptor.intercept(this);
    }

    @Override
    public InvocationContext<R> add(CharSequence key, Object value) {
        this.attributes.add(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> put(CharSequence key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> remove(CharSequence key, Object value) {
        this.attributes.remove(key, value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> clear(CharSequence key) {
        this.attributes.clear(key);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<Object> clear() {
        this.attributes.clear();
        return this;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return executionHandle.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return executionHandle.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return executionHandle.getDeclaredAnnotations();
    }
}