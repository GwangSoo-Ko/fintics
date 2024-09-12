package org.oopscraft.fintics.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.oopscraft.arch4j.web.common.data.PageableUtils;
import org.oopscraft.fintics.api.v1.dto.StrategyRequest;
import org.oopscraft.fintics.api.v1.dto.StrategyResponse;
import org.oopscraft.fintics.model.Strategy;
import org.oopscraft.fintics.model.StrategySearch;
import org.oopscraft.fintics.service.StrategyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategies")
@PreAuthorize("hasAuthority('API_STRATEGIES')")
@Tag(name = "strategies", description = "Strategies")
@RequiredArgsConstructor
public class StrategiesRestController {

    private final StrategyService strategyService;

    /**
     * gets strategies
     * @param name strategy name
     * @param pageable pageable
     * @return list of strategy
     */
    @GetMapping
    @Operation(description = "gets strategies")
    public ResponseEntity<List<StrategyResponse>> getStrategies(
            @RequestParam(value = "name", required = false)
            @Parameter(description = "name")
                    String name,
            @PageableDefault
            @Parameter(hidden = true)
                    Pageable pageable
    ) {
        StrategySearch strategySearch = StrategySearch.builder()
                .name(name)
                .build();
        Page<Strategy> strategyPage = strategyService.getStrategies(strategySearch, pageable);
        List<StrategyResponse> strategyResponses = strategyPage.getContent().stream()
                .map(StrategyResponse::from)
                .toList();
        long total = strategyPage.getTotalElements();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_RANGE, PageableUtils.toContentRange("strategies", pageable, total))
                .body(strategyResponses);
    }

    /**
     * gets specified strategy
     * @param strategyId strategy id
     * @return strategy info
     */
    @GetMapping("{strategyId}")
    @Operation(description = "gets specified strategy")
    public ResponseEntity<StrategyResponse> getStrategy(
            @PathVariable("strategyId")
            @Parameter(description = "strategy id")
                    String strategyId
    ) {
        StrategyResponse ruleResponse = strategyService.getStrategy(strategyId)
                .map(StrategyResponse::from)
                .orElseThrow();
        return ResponseEntity.ok(ruleResponse);
    }

    /**
     * creates strategy
     * @param strategyRequest strategy request
     * @return created strategy
     */
    @PostMapping
    @PreAuthorize("hasAuthority('API_STRATEGIES_EDIT')")
    @Transactional
    @Operation(description = "gets specified strategy")
    public ResponseEntity<StrategyResponse> createStrategy(
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "strategy request")
                    StrategyRequest strategyRequest
    ) {
        Strategy strategy = Strategy.builder()
                .name(strategyRequest.getName())
                .language(strategyRequest.getLanguage())
                .variables(strategyRequest.getVariables())
                .script(strategyRequest.getScript())
                .build();
        Strategy savedStrategy = strategyService.saveStrategy(strategy);
        return ResponseEntity.ok(StrategyResponse.from(savedStrategy));
    }

    /**
     * modifies strategy
     * @param strategyId strategy id
     * @param strategyRequest strategy info
     * @return changed strategy info
     */
    @PutMapping("{strategyId}")
    @PreAuthorize("hasAuthority('API_STRATEGIES_EDIT')")
    @Transactional
    @Operation(description = "modifies strategy")
    public ResponseEntity<StrategyResponse> modifyStrategy(
            @PathVariable("strategyId")
            @Parameter(description = "strategy id")
                    String strategyId,
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "strategy request")
                    StrategyRequest strategyRequest
    ) {
        Strategy strategy = strategyService.getStrategy(strategyId).orElseThrow();
        strategy.setName(strategyRequest.getName());
        strategy.setLanguage(strategyRequest.getLanguage());
        strategy.setVariables(strategyRequest.getVariables());
        strategy.setScript(strategyRequest.getScript());
        Strategy savedStrategy = strategyService.saveStrategy(strategy);
        return ResponseEntity.ok(StrategyResponse.from(savedStrategy));
    }

    /**
     * deletes strategy
     * @param strategyId strategy id
     */
    @DeleteMapping("{strategyId}")
    @PreAuthorize("hasAuthority('API_STRATEGIES_EDIT')")
    @Transactional
    @Operation(description = "deletes strategy")
    public void deleteStrategy(
            @PathVariable("strategyId")
            @Parameter(description = "strategy id")
                    String strategyId
    ) {
        strategyService.deleteStrategy(strategyId);
    }

}
