package com.godlife.backend.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorySubTypeRepository extends JpaRepository<CategorySubType, Integer> {

    List<CategorySubType> findByActiveTrueOrderBySortOrderAscIdAsc();
}
