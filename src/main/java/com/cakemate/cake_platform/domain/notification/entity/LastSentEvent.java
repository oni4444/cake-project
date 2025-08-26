package com.cakemate.cake_platform.domain.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "last_sent_events")
    public class LastSentEvent {
    @Id
    private Long memberId;

    private Long lastEventId;

    protected LastSentEvent() {}

    public LastSentEvent(Long memberId, Long lastEventId) {
        this.memberId = memberId;
        this.lastEventId = lastEventId;
    }
}
