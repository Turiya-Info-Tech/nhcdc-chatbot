package com.nhcdc.chatbot.repository;

import com.nhcdc.chatbot.entity.Centre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CentreRepository extends JpaRepository<Centre, Long> {

    List<Centre> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<Centre> findByIdAndActiveTrue(Long id);
}
