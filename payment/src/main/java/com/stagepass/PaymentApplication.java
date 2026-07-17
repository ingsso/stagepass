package com.stagepass;

import com.stagepass.kafka.consumer.QueueEventConsumer;
import com.stagepass.kafka.consumer.ReservationEventConsumer;
import com.stagepass.kafka.consumer.SeatEventConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 결제 앱. Toss 승인·취소와 payment.requested Saga 만 담당한다.
 *
 * kafka 모듈의 Consumer(reservation/seat/queue-group)는 api 앱이 전담한다.
 * payment 는 com.stagepass 를 스캔하고 kafka 모듈을 의존하므로, 제외하지 않으면
 * 같은 consumer group 의 Consumer 가 api·payment 두 프로세스에 뜬다. 토픽이
 * 파티션 1개라 그중 하나만 파티션을 할당받아, 예매 확정·보상 트랜잭션을 어느
 * 앱이 처리할지가 기동 순서에 따라 갈린다.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "com.stagepass",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {QueueEventConsumer.class, ReservationEventConsumer.class, SeatEventConsumer.class}
    )
)
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
