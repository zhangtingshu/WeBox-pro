package com.webox.repository;

import com.webox.entity.PreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<PreferenceEntity, Long> {
}
