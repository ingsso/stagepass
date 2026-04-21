package com.stagepass.payment.client;

import com.stagepass.payment.dto.TossPaymentCancelRequest;
import com.stagepass.payment.dto.TossPaymentConfirmRequest;
import com.stagepass.payment.dto.TossPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

  public TossPaymentClient(@Value("${toss.secret-key}") String secretKey) {
    this.restTemplate = new RestTemplate();
    String raw = secretKey + ":";
    this.encodedSecretKey = Base64.getEncoder()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public TossPaymentResponse confirm(TossPaymentConfirmRequest request) {
    HttpEntity<TossPaymentConfirmRequest> entity = new HttpEntity<>(request, jsonHeaders());
    try {
      ResponseEntity<TossPaymentResponse> response =
          restTemplate.postForEntity(CONFIRM_URL, entity, TossPaymentResponse.class);
      log.info("[Toss] 결제 승인 성공 orderId={}", request.getOrderId());
      return response.getBody();
    } catch (HttpClientErrorException e) {
      log.error("[Toss] 결제 승인 실패 orderId={} status={} body={}",
          request.getOrderId(), e.getStatusCode(), e.getResponseBodyAsString());
      throw new RuntimeException("토스페이먼츠 결제 승인 실패: " + e.getResponseBodyAsString());
    }
  }

  public TossPaymentResponse cancel(String paymentKey, String reason, Integer amount) {
    TossPaymentCancelRequest body = new TossPaymentCancelRequest(reason, amount);
    HttpEntity<TossPaymentCancelRequest> entity = new HttpEntity<>(body, jsonHeaders());
    try {
      ResponseEntity<TossPaymentResponse> response = restTemplate.postForEntity(
          CANCEL_URL, entity, TossPaymentResponse.class, paymentKey);
      log.info("[Toss] 결제 취소 성공 paymentKey={}", paymentKey);
      return response.getBody();
    } catch (HttpClientErrorException e) {
      log.error("[Toss] 결제 취소 실패 paymentKey={} status={} body={}",
          paymentKey, e.getStatusCode(), e.getResponseBodyAsString());
      throw new RuntimeException("토스페이먼츠 결제 취소 실패: " + e.getResponseBodyAsString());
    }
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Basic " + encodedSecretKey);
    return headers;
  }
}