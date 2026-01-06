package com.mvbr.retailstore.notification.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaSentNotificationSpringDataRepository extends JpaRepository<JpaSentNotificationEntity, String> {
    List<JpaSentNotificationEntity> findByStatusInOrderBySentAtAsc(List<String> statuses, Pageable pageable);
}
