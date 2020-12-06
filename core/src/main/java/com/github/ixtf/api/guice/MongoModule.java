package com.github.ixtf.api.guice;

import com.github.ixtf.persistence.mongo.Jmongo;
import com.github.ixtf.persistence.mongo.JmongoOptions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.vertx.core.json.JsonObject;

import static com.github.ixtf.api.guice.ApiModule.CONFIG;
import static com.github.ixtf.japp.GuiceModule.getInstance;

public class MongoModule extends AbstractModule {
    private static volatile String dbName;

    public MongoModule(String dbName) {
        MongoModule.dbName = dbName;
    }

    @Singleton
    @Provides
    private Jmongo Jmongo() {
        return Jmongo.of(Options.class);
    }

    @Singleton
    @Provides
    private MongoClient MongoClient(@Named(CONFIG) JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("mongo");
        final var connection_string = config.getString("connection_string");
        final var builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connection_string));
        return MongoClients.create(builder.build());
    }

    public static class Options extends JmongoOptions {
        @Override
        protected MongoClient client() {
            return getInstance(MongoClient.class);
        }

        @Override
        public String dbName() {
            return dbName;
        }
    }

}
