package com.ilham.personal_finance_api.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/flyway")
@RequiredArgsConstructor
public class FlyWayController {

    private final Flyway flyway;

    @GetMapping("/info")
    public List<Map<String, Object>> getMigrationInfo() {
        MigrationInfoService infoService = flyway.info();
        return Arrays.stream(infoService.all())
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(MigrationInfo info) {
        return Map.of(
                "version",          info.getVersion()     != null ? info.getVersion().getVersion() : "repeatable",
                "description",      info.getDescription() != null ? info.getDescription() : "",
                "type",             info.getType().name(),
                "state",            info.getState().getDisplayName(),
                "installedOn",      info.getInstalledOn() != null ? info.getInstalledOn().toString() : "pending",
                "executionTime_ms", info.getExecutionTime() != null ? info.getExecutionTime() : 0,
                "script",           info.getScript()
        );
    }
}
