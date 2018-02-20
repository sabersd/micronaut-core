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
package org.particleframework.configuration.lettuce.test;

import io.lettuce.core.RedisURI;
import org.particleframework.configuration.lettuce.AbstractRedisConfiguration;
import org.particleframework.configuration.lettuce.RedisSetting;
import org.particleframework.context.annotation.*;
import org.particleframework.context.event.BeanCreatedEvent;
import org.particleframework.context.event.BeanCreatedEventListener;
import org.particleframework.core.io.socket.SocketUtils;
import org.particleframework.core.util.StringUtils;
import redis.embedded.RedisServer;
import redis.embedded.RedisServerBuilder;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * An bean for an embedded Redis server
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(classes = RedisServer.class)
@Requires(beans = AbstractRedisConfiguration.class)
@Factory
public class EmbeddedRedisServer implements BeanCreatedEventListener<AbstractRedisConfiguration>, Closeable {

    private final Configuration embeddedConfiguration;
    private RedisServer redisServer;

    public EmbeddedRedisServer(Configuration embeddedConfiguration) {
        this.embeddedConfiguration = embeddedConfiguration;
    }

    @Override
    public AbstractRedisConfiguration onCreated(BeanCreatedEvent<AbstractRedisConfiguration> event) {
        AbstractRedisConfiguration configuration = event.getBean();
        Optional<RedisURI> uri = configuration.getUri();
        int port  = configuration.getPort();
        String host = configuration.getHost();
        if(uri.isPresent()) {
            RedisURI redisURI = uri.get();
            port = redisURI.getPort();
            host = redisURI.getHost();

        }
        if(StringUtils.isNotEmpty(host) && host.equals("localhost") && SocketUtils.isTcpPortAvailable(port)) {
            RedisServerBuilder builder = embeddedConfiguration.builder;
            builder.port(port);
            redisServer = builder.build();
            redisServer.start();

        }
        return configuration;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if(redisServer != null) {
            redisServer.stop();
        }
    }


    @ConfigurationProperties(RedisSetting.REDIS_EMBEDDED)
    @Requires(classes = RedisServerBuilder.class )
    public static class Configuration {
        @ConfigurationBuilder(
                prefixes = ""
        )
        RedisServerBuilder builder = new RedisServerBuilder().port(SocketUtils.findAvailableTcpPort());
    }
}