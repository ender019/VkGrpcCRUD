package com.vk.VkGrpcCRUD.config;

import io.tarantool.client.box.TarantoolBoxClient;
import io.tarantool.client.crud.TarantoolCrudClient;
import io.tarantool.client.factory.TarantoolFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TarantoolConfig {

    @Value("${spring.data.tarantool.host:localhost}")
    private String host;

    @Value("${spring.data.tarantool.port:3301}")
    private int port;

    @Value("${spring.data.tarantool.username:admin}")
    private String username;

    @Value("${spring.data.tarantool.password:password}")
    private String password;

    @Bean
    public TarantoolBoxClient boxClientSettings() throws Exception{
        return TarantoolFactory.box()
                .withHost(host)
                .withPort(port)
                .withUser(username)
                .withPassword(password)
                .build();
    }
}
