package org.oopscraft.fintics.indicator.sma;

import org.oopscraft.fintics.indicator.IndicatorCalculator;
import org.oopscraft.fintics.model.Ohlcv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SmaCalculator extends IndicatorCalculator<SmaContext, Sma> {

    public SmaCalculator(SmaContext context) {
        super(context);
    }

    @Override
    public List<Sma> calculate(List<Ohlcv> series) {
        List<BigDecimal> closePrices = series.stream()
                .map(Ohlcv::getClosePrice)
                .toList();
        List<BigDecimal> smaValues = smas(closePrices, getContext().getPeriod(), getContext().getMathContext());
        List<Sma> smas = new ArrayList<>();
        for (int i = 0; i < smaValues.size(); i ++ ) {
            smas.add(Sma.builder()
                    .dateTime(series.get(i).getDateTime())
                    .value(smaValues.get(i))
                    .build());
        }
        return smas;
    }

}
