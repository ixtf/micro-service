package com.github.ixtf.api.netifi;

import com.github.ixtf.api.netifi.internal.NetifiServerMethodInterceptor;
import com.github.ixtf.japp.GuiceModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.lang.reflect.Method;
import java.util.Collection;

import static com.github.ixtf.api.guice.ApiModule.ACTIONS;
import static java.util.stream.Collectors.toUnmodifiableList;

public abstract class NetifiModule extends AbstractModule {
    public static final String NETIFI_SERVER_METHOD_INTERCEPTORS = "__:NetifiModule:NETIFI_SERVER_METHOD_INTERCEPTORS:__";

    @Singleton
    @Provides
    @Named(NETIFI_SERVER_METHOD_INTERCEPTORS)
    private Collection<NetifiServerMethodInterceptor> ServerMethodInterceptorMap(@Named(ACTIONS) Collection<Method> methods) {
        return methods.stream()
                .map(NetifiServerMethodInterceptor::from)
                .peek(GuiceModule::injectMembers)
                .collect(toUnmodifiableList());
    }

}
