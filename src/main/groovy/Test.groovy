import groovy.transform.ToString
import org.jetbrains.annotations.NotNull
import org.oopscraft.fintics.calculator.*
import org.oopscraft.fintics.model.Ohlcv
import org.oopscraft.fintics.trade.Tool

import java.time.LocalTime

def hold = null

def priceZScore = Tool.zScore(assetIndicator.getOhlcvs(Ohlcv.Type.MINUTE,1).take(20).collect{it.closePrice})

List<Ohlcv> ohlcvs = assetIndicator.getOhlcvs(Ohlcv.Type.MINUTE, 3)
def ohlcv = ohlcvs.first()
List<Ohlcv> tideOhlcvs = assetIndicator.getOhlcvs(Ohlcv.Type.DAILY, 1)

// ema
List<Ema> emas = Tool.calculate(ohlcvs, EmaContext.of(120))
def ema = emas.first()

// 기본
List<Dmi> dmis = Tool.calculate(ohlcvs, DmiContext.DEFAULT)
def dmi = dmis.first()
def dmiAdxs = dmis.collect{it.adx}

List<Macd> macds = Tool.calculate(ohlcvs, MacdContext.DEFAULT)
def macd = macds.first()
def macdOscillators = macds.collect{it.oscillator}

List<Macd> tideMacds = Tool.calculate(tideOhlcvs, MacdContext.DEFAULT)
def tideMacd = tideMacds.first()
def tideMacdOscillators = tideMacds.collect{it.oscillator}



List<BollingerBand> bollingerBands = Tool.calculate(ohlcvs, BollingerBandContext.DEFAULT)
def bollingerBand = bollingerBands.first()

List<Rsi> rsis = Tool.calculate(ohlcvs, RsiContext.DEFAULT)
def rsi = rsis.first()

List<Cci> ccis = Tool.calculate(ohlcvs, CciContext.of(20,10))
def cci = ccis.first()

List<Atr> atrs = Tool.calculate(ohlcvs, AtrContext.DEFAULT)
def atr = atrs.first()


log.info("############### cci: {}", cci)
if (priceZScore > 1.0) {
    if (macd.value < 0 && macd.oscillator > 0) {
        hold = 1
    }
}

if (priceZScore < -1.0) {
    if (macd.value > 0 && macd.oscillator < 0) {
        hold = 0
    }
}


//// test-1: 상승장(20%) 코스닥레버+인버: 4.7%(2.5~3.4), 코스닥레버: 13%(2.5~3.4)
//if (dmi.adx > 25) {
//    if (macd.oscillator > 0) {
//        hold = 1
//    }
//    if (macd.oscillator < 0) {
//        hold = 0
//    }
//}

//// 결과: 코스닥레버:10%(2.5~3.4), 코스피레버:9%(2.5~3.4)
//if (dmi.adx > 25 && bollingerBand.width > 0.01) {
//    if (macd.oscillator > 0) {
//        hold = 1
//    }
//    if (macd.oscillator < 0) {
//        hold = 0
//    }
//}

//// test-1:
//if (priceZScore >= 1.5) {
//    if (rsi.value < 30 && rsi.value > rsi.signal) {
//        hold = 1
//    }
//}
//if (priceZScore <= -1.5) {
//    if (rsi.value > 70 && rsi.value < rsi.signal) {
//        hold = 0
//    }
//}

//// test - 코스닥 레버: 15%
//if (priceZScore > 1.5) {
//    if (dmi.adx > 25) {
//        if (macd.value < 0 && macd.oscillator > 0) {
//            hold = 1
//        }
//        if (rsi.value < 30 && rsi.value > rsi.signal) {
//            hold = 1
//        }
//    }
//}
//if (priceZScore < -1.5) {
//    if (dmi.adx > 25) {
//        if (macd.value > 0 && macd.oscillator < 0) {
//            hold = 0
//        }
//        if (rsi.value > 70 && rsi.value < rsi.signal) {
//            hold = 0
//        }
//    }
//}


if (tideMacd.value < 0) {
    log.info("############ tideMacd.value: {}", tideMacd.value)
    //hold = 0
}










//if (tideMacd.oscillator > 0) {
//    if (priceZScore < -2.0) {
//        if (rsi.value >= 70 && rsi.value < rsi.signal) {
//            hold = 0
//        }
//    }
//    if (priceZScore > 2.0) {
//        if (rsi.value <= 30 && rsi.value > rsi.signal) {
//            hold = 1
//        }
//    }
//}
//
//if (tideMacd.oscillator < 0) {
//    hold = 0
//}




//List<Macd> slowMacds = Tool.calculate(assetIndicator.getOhlcvs(Ohlcv.Type.DAILY, 1), MacdContext.DEFAULT)
//def slowMacd = slowMacds.first()
//if (slowMacd.oscillator < 0) {
//    hold = 0
//}


//// test-2 : 4.5%(2.5-3.4)
//def dmiAdxPctChange = Tool.pctChange(dmiAdxs.take(5))
//def macdOscillatorPctChange = Tool.pctChange(macdOscillators.take(5))
//if (dmi.adx > 25) {
//    if (macd.oscillator > 0 && dmiAdxPctChange > 0) {
//        hold = 1
//    }
//    if (macd.oscillator < 0 && dmiAdxPctChange < 0) {
//        hold = 0
//    }
//}


//// test-2: fail
//List<Macd> macds = Tool.calculate(ohlcvs, MacdContext.of(24, 52, 18))
//def macd = macds.first()
//if (dmi.adx > 25) {
//    if (macd.oscillator > 0) {
//        hold = 1
//    }
//    if (macd.oscillator < 0) {
//        hold = 0
//    }
//}



return hold

