package org.oopscraft.fintics.trade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.oopscraft.fintics.dao.TradeEntity;
import org.oopscraft.fintics.dao.TradeRepository;
import org.oopscraft.fintics.model.Trade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeSynchronizer {

    private final TradeRepository tradeRepository;

    private final TradeThreadManager tradeThreadManager;

    @Scheduled(initialDelay = 1_000, fixedDelay = 10_000)
    @Transactional(readOnly = true)
    public void synchronize() {
        log.info("TradeSynchronizer.synchronize.");
        List<TradeEntity> tradeEntities = tradeRepository.findAll();

        // deleted trade thread
        for(Thread tradeThread : tradeThreadManager.getTradeThreads()) {
            String tradeId = tradeThread.getName();
            boolean notExists = tradeEntities.stream()
                    .noneMatch(tradeEntity ->
                            tradeEntity.getTradeId().equals(tradeId));
            if(notExists) {
                tradeThreadManager.stopTradeThread(tradeId);
                sleep();
            }
        }

        // start trade thread if enabled
        for(TradeEntity tradeEntity : tradeEntities) {
            Trade trade = Trade.from(tradeEntity);
            if(trade.isEnabled()) {
                TradeThread tradeThread = tradeThreadManager.getTradeThread(trade.getTradeId()).orElse(null);
                if(tradeThread == null) {
                    tradeThreadManager.startTradeThread(trade);
                    sleep();
                    continue;
                }
                if(tradeThread.getTradeRunnable().getInterval().intValue() != trade.getInterval().intValue()) {
                    tradeThreadManager.restartTradeThread(trade);
                    sleep();
                }
            }else{
                if(tradeThreadManager.isTradeThreadRunning(trade.getTradeId())) {
                    tradeThreadManager.stopTradeThread(trade.getTradeId());
                    sleep();
                }
            }
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
