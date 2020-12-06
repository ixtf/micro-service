package com.github.ixtf.api.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Data;

import static com.github.ixtf.api.guice.ApiModule.CONFIG;

public class KeycloakModule extends AbstractModule {

    @Singleton
    @Provides
    private KeycloakOptions KeycloakOptions(@Named(CONFIG) JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("keycloak-admin");
        return KeycloakOptions.builder()
                .serverUrl(config.getString("serverUrl"))
                .username(config.getString("username"))
                .password(config.getString("password"))
                .build();
    }

    @Builder
    @Data
    public static class KeycloakOptions {
        private final String serverUrl;
        @Builder.Default
        private final String realm = "master";
        private final String username;
        private final String password;
        @Builder.Default
        private final String clientId = "admin-cli";
    }

}
