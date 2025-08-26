package com.cakemate.cake_platform.domain.store.scheduler;

import com.cakemate.cake_platform.domain.store.ranking.dto.StoreRankingResponseDto;
import com.cakemate.cake_platform.domain.store.ranking.service.StoreRankingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class StoreRankingCacheScheduler {
    private final StoreRankingService storeRankingService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // TTL 및 마진 설정 (운영 환경: TTL 60분, 마진 5분)
    private static final long TTL_MINUTES = 60;
    private static final long MARGIN_MINUTES = 5;

    public StoreRankingCacheScheduler(StoreRankingService storeRankingService) {
        this.storeRankingService = storeRankingService;
    }

    @PostConstruct
    public void init() {
        try {
            storeRankingService.refreshWeeklyTopStores(); // 서버 시작 시 캐시 초기 로드
            log.info("[스케줄러] 서버 시작 시 캐시 초기 로드 완료");
        } catch (Exception e) {
            log.error("[스케줄러] 서버 시작 시 캐시 로드 실패", e);
        }
    }

    // 매 정각 0분마다 실행
    @Scheduled(cron = "0 0 * * * *")
    public void refreshCacheAtTopOfHour() {
        try {
            storeRankingService.refreshWeeklyTopStores();
            log.info("[스케줄러] 매 정각 캐시 갱신 완료");
        } catch (Exception e) {
            log.error("[스케줄러] 캐시 갱신 실패", e);
        }
    }
}


