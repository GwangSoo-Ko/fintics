package org.oopscraft.fintics.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.oopscraft.arch4j.core.data.converter.AbstractEnumConverter;
import org.oopscraft.arch4j.core.support.ObjectMapperHolder;
import org.oopscraft.fintics.dao.OrderEntity;

import javax.persistence.Converter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class Order {

    private String orderId;

    private Instant orderAt;

    private Type type;

    private String tradeId;

    private String assetId;

    private String assetName;

    private Kind kind;

    private BigDecimal quantity;

    private BigDecimal price;

    private StrategyResult strategyResult;

    private BigDecimal purchasePrice;

    private BigDecimal realizedProfitAmount;

    private String brokerOrderId;

    private Result result;

    private String errorMessage;

    /**
     * gets symbol
     * @return symbol
     */
    public String getSymbol() {
        return Optional.ofNullable(assetId)
                .map(string -> string.split("\\."))
                .filter(array -> array.length > 1)
                .map(array -> array[1])
                .orElseThrow(() -> new RuntimeException(String.format("invalid assetId[%s]",assetId)));
    }

    public enum Type { BUY, SELL }

    @Converter(autoApply = true)
    public static class TypeConverter extends AbstractEnumConverter<Type> {}

    public enum Kind { LIMIT, MARKET }

    @Converter(autoApply = true)
    public static class KindConverter extends AbstractEnumConverter<Kind> {}

    public enum Result { COMPLETED, FAILED }

    @Converter(autoApply = true)
    public static class ResultConverter extends AbstractEnumConverter<Result> {}

    public static Order from(OrderEntity orderEntity) {
        ObjectMapper objectMapper = ObjectMapperHolder.getObject();

        // strategy result
        StrategyResult strategyResult = null;
        if (orderEntity.getStrategyResultData() != null) {
           try {
               strategyResult = objectMapper.readValue(orderEntity.getStrategyResultData(), StrategyResult.class);
           } catch (JsonProcessingException ignore) {
               log.warn(ignore.getMessage());
           }
        }

        // return
        return Order.builder()
                .orderId(orderEntity.getOrderId())
                .orderAt(orderEntity.getOrderAt())
                .type(orderEntity.getType())
                .tradeId(orderEntity.getTradeId())
                .assetId(orderEntity.getAssetId())
                .assetName(orderEntity.getAssetName())
                .kind(orderEntity.getKind())
                .quantity(orderEntity.getQuantity())
                .price(orderEntity.getPrice())
                .strategyResult(strategyResult)
                .purchasePrice(orderEntity.getPurchasePrice())
                .realizedProfitAmount(orderEntity.getRealizedProfitAmount())
                .brokerOrderId(orderEntity.getBrokerOrderId())
                .result(orderEntity.getResult())
                .errorMessage(orderEntity.getErrorMessage())
                .build();
    }

}
