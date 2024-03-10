package org.oopscraft.fintics.calculator;

import org.oopscraft.fintics.model.Ohlcv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MacdCalculator extends Calculator<MacdContext, Macd> {

    public MacdCalculator(MacdContext context) {
        super(context);
    }

    @Override
    public List<Macd> calculate(List<Ohlcv> series) {
        List<BigDecimal> closePrices = series.stream()
                .map(Ohlcv::getClosePrice)
                .toList();

        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> oscillators = new ArrayList<>();

        // Calculate MACD line
        List<BigDecimal> shortTermEmas = emas(closePrices, getContext().getShortPeriod(), getContext().getMathContext());
        List<BigDecimal> longTermEmas = emas(closePrices, getContext().getLongPeriod(), getContext().getMathContext());

        for (int i = 0; i < longTermEmas.size(); i++) {
            BigDecimal value = shortTermEmas.get(i).subtract(longTermEmas.get(i));
            values.add(value);
        }

        // ine using MACD line
        List<BigDecimal> signals = emas(values, getContext().getSignalPeriod(), getContext().getMathContext());

        // oscillator
        for (int i = 0; i < values.size(); i++) {
            BigDecimal oscillator = values.get(i).subtract(signals.get(i));
            oscillators.add(oscillator);
        }

        // return macds
        List<Macd> macds = new ArrayList<>();
        for(int i = 0; i < values.size(); i ++ ) {
            Macd macd = Macd.builder()
                    .dateTime(series.get(i).getDateTime())
                    .value(values.get(i).setScale(2, RoundingMode.HALF_UP))
                    .signal(signals.get(i).setScale(2, RoundingMode.HALF_UP))
                    .oscillator(oscillators.get(i).setScale(2, RoundingMode.HALF_UP))
                    .build();
            macds.add(macd);
        }
        return macds;
    }

}
