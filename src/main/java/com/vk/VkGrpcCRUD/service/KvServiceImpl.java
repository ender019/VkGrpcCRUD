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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

@GrpcService
public class KvServiceImpl extends KvServiceGrpc.KvServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(KvServiceImpl.class);
    private final KvBoxRepository repository;

    public KvServiceImpl(KvBoxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void put(PutRequest request, StreamObserver<Empty> responseObserver) {
        byte[] value = request.hasValue() ? request.getValue().toByteArray() : null;

        try {
            repository.save(new Kv(request.getKey(), value));
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error while putting value", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        GetResponse.Builder responseBuilder = GetResponse.newBuilder();
        try {
            repository.findByKey(request.getKey()).ifPresent(kv -> {
                if (kv.getValue() != null) {
                    responseBuilder.setValue(ByteString.copyFrom(kv.getValue()));
                }
            });

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error while getting value", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<Empty> responseObserver) {
        try {
            repository.deleteByKey(request.getKey());
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error while deleting value", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void count(Empty request, StreamObserver<CountResponse> responseObserver) {
        try {
            long count = repository.count();
            responseObserver.onNext(CountResponse.newBuilder().setCount(count).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error while counting value", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
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
                        LOG.error("Error while processing range request", ex);
                        responseObserver.onError(Status.INTERNAL.withCause(ex).asException());
                    } else {
                        responseObserver.onCompleted();
                    }
                });
    }
}
