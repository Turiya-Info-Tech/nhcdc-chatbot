package com.nhcdc.chatbot.controller;

import com.nhcdc.chatbot.dto.lead.LeadResponseDto;
import com.nhcdc.chatbot.mapper.LeadMapper;
import com.nhcdc.chatbot.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;

    @GetMapping
    public List<LeadResponseDto> getLeads(@RequestParam(required = false) Long centreId) {
        List<com.nhcdc.chatbot.entity.Lead> leads = centreId != null
                ? leadRepository.findByCentreIdOrderByCreatedAtDesc(centreId)
                : leadRepository.findAllByOrderByCreatedAtDesc();
        return leadMapper.toDtoList(leads);
    }
}
