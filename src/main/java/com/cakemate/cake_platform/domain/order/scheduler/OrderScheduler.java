package com.cakemate.cake_platform.domain.order.scheduler;

import com.cakemate.cake_platform.domain.order.entity.Order;
import com.cakemate.cake_platform.domain.order.enums.OrderStatus;
import com.cakemate.cake_platform.domain.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class OrderScheduler {

    private final OrderRepository orderRepository;

    public OrderScheduler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void checkExpiredOrders() {

        // 만료 시간이 지난 주문 리스트 가져오기
        List<Order> expiredOrders = orderRepository.findByStatusAndPaymentExpiresAtBefore(OrderStatus.MAKE_WAITING, LocalDateTime.now());

        log.info("[OrderScheduler] 만료된 주문 수: {}개", expiredOrders.size());

        if (expiredOrders.isEmpty()) {
            log.info("[OrderScheduler] 처리할 만료 주문 없음");
            return;
        }


        for (Order order : expiredOrders) {
            order.setStatus(OrderStatus.EXPIRED);

            String customerId = Optional.ofNullable(order.getCustomer())
                    .map(c -> c.getId())
                    .map(String::valueOf)
                    .orElse("unknown");

            log.info("[OrderScheduler] 주문 만료 처리: id={}, customerId={}, paymentExpiresAt={}",
                    order.getId(),
                    customerId,
                    order.getPaymentExpiresAt());
        }

        try {
            orderRepository.saveAll(expiredOrders);
            log.info("[OrderScheduler] 상태 업데이트 완료");
        } catch (Exception e) {
            log.error("[OrderScheduler] 상태 업데이트 실패", e);
        }

    }
}
