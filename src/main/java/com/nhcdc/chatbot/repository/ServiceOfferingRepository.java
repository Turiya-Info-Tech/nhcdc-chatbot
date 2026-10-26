package com.nhcdc.chatbot.repository;

import com.nhcdc.chatbot.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<ServiceOffering> findByIdAndActiveTrue(Long id);
}
