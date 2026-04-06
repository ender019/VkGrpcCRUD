package com.vk.VkGrpcCRUD.testconfig;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
public class TarantoolTestConfig {

    @Container
    private static final GenericContainer<?> container;

    static {
        container = new GenericContainer<>(DockerImageName.parse("tarantool/tarantool:3.2"))
                .withExposedPorts(3301)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("init.lua"),
                        "/opt/tarantool/init.lua"
                )
                .withCommand("tarantool", "/opt/tarantool/init.lua")
                .withReuse(true);
        container.start();
    }

    @DynamicPropertySource
    static void registerTarantoolProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.tarantool.host", container::getHost);
        registry.add("spring.data.tarantool.port", () -> container.getMappedPort(3301));
        registry.add("spring.data.tarantool.username", () -> "admin");
        registry.add("spring.data.tarantool.password", () -> "password");
    }

}

