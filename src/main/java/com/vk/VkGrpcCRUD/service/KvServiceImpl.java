package com.vk.VkGrpcCRUD.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.vk.VkGrpcCRUD.entity.Kv;
import com.vk.VkGrpcCRUD.grpc.CountResponse;
import com.vk.VkGrpcCRUD.grpc.DeleteRequest;
import com.vk.VkGrpcCRUD.grpc.GetRequest;
import com.vk.VkGrpcCRUD.grpc.GetResponse;
import com.vk.VkGrpcCRUD.grpc.KeyValueResponse;
import com.vk.VkGrpcCRUD.grpc.KvServiceGrpc;
import com.vk.VkGrpcCRUD.grpc.PutRequest;
import com.vk.VkGrpcCRUD.grpc.RangeRequest;
import com.vk.VkGrpcCRUD.repository.KvBoxRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@GrpcService
public class KvServiceImpl extends KvServiceGrpc.KvServiceImplBase {

    private static final Logger LOG = Logger.getLogger(KvServiceImpl.class.getName());
    private final KvBoxRepository repository;

    public KvServiceImpl(KvBoxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void put(PutRequest request, StreamObserver<Empty> responseObserver) {
        byte[] value = request.hasValue() ? request.getValue().toByteArray() : null;

        repository.save(new Kv(request.getKey(), value));

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        LOG.log(Level.INFO, "Getting key {0}", request.getKey());
        GetResponse.Builder responseBuilder = GetResponse.newBuilder();

        repository.findByKey(request.getKey()).ifPresent(kv -> {
            if (kv.getValue() != null) {
                responseBuilder.setValue(ByteString.copyFrom(kv.getValue()));
            }
        });

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<Empty> responseObserver) {
        repository.deleteByKey(request.getKey());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void count(Empty request, StreamObserver<CountResponse> responseObserver) {
        long count = repository.count();
        responseObserver.onNext(CountResponse.newBuilder().setCount(count).build());
        responseObserver.onCompleted();
    }

    @Override
    public void range(RangeRequest request, StreamObserver<KeyValueResponse> responseObserver) {
        Consumer<Kv> consumer = kv -> {
            var responseBuilder = KeyValueResponse.newBuilder();
            if (kv.getValue() != null) {
                responseBuilder.setValue(ByteString.copyFrom(kv.getValue()));
            }
            responseObserver.onNext(
                    responseBuilder
                            .setKey(kv.getKey())
                            .build());
        };

        repository.findRangeByKey(request.getKeySince(), request.getKeyTo(), consumer)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.log(Level.WARNING, "Error getting values", ex);
                        responseObserver.onError(Status.INTERNAL.withCause(ex).asException());
                    } else {
                        responseObserver.onCompleted();
                    }
                });
    }
}
