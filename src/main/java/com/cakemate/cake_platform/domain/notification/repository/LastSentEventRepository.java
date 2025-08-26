package com.cakemate.cake_platform.domain.notification.repository;

import com.cakemate.cake_platform.domain.notification.entity.LastSentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LastSentEventRepository extends JpaRepository<LastSentEvent, Long> {

}
