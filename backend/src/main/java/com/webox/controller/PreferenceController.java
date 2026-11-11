package com.webox.controller;

import com.webox.common.ApiResponse;
import com.webox.common.RequestContext;
import com.webox.dto.Dtos.PreferenceRequest;
import com.webox.dto.Dtos.PreferenceView;
import com.webox.service.PreferenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<PreferenceView> get() {
        return ApiResponse.ok(preferenceService.get(RequestContext.userId()));
    }

    @PutMapping
    public ApiResponse<PreferenceView> update(@RequestBody PreferenceRequest request) {
        return ApiResponse.ok(preferenceService.update(RequestContext.userId(), request));
    }
}
