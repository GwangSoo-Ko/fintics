package org.oopscraft.fintics.indicator;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.oopscraft.fintics.indicator.Indicator;

import java.math.BigDecimal;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class BollingerBand extends Indicator {

    private BigDecimal middle;

    private BigDecimal upper;

    private BigDecimal lower;

    private BigDecimal width;

    private BigDecimal percentB;

}
