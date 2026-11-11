package com.webox.repository;

import com.webox.entity.OptionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OptionItemRepository extends JpaRepository<OptionItemEntity, Long> {

    List<OptionItemEntity> findByGroupIdOrderBySortOrderAsc(Long groupId);

    List<OptionItemEntity> findByGroupIdInOrderByGroupIdAscSortOrderAsc(Collection<Long> groupIds);

    void deleteByGroupIdIn(Collection<Long> groupIds);
}
