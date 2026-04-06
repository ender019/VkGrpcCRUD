package com.vk.VkGrpcCRUD.testconfig;

import com.vk.VkGrpcCRUD.grpc.KvServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javax.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

@TestConfiguration
public class KvGrpcStubConfig {

    private ManagedChannel channel;

    @Bean
    public ManagedChannel managedChannel() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 9091)
                .usePlaintext()
                .build();
        return this.channel;
    }

    @Bean
    public KvServiceGrpc.KvServiceBlockingStub kvServiceBlockingStub(ManagedChannel channel) {
        return KvServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

}
