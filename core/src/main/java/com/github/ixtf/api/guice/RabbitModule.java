package com.github.ixtf.api.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import io.vertx.core.json.JsonObject;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.*;

import java.time.Duration;

import static com.github.ixtf.api.guice.ApiModule.CONFIG;
import static com.github.ixtf.api.guice.ApiModule.SERVICE;
import static reactor.rabbitmq.Utils.singleConnectionMono;

public class RabbitModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SendOptions.class).toInstance(new SendOptions().trackReturned(true).exceptionHandler(
                new ExceptionHandlers.RetrySendingExceptionHandler(
                        Duration.ofHours(1), Duration.ofMinutes(5),
                        ExceptionHandlers.CONNECTION_RECOVERY_PREDICATE
                )
        ));
        bind(ConsumeOptions.class).toInstance(new ConsumeOptions().exceptionHandler(
                new ExceptionHandlers.RetryAcknowledgmentExceptionHandler(
                        Duration.ofDays(1), Duration.ofMinutes(5),
                        ExceptionHandlers.CONNECTION_RECOVERY_PREDICATE
                )
        ));
    }

    @Provides
    private ConnectionFactory ConnectionFactory(@Named(CONFIG) JsonObject rootConfig, @Named(SERVICE) String group) {
        final var config = rootConfig.getJsonObject("rabbit");

        final var connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.setHost(config.getString("host"));
        connectionFactory.setUsername(config.getString("username"));
        connectionFactory.setPassword(config.getString("password"));
        return connectionFactory;
    }

    @Singleton
    @Provides
    private Sender Sender(ConnectionFactory connectionFactory, @Named(SERVICE) String group) {
        final var connectionMono = singleConnectionMono(() -> {
            final Address address = new Address(connectionFactory.getHost());
            final Address[] addrs = {address};
            return connectionFactory.newConnection(addrs, group + ":sender");
        });
        final var channelPoolOptions = new ChannelPoolOptions().maxCacheSize(10);
        final var senderOptions = new SenderOptions()
                .connectionFactory(connectionFactory)
                .connectionMono(connectionMono)
                .resourceManagementScheduler(Schedulers.boundedElastic())
                .channelPool(ChannelPoolFactory.createChannelPool(connectionMono, channelPoolOptions));
        return RabbitFlux.createSender(senderOptions);
    }

    @Singleton
    @Provides
    private Receiver Receiver(ConnectionFactory connectionFactory, @Named(SERVICE) String group) {
        final ReceiverOptions receiverOptions = new ReceiverOptions()
                .connectionFactory(connectionFactory)
                .connectionSupplier(cf -> {
                    final Address address = new Address(connectionFactory.getHost());
                    return cf.newConnection(new Address[]{address}, group + ":receiver");
                });
        return RabbitFlux.createReceiver(receiverOptions);
    }

}
