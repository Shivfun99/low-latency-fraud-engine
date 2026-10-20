package com.fraudshield.gateway;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayApplicationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper mapper;

  @MockBean
  KafkaTemplate<String, String> kafka;

  private static final String TRANSACTION = """
  {"user_id":"regression-user","amount":125.50,"device_id":"device","location":"Mumbai"}
  """;

  @Test
  void healthIsPublicAndDoesNotConsumeTransactionRateLimit() throws Exception {
    for (int i = 0; i < 12; i++) {
      mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
  }

  @Test
  void transactionRouteRequiresAuthentication() throws Exception {
    mvc
      .perform(
        post("/api/v1/transactions")
          .contentType("application/json")
          .content(TRANSACTION)
      )
      .andExpect(status().isUnauthorized());
    verifyNoInteractions(kafka);
  }

  @Test
  void loginTokenAllowsTransactionAndWaitsForBrokerAcceptance()
    throws Exception {
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
      CompletableFuture.completedFuture(null)
    );
    var response = mvc
      .perform(
        post("/api/v1/auth/login")
          .contentType("application/json")
          .content("{\"username\":\"admin\",\"password\":\"password\"}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String token = mapper.readTree(response).get("token").asText();
    mvc
      .perform(
        post("/api/v1/transactions")
          .header("Authorization", "Bearer " + token)
          .contentType("application/json")
          .content(TRANSACTION)
      )
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.decision").value("PENDING"));
    verify(kafka).send(
      eq("transactions.raw"),
      eq("regression-user"),
      anyString()
    );
  }

  @Test
  void sandboxValidatesInputBeforePublishing() throws Exception {
    mvc
      .perform(
        post("/api/v1/sandbox/ingest")
          .contentType("application/json")
          .content("{\"amount\":-1}")
      )
      .andExpect(status().isBadRequest());
    verifyNoInteractions(kafka);
  }

  @Test
  void unavailableBrokerDoesNotReturnAccepted() throws Exception {
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
      CompletableFuture.failedFuture(
        new IllegalStateException("broker unavailable")
      )
    );
    mvc
      .perform(
        post("/api/v1/sandbox/ingest")
          .contentType("application/json")
          .content(TRANSACTION)
      )
      .andExpect(status().is5xxServerError());
  }
}
