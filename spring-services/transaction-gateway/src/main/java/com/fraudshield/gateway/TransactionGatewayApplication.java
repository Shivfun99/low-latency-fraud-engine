package com.fraudshield.gateway;

import com.fraudshield.common.security.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JwtUtil.class)
public class TransactionGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(TransactionGatewayApplication.class, args);
  }
}
