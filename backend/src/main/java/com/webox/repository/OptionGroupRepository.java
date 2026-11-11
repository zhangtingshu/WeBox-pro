package com.webox.repository;

import com.webox.entity.OptionGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OptionGroupRepository extends JpaRepository<OptionGroupEntity, Long> {

    List<OptionGroupEntity> findByDishIdOrderBySortOrderAsc(Long dishId);

    List<OptionGroupEntity> findByDishIdInOrderByDishIdAscSortOrderAsc(Collection<Long> dishIds);

    void deleteByDishId(Long dishId);
}
