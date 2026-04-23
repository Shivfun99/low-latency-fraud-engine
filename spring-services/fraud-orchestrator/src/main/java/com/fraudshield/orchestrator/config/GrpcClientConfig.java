package com.fraudshield.orchestrator.config;
import com.frauddetection.grpc.FraudScoringServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class GrpcClientConfig {
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel managedChannel(@Value("${fraud.ml.host}") String host,
                                         @Value("${fraud.ml.port}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }
    @Bean
    public FraudScoringServiceGrpc.FraudScoringServiceBlockingStub fraudScoringServiceBlockingStub(ManagedChannel managedChannel) {
        return FraudScoringServiceGrpc.newBlockingStub(managedChannel);
    }
}
