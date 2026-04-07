package com.vk.VkGrpcCRUD.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.vk.VkGrpcCRUD.entity.Kv;
import com.vk.VkGrpcCRUD.grpc.*;
import com.vk.VkGrpcCRUD.repository.KvBoxRepository;
import com.vk.VkGrpcCRUD.testconfig.KvGrpcStubConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "grpc.server.port=9091",
    "grpc.server.in-process-name=test"
})
@ContextConfiguration(classes = {
        KvServiceImpl.class,
        KvGrpcStubConfig.class,
        net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration.class,
        net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration.class
})
public class KvServiceImplITest {

    @MockitoBean
    private KvBoxRepository repository;

    @Autowired
    private KvServiceGrpc.KvServiceBlockingStub blockingStub;

    @Test
    @DisplayName("PUT: Тест передачи данных через сеть до мока")
    void testPutIntegration() {
        String key = "test_key";
        byte[] value = "test_val".getBytes();

        Mockito.doNothing().when(repository).save(Mockito.any(Kv.class));

        PutRequest request = PutRequest.newBuilder()
                .setKey(key)
                .setValue(ByteString.copyFrom(value))
                .build();

        assertDoesNotThrow(() -> blockingStub.put(request));

        Mockito.verify(repository).save(Mockito.argThat(kv ->
                kv.getKey().equals(key) && java.util.Arrays.equals(kv.getValue(), value)
        ));
    }

    @Test
    @DisplayName("PUT: Тест передачи null через сеть до мока")
    void testPutNullIntegration() {
        String key = "test_key";
        byte[] value = null;

        Mockito.doNothing().when(repository).save(new Kv(key, value));

        PutRequest request = PutRequest.newBuilder()
                .setKey(key)
                .build();

        assertDoesNotThrow(() -> blockingStub.put(request));

        Mockito.verify(repository).save(Mockito.argThat(kv ->
                kv.getKey().equals(key) && java.util.Arrays.equals(kv.getValue(), value)
        ));
    }

    @Test
    @DisplayName("GET: Тест получения данных и обработки NULL")
    void testGetWithNullValue() {
        String key = "null_key";
        Mockito.doReturn(Optional.of(new Kv(key, null))).when(repository).findByKey(key);

        GetResponse response = blockingStub.get(GetRequest.newBuilder().setKey(key).build());

        assertFalse(response.hasValue(), "В gRPC ответе поле value не должно быть установлено для NULL из БД");
    }

    @Test
    @DisplayName("GET: Тест null ключа")
    void testGetWithNullKey() {
        String key = null;
        Mockito.doReturn(Optional.of(new Kv(key, null))).when(repository).findByKey(key);

        GetResponse response = blockingStub.get(GetRequest.newBuilder().build());

        assertFalse(response.hasValue(), "В gRPC ответе поле value не должно быть установлено для NULL из БД");
    }

    @Test
    @DisplayName("COUNT: Тест получения числового значения")
    void testCountIntegration() {
        Mockito.doReturn(5000000L).when(repository).count();

        CountResponse response = blockingStub.count(Empty.getDefaultInstance());

        assertEquals(5000000L, response.getCount());
    }

    @Test
    @DisplayName("RANGE: Тест сетевого стриминга")
    @SuppressWarnings("unchecked")
    void testRangeStreamingIntegration() {
        String since = "a";
        String to = "c";

        Mockito.when(repository.findRangeByKey(Mockito.eq(since), Mockito.eq(to), Mockito.any(Consumer.class)))
                .thenAnswer(invocation -> {
                    Consumer<Kv> consumer = invocation.getArgument(2);
                    consumer.accept(new Kv("a1", "v1".getBytes()));
                    consumer.accept(new Kv("b1", null)); // Еще одна проверка на null
                    return CompletableFuture.completedFuture(null);
                });

        RangeRequest request = RangeRequest.newBuilder()
                .setKeySince(since)
                .setKeyTo(to)
                .build();

        // Реально читаем из gRPC стрима
        Iterator<KeyValueResponse> it = blockingStub.range(request);
        List<KeyValueResponse> results = new ArrayList<>();
        it.forEachRemaining(results::add);

        assertEquals(2, results.size());
        assertEquals("a1", results.get(0).getKey());
        assertEquals("b1", results.get(1).getKey());
        assertFalse(results.get(1).hasValue()); // Проверка null в стриме
    }

    @Test
    @DisplayName("Ошибка: Тест проброса исключения через gRPC")
    void testErrorHandling() {
        Mockito.doThrow(new RuntimeException("Tarantool error"))
                        .when(repository).findByKey(Mockito.anyString());

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                blockingStub.get(GetRequest.newBuilder().setKey("error").build())
        );

        assertEquals(Status.Code.INTERNAL, exception.getStatus().getCode());
    }
}
