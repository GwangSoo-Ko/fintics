package org.chomookun.fintics.core.trade.executor;

import ch.qos.logback.classic.Logger;
import lombok.Builder;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.chomookun.arch4j.core.notification.NotificationService;
import org.chomookun.fintics.core.asset.model.Asset;
import org.chomookun.fintics.core.broker.model.Balance;
import org.chomookun.fintics.core.broker.model.BalanceAsset;
import org.chomookun.fintics.core.basket.model.Basket;
import org.chomookun.fintics.core.basket.model.BasketAsset;
import org.chomookun.fintics.core.broker.client.BrokerClient;
import org.chomookun.fintics.core.asset.AssetService;
import org.chomookun.fintics.core.basket.BasketService;
import org.chomookun.fintics.core.ohlcv.model.Ohlcv;
import org.chomookun.fintics.core.ohlcv.OhlcvService;
import org.chomookun.fintics.core.order.model.Order;
import org.chomookun.fintics.core.broker.model.OrderBook;
import org.chomookun.fintics.core.order.OrderService;
import org.chomookun.fintics.core.strategy.model.Strategy;
import org.chomookun.fintics.core.strategy.runner.StrategyResult;
import org.chomookun.fintics.core.strategy.runner.StrategyRunner;
import org.chomookun.fintics.core.strategy.runner.StrategyRunnerContext;
import org.chomookun.fintics.core.strategy.runner.StrategyRunnerFactory;
import org.chomookun.fintics.core.trade.model.Trade;
import org.chomookun.fintics.core.trade.model.TradeAsset;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Builder
public class TradeExecutor {

    private final PlatformTransactionManager transactionManager;

    private final BasketService basketService;

    private final AssetService assetService;

    private final OhlcvService ohlcvService;

    private final OrderService orderService;

    private final NotificationService notificationService;

    private final StrategyRunnerFactory strategyRunnerFactory;

    private final OhlcvCacheManager ohlcvCacheManager;

    @Setter
    private Logger log;

    private final Map<String, StrategyResult> strategyResultMap = new HashMap<>();

    private final Map<String, Integer> strategyResultValueMatchCountMap = new HashMap<>();

    @Setter
    private TradeAssetStore tradeAssetStore;

    public void execute(Trade trade, Strategy strategy, LocalDateTime dateTime, BrokerClient brokerClient) throws InterruptedException {
        log.info("=".repeat(80));
        log.info("[{}] execute trade", trade.getName());

        // time zone
        ZoneId timeZone = brokerClient.getDefinition().getTimezone();
        log.info("[{}] market timeZone: {}", trade.getName(), timeZone);
        log.info("[{}] market dateTime: {}", trade.getName(), dateTime);

        // checks start,end time
        if (!isOperatingTime(trade, dateTime)) {
            log.info("[{}] not operating time - {} ~ {}", trade.getName(), trade.getStartTime(), trade.getEndTime());
            return;
        }

        // check market opened
        if(!brokerClient.isOpened(dateTime)) {
            log.info("[{}] market not opened.", trade.getName());
            return;
        }

        // basket
        Basket basket = basketService.getBasket(trade.getBasketId()).orElseThrow();
        log.info("[{}] basket: {}", trade.getName(), basket.getName());

        // checks buy condition
        for (BasketAsset basketAsset : basket.getBasketAssets()) {
            try {
                // prevent mixed message by overhead
                Thread.sleep(100);

                // logging
                log.info("-".repeat(80));
                log.info("[{} - {}] check asset", basketAsset.getAssetId(), basketAsset.getName());

                // daily ohlcvs
                List<Ohlcv> dailyOhlcvs = brokerClient.getDailyOhlcvs(basketAsset);
                List<Ohlcv> previousDailyOhlcvs = getPreviousDailyOhlcvs(basketAsset.getAssetId(), dailyOhlcvs, dateTime);
                dailyOhlcvs.addAll(previousDailyOhlcvs);
                TradeValidator.validateOhlcvs(dailyOhlcvs);

                // minute ohlcvs
                List<Ohlcv> minuteOhlcvs = brokerClient.getMinuteOhlcvs(basketAsset);
                List<Ohlcv> previousMinuteOhlcvs = getPreviousMinuteOhlcvs(basketAsset.getAssetId(), minuteOhlcvs, dateTime);
                minuteOhlcvs.addAll(previousMinuteOhlcvs);
                TradeValidator.validateOhlcvs(minuteOhlcvs);

                // creates trade asset
                TradeAsset tradeAsset = tradeAssetStore.load(trade.getTradeId(), basketAsset.getAssetId())
                        .orElse(TradeAsset.builder()
                                .tradeId(trade.getTradeId())
                                .assetId(basketAsset.getAssetId())
                                .build());
                tradeAsset.setName(basketAsset.getName());
                tradeAsset.setMarket(basketAsset.getMarket());
                tradeAsset.setType(basketAsset.getType());
                tradeAsset.setExchange(basketAsset.getExchange());
                tradeAsset.setMarketCap(basketAsset.getMarketCap());
                tradeAsset.setDateTime(minuteOhlcvs.get(0).getDateTime());
                tradeAsset.setPreviousClose(dailyOhlcvs.get(1).getClose());
                tradeAsset.setOpen(dailyOhlcvs.get(0).getOpen());
                tradeAsset.setClose(minuteOhlcvs.get(0).getClose());
                tradeAsset.setVolume(dailyOhlcvs.get(0).getVolume());
                tradeAsset.setDailyOhlcvs(dailyOhlcvs);
                tradeAsset.setMinuteOhlcvs(minuteOhlcvs);

                // check enabled
                if (!basketAsset.isEnabled()) {
                    tradeAsset.setMessage(null);
                    if (tradeAssetStore != null) {
                        tradeAssetStore.save(tradeAsset);
                    }
                    continue;
                }

                // logging
                log.info("[{} - {}] dailyOhlcvs({}):{}", tradeAsset.getAssetId(), tradeAsset.getName(), tradeAsset.getDailyOhlcvs().size(), tradeAsset.getDailyOhlcvs().isEmpty() ? null : tradeAsset.getDailyOhlcvs().get(0));
                log.info("[{} - {}] minuteOhlcvs({}):{}", tradeAsset.getAssetId(), tradeAsset.getName(), tradeAsset.getMinuteOhlcvs().size(), tradeAsset.getMinuteOhlcvs().isEmpty() ? null : tradeAsset.getMinuteOhlcvs().get(0));

                // balance
                Balance balance = brokerClient.getBalance();
                BalanceAsset balanceAsset = balance.getBalanceAsset(basketAsset.getAssetId()).orElse(null);

                // order book
                OrderBook orderBook = brokerClient.getOrderBook(basketAsset);
                TradeValidator.validateOrderBook(orderBook);

                // executes trade asset decider
                log.info("[{} - {}] strategy start", basketAsset.getAssetId(), basketAsset.getName());
                StrategyRunnerContext strategyRunnerContext = StrategyRunnerContext.builder()
                        .strategy(strategy)
                        .variables(trade.getStrategyVariables())
                        .dateTime(dateTime)
                        .basketAsset(basketAsset)
                        .tradeAsset(tradeAsset)
                        .balanceAsset(balanceAsset)
                        .orderBook(orderBook)
                        .build();
                StrategyRunner strategyRunner = strategyRunnerFactory.getObject(strategyRunnerContext);
                strategyRunner.setLog(log);
                Instant strategyStartTime = Instant.now();
                StrategyResult strategyResult = strategyRunner.run();
                tradeAsset.setStrategyResult(strategyResult);
                log.info("[{} - {}] strategy execution elapsed:{}", basketAsset.getAssetId(), basketAsset.getName(), Duration.between(strategyStartTime, Instant.now()));
                log.info("[{} - {}] strategy result: {}", basketAsset.getAssetId(), basketAsset.getName(), strategyResult);

                // save trade asset to store
                if (tradeAssetStore != null) {
                    tradeAssetStore.save(tradeAsset);
                }

                // check strategy result and count
                StrategyResult previousStrategyResult = strategyResultMap.get(basketAsset.getAssetId());
                int strategyResultValueMatchCount = strategyResultValueMatchCountMap.getOrDefault(basketAsset.getAssetId(), 0);
                if (Objects.equals(strategyResult, previousStrategyResult)) {
                    strategyResultValueMatchCount ++;
                } else {
                    strategyResultValueMatchCount = 1;
                }
                strategyResultMap.put(basketAsset.getAssetId(), strategyResult);
                strategyResultValueMatchCountMap.put(basketAsset.getAssetId(), strategyResultValueMatchCount);

                // checks threshold exceeded
                log.info("[{} - {}] strategyResultValueMatchCount: {}", basketAsset.getAssetId(), basketAsset.getName(), strategyResultValueMatchCount);
                if (strategyResultValueMatchCount < trade.getThreshold()) {
                    log.info("[{} - {}] threshold has not been exceeded yet - threshold is {}", basketAsset.getAssetId(), basketAsset.getName(), trade.getThreshold());
                    continue;
                }

                //===============================================
                // 1. null is no operation
                //===============================================
                if (strategyResult == null) {
                    continue;
                }

                //===============================================
                // 2. apply holding weight
                //===============================================
                // defines
                BigDecimal investAmount = trade.getInvestAmount();
                BigDecimal holdingWeight = basketAsset.getHoldingWeight();
                BigDecimal holdingWeightAmount = investAmount
                        .divide(BigDecimal.valueOf(100), MathContext.DECIMAL32)
                        .multiply(holdingWeight)
                        .setScale(2, RoundingMode.HALF_UP);

                StrategyResult.Action action = strategyResult.getAction();
                BigDecimal position = strategyResult.getPosition();
                BigDecimal positionAmount = holdingWeightAmount
                        .multiply(position)
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal currentOwnedAmount = balance.getBalanceAsset(basketAsset.getAssetId())
                        .map(BalanceAsset::getValuationAmount)
                        .orElse(BigDecimal.ZERO);
                BigDecimal currentOwnedQuantity = balance.getBalanceAsset(basketAsset.getAssetId())
                        .map(BalanceAsset::getQuantity)
                        .orElse(BigDecimal.ZERO);

                // buy
                if (action == StrategyResult.Action.BUY) {
                    BigDecimal buyAmount = positionAmount.subtract(currentOwnedAmount);
                    if (buyAmount.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal buyPrice = calculateBuyPrice(orderBook);
                        BigDecimal buyQuantity = buyAmount
                                .divide(buyPrice, MathContext.DECIMAL64)
                                .setScale(brokerClient.getQuantityScale(), RoundingMode.HALF_UP);
                        // check minimum order amount
                        boolean canBuy = brokerClient.isAvailablePriceAndQuantity(buyPrice, buyQuantity);
                        if (canBuy) {
                            buyTradeAsset(brokerClient, trade, tradeAsset, buyQuantity, buyPrice, strategyResult);
                        }
                    }
                    continue;
                }

                // sell
                if (action == StrategyResult.Action.SELL) {
                    BigDecimal sellAmount = currentOwnedAmount.subtract(positionAmount);
                    if (sellAmount.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal sellPrice = calculateSellPrice(orderBook);
                        BigDecimal sellQuantity = sellAmount
                                .divide(sellPrice, MathContext.DECIMAL64)
                                .setScale(brokerClient.getQuantityScale(), RoundingMode.HALF_UP);
                        // if sell quantity exceed current owned quantity, sell current owned quantity
                        if (sellQuantity.compareTo(currentOwnedQuantity) > 0) {
                            sellQuantity = currentOwnedQuantity;
                        }
                        // check minimum order amount
                        boolean canSell = brokerClient.isAvailablePriceAndQuantity(sellPrice, sellQuantity);
                        if (canSell) {
                            sellTradeAsset(brokerClient, trade, tradeAsset, sellQuantity, sellPrice, strategyResult, balanceAsset);
                        }
                    }
                    continue;
                }

            } catch (Throwable e) {
                log.error(e.getMessage(), e);
                sendErrorNotificationIfEnabled(trade, basketAsset, e);
            }
        }
    }

    private boolean isOperatingTime(Trade trade, LocalDateTime dateTime) {
        if(trade.getStartTime() == null || trade.getEndTime() == null) {
            return false;
        }
        LocalTime startTime = trade.getStartTime();
        LocalTime endTime = trade.getEndTime();
        LocalTime currentTime = dateTime.toLocalTime();
        if (startTime.isAfter(endTime)) {
            return !(currentTime.isBefore(startTime) || currentTime.equals(startTime))
                    || !(currentTime.isAfter(endTime) || currentTime.equals(endTime));
        } else {
            return (currentTime.isAfter(startTime) || currentTime.equals(startTime))
                    && (currentTime.isBefore(endTime) || currentTime.equals(endTime));
        }
    }

    private List<Ohlcv> getPreviousDailyOhlcvs(String assetId, List<Ohlcv> ohlcvs, LocalDateTime dateTime) {
        LocalDateTime dateTimeFrom = dateTime.minusYears(3);
        LocalDateTime dateTimeTo = ohlcvs.isEmpty()
                ? dateTime
                : ohlcvs.get(ohlcvs.size()-1).getDateTime().minusDays(1);
        if(dateTimeTo.isBefore(dateTimeFrom)) {
            return new ArrayList<>();
        }
        return ohlcvCacheManager.getDailyOhlcvs(assetId, dateTimeFrom, dateTimeTo);
    }

    private List<Ohlcv> getPreviousMinuteOhlcvs(String assetId, List<Ohlcv> ohlcvs, LocalDateTime dateTime) {
        LocalDateTime dateTimeFrom = dateTime.minusMonths(1);
        LocalDateTime dateTimeTo = ohlcvs.isEmpty()
                ? dateTime
                : ohlcvs.get(ohlcvs.size()-1).getDateTime().minusMinutes(1);
        if(dateTimeTo.isBefore(dateTimeFrom)) {
            return new ArrayList<>();
        }
        return ohlcvCacheManager.getMinuteOhlcvs(assetId, dateTimeFrom, dateTimeTo);
    }

    private TradeAsset loadTradeAsset() {
        return null;
    }

    private void persistTradeAsset() {

    }

    private BigDecimal calculateBuyPrice(OrderBook orderBook) throws InterruptedException {
        BigDecimal price = orderBook.getAskPrice();
        BigDecimal tickPrice = orderBook.getTickPrice();
        // max competitive price (매도 호가 에서 1틱 유리한 가격 설정)
        if(tickPrice != null) {
            price = price.subtract(tickPrice);
        }
        return price.max(orderBook.getBidPrice());
    }

    private BigDecimal calculateSellPrice(OrderBook orderBook) throws InterruptedException {
        BigDecimal price = orderBook.getBidPrice();
        BigDecimal tickPrice = orderBook.getTickPrice();
        // min competitive price (매수 호가 에서 1틱 유리한 가격 설정)
        if(tickPrice != null) {
            price = price.add(tickPrice);
        }
        return price.min(orderBook.getAskPrice());
    }

    private void buyTradeAsset(BrokerClient brokerClient, Trade trade, TradeAsset tradeAsset, BigDecimal quantity, BigDecimal price, StrategyResult strategyResult) throws InterruptedException {
        Order order = Order.builder()
                .orderAt(Instant.now())
                .type(Order.Type.BUY)
                .kind(trade.getOrderKind())
                .tradeId(trade.getTradeId())
                .assetId(tradeAsset.getAssetId())
                .assetName(tradeAsset.getName())
                .quantity(quantity)
                .price(price)
                .strategyResult(strategyResult)
                .build();
        log.info("[{}] buyTradeAsset: {}", tradeAsset.getName(), order);
        try {
            // check waiting order exists
            Order waitingOrder = brokerClient.getWaitingOrders().stream()
                    .filter(element ->
                            Objects.equals(element.getSymbol(), order.getSymbol())
                                    && element.getType() == order.getType())
                    .findFirst()
                    .orElse(null);
            if (waitingOrder != null) {
                // if limit type order, amend order
                if (waitingOrder.getKind() == Order.Kind.LIMIT) {
                    if (!waitingOrder.getPrice().equals(price)) {
                        waitingOrder.setPrice(price);
                        log.info("[{}] amend buy order:{}", tradeAsset.getName(), waitingOrder);
                        brokerClient.amendOrder(tradeAsset, waitingOrder);
                    }
                }
                return;
            }

            // withdraws buy amount in cash
            if (trade.getCashAssetId() != null) {
                try {
                    BigDecimal buyAmount = quantity.multiply(price);
                    withdrawBuyAmountFromCash(brokerClient, trade, buyAmount);
                } catch (Exception ignore) {
                    log.warn(ignore.getMessage());
                }
            }

            // submit buy order
            brokerClient.submitOrder(tradeAsset, order);
            order.setResult(Order.Result.COMPLETED);

            // alarm
            sendOrderNotificationIfEnabled(trade, order);

        } catch (Throwable e) {
            order.setResult(Order.Result.FAILED);
            order.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            saveTradeOrder(order);
        }
    }

    private void sellTradeAsset(BrokerClient brokerClient, Trade trade, TradeAsset tradeAsset, BigDecimal quantity, BigDecimal price, StrategyResult strategyResult, BalanceAsset balanceAsset) throws InterruptedException {
        Order order = Order.builder()
                .orderAt(Instant.now())
                .type(Order.Type.SELL)
                .kind(trade.getOrderKind())
                .tradeId(trade.getTradeId())
                .assetId(tradeAsset.getAssetId())
                .assetName(tradeAsset.getName())
                .quantity(quantity)
                .price(price)
                .strategyResult(strategyResult)
                .build();
        log.info("[{}] sellTradeAsset: {}", tradeAsset.getName(), order);
        // purchase price, realized amount
        if (balanceAsset.getPurchasePrice() != null) {
            order.setPurchasePrice(balanceAsset.getPurchasePrice());
            BigDecimal realizedProfitAmount = price.subtract(balanceAsset.getPurchasePrice())
                    .multiply(quantity)
                    .setScale(4, RoundingMode.FLOOR);
            order.setRealizedProfitAmount(realizedProfitAmount);
        }
        try {
            // check waiting order exists
            Order waitingOrder = brokerClient.getWaitingOrders().stream()
                    .filter(element ->
                            Objects.equals(element.getSymbol(), order.getSymbol())
                                    && element.getType() == order.getType())
                    .findFirst()
                    .orElse(null);
            if (waitingOrder != null) {
                // if limit type order, amend order
                if (waitingOrder.getKind() == Order.Kind.LIMIT) {
                    if (!waitingOrder.getPrice().equals(price)) {
                        waitingOrder.setPrice(price);
                        log.info("[{}] amend sell order:{}", tradeAsset.getName(), waitingOrder);
                        brokerClient.amendOrder(tradeAsset, waitingOrder);
                    }
                }
                return;
            }
            // submit sell order
            brokerClient.submitOrder(tradeAsset, order);
            order.setResult(Order.Result.COMPLETED);
            // deposit sell amount in cash
            if (trade.getCashAssetId() != null) {
                try {
                    BigDecimal sellAmount = quantity.multiply(price);
                    depositSellAmountToCash(brokerClient, trade, sellAmount);
                } catch (Exception ignore) {
                    log.warn(ignore.getMessage());
                }
            }
            // alarm
            sendOrderNotificationIfEnabled(trade, order);
        } catch (Throwable e) {
            order.setResult(Order.Result.FAILED);
            order.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            saveTradeOrder(order);
        }
    }

    private void saveTradeOrder(Order order) {
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager, transactionDefinition);
        transactionTemplate.executeWithoutResult(transactionStatus ->
                orderService.saveOrder(order));
    }

    private void sendErrorNotificationIfEnabled(Trade trade, Asset asset, Throwable t) {
        if(trade.getNotifierId() != null && !trade.getNotifierId().isBlank()) {
            if (trade.isNotifyOnError()) {
                String subject = String.format("[%s - %s] Error", trade.getName(), asset != null ? asset.getName() : "");
                Throwable rootCause = ExceptionUtils.getRootCause(t);
                String content = rootCause.getClass().getName() + "\n" + rootCause.getMessage();
                notificationService.sendNotification(trade.getNotifierId(), subject, content, null, true);
            }
        }
    }

    private void sendOrderNotificationIfEnabled(Trade trade, Order order) {
        if (trade.isNotifyOnOrder()) {
            if (trade.getNotifierId() != null && !trade.getNotifierId().isBlank()) {
                // subject
                StringBuilder subject = new StringBuilder();
                subject.append(String.format("[%s - %s] %s", trade.getName(), order.getAssetName(), order.getType()));
                // content
                StringBuilder content = new StringBuilder();
                content.append(String.format("- kind: %s", order.getKind())).append('\n');
                content.append(String.format("- price: %s", order.getPrice())).append('\n');
                content.append(String.format("- quantity: %s", order.getQuantity())).append('\n');
                content.append(String.format("- strategyResult: %s", order.getStrategyResult())).append('\n');
                notificationService.sendNotification(trade.getNotifierId(), subject.toString(), content.toString(), null, false);
            }
        }
    }

    void withdrawBuyAmountFromCash(BrokerClient brokerClient, Trade trade, BigDecimal buyAmount) throws InterruptedException {
        // 계좌 정보 조회
        Balance balance = brokerClient.getBalance();

        // 증거금 40% 로 가정 후 추가 (일부 파상 상품의 경우 증거금 부족 으로 매수 안되는 경우 있음)
        buyAmount = buyAmount.multiply(BigDecimal.valueOf(1.4));

        // 매수 미체결 주문 금액 추가 (예수금 에서 아직 차감 되지 않은 상태)
        BigDecimal waitingBuyAmount = brokerClient.getWaitingOrders().stream()
                .filter(order -> order.getType() == Order.Type.BUY)
                .map(order -> order.getPrice().multiply(order.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        buyAmount = buyAmount.add(waitingBuyAmount);

        // 매수 금액 이상 현금이 남아 있는 경우 제외
        if (balance.getCashAmount().compareTo(buyAmount) > 0) {
            return;
        }

        // 부족한 금액
        BigDecimal insufficientAmount = buyAmount.subtract(balance.getCashAmount());

        // 부족한 금액 만큼 cash asset 매도
        Asset cashAsset = assetService.getAsset(trade.getCashAssetId())
                .orElseThrow();
        OrderBook cashAssetOrderBook = brokerClient.getOrderBook(cashAsset);
        BigDecimal cashAssetAskPrice = cashAssetOrderBook.getAskPrice();
        BigDecimal cashAssetTickPrice = cashAssetOrderBook.getTickPrice();
        BigDecimal cashAssetSellPrice = cashAssetAskPrice.subtract(cashAssetTickPrice);
        BigDecimal cashAssetSellQuantity = insufficientAmount.divide(cashAssetSellPrice, MathContext.DECIMAL32)
                .setScale(0, RoundingMode.CEILING);

        // 현재 보유 수량 체크 후 필요한 수량 이상인 경우 매도
        BalanceAsset cashBalanceAsset = balance.getBalanceAsset(cashAsset.getAssetId()).orElse(null);
        if (cashBalanceAsset != null) {
            // 주문 가능 수량 으로 조정 후 매도
            cashAssetSellQuantity = cashAssetSellQuantity.min(cashBalanceAsset.getOrderableQuantity());
            if (cashAssetSellQuantity.compareTo(BigDecimal.ZERO) > 0) {
                Order order = Order.builder()
                        .orderAt(Instant.now())
                        .type(Order.Type.SELL)
                        .kind(Order.Kind.LIMIT)     // 해외의 경우 시장가 주문 시 체결이 안되는 경우가 많음
                        .assetId(cashAsset.getAssetId())
                        .quantity(cashAssetSellQuantity)
                        .price(cashAssetSellPrice)
                        .build();
                brokerClient.submitOrder(cashAsset, order);

                // 일정 시간 매수 완료 시 까지 대기
                Thread.sleep(5_000);
            }
        }
    }

    void depositSellAmountToCash(BrokerClient brokerClient, Trade trade, BigDecimal sellAmount) throws InterruptedException {
        // 설정 된 현금 대기 비중 계산
        BigDecimal cashBufferWeight = trade.getCashBufferWeight();
        BigDecimal cashBufferPercentage = cashBufferWeight.divide(BigDecimal.valueOf(100), MathContext.DECIMAL32);
        BigDecimal cashBufferAmount = trade.getInvestAmount().multiply(cashBufferPercentage);

        // 현재 잔고 + 매도 금엑 합산이 설정된 현금 대기 금액 이하인 경우 제외
        Balance balance = brokerClient.getBalance();
        BigDecimal expectedCashAmount = balance.getCashAmount().add(sellAmount);
        if (expectedCashAmount.compareTo(cashBufferAmount) < 0) {
            return;
        }

        // 현금 대기 금액 초과 된 금액
        BigDecimal overflowAmount = expectedCashAmount.subtract(cashBufferAmount);

        // 설정된 현금 대기 금액을 초과 하는 금액은 현금성 자산(cash asset) 매수
        Asset cashAsset = assetService.getAsset(trade.getCashAssetId())
                .orElseThrow();
        OrderBook cashAssetOrderBook = brokerClient.getOrderBook(cashAsset);
        BigDecimal cashAssetBidPrice = cashAssetOrderBook.getBidPrice();
        BigDecimal cashAssetTickPrice = cashAssetOrderBook.getTickPrice();
        BigDecimal cashAssetBuyPrice = cashAssetBidPrice.add(cashAssetTickPrice);
        BigDecimal cashAssetBuyQuantity = overflowAmount.divide(cashAssetBuyPrice, MathContext.DECIMAL32)
                .setScale(0, RoundingMode.DOWN);

        // 현금성 종목 매수 수량이 있을 경우 매수
        if (cashAssetBuyQuantity.compareTo(BigDecimal.ZERO) > 0) {
            Order order = Order.builder()
                    .orderAt(Instant.now())
                    .type(Order.Type.BUY)
                    .kind(Order.Kind.LIMIT)     // 해외의 경우 시장가 주문 시 체결이 안되는 경우가 많음
                    .assetId(cashAsset.getAssetId())
                    .quantity(cashAssetBuyQuantity)
                    .price(cashAssetBuyPrice)
                    .build();
            brokerClient.submitOrder(cashAsset, order);
        }
    }

}
