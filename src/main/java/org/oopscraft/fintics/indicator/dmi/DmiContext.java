package org.oopscraft.fintics.indicator.dmi;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.oopscraft.fintics.indicator.IndicatorContext;

@SuperBuilder
@Getter
public class DmiContext extends IndicatorContext {

    public static final DmiContext DEFAULT = DmiContext.of(14);

    private final int period;

    public static DmiContext of(int period) {
        return DmiContext.builder()
                .period(period)
                .build();
    }

}
