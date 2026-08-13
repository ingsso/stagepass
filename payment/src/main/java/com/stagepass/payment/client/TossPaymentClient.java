package com.stagepass.payment.client;

import com.stagepass.payment.dto.TossPaymentCancelRequest;
import com.stagepass.payment.dto.TossPaymentConfirmRequest;
import com.stagepass.payment.dto.TossPaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class TossPaymentClient {

  private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
  private static final String CANCEL_URL  = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";

  private final RestTemplate restTemplate;
  private final String encodedSecretKey;

  public TossPaymentClient(
      @Value("${toss.secret-key}") String secretKey,
      @Value("${toss.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${toss.read-timeout-ms:5000}") int readTimeoutMs) {
    this.restTemplate = new RestTemplate(requestFactory(connectTimeoutMs, readTimeoutMs));
    String raw = secretKey + ":";
    this.encodedSecretKey = Base64.getEncoder()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @CircuitBreaker(name = "toss")
  public TossPaymentResponse confirm(TossPaymentConfirmRequest request) {
    HttpEntity<TossPaymentConfirmRequest> entity = new HttpEntity<>(request, jsonHeaders());
    try {
      ResponseEntity<TossPaymentResponse> response =
          restTemplate.postForEntity(CONFIRM_URL, entity, TossPaymentResponse.class);
      log.info("[Toss] payment confirmed orderId={}", request.getOrderId());
      return response.getBody();
    } catch (ResourceAccessException e) {
      log.error("[Toss] payment confirm network error orderId={}", request.getOrderId(), e);
      throw new RuntimeException("TossPayments communication failed (timeout or network error)");
    } catch (HttpClientErrorException e) {
      log.error("[Toss] payment confirm failed orderId={} status={} body={}",
          request.getOrderId(), e.getStatusCode(), e.getResponseBodyAsString());
      throw new RuntimeException("TossPayments payment confirm failed: " + e.getResponseBodyAsString());
    }
  }

  @CircuitBreaker(name = "toss")
  public TossPaymentResponse cancel(String paymentKey, String reason, Integer amount) {
    TossPaymentCancelRequest body = new TossPaymentCancelRequest(reason, amount);
    HttpEntity<TossPaymentCancelRequest> entity = new HttpEntity<>(body, jsonHeaders());
    try {
      ResponseEntity<TossPaymentResponse> response = restTemplate.postForEntity(
          CANCEL_URL, entity, TossPaymentResponse.class, paymentKey);
      log.info("[Toss] payment cancel success paymentKey={}", paymentKey);
      return response.getBody();
    } catch (ResourceAccessException e) {
      log.error("[Toss] payment cancel network error paymentKey={}", paymentKey, e);
      throw new RuntimeException("TossPayments communication failed (timeout or network error)");
    } catch (HttpClientErrorException e) {
      log.error("[Toss] payment cancel failed paymentKey={} status={} body={}",
          paymentKey, e.getStatusCode(), e.getResponseBodyAsString());
      throw new RuntimeException("TossPayments cancel failed: " + e.getResponseBodyAsString());
    }
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Basic " + encodedSecretKey);
    return headers;
  }

  private SimpleClientHttpRequestFactory requestFactory(int connectMs, int readMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectMs);
    factory.setReadTimeout(readMs);
    return factory;
  }
}
