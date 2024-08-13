package org.oopscraft.fintics.model;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IconFactory {

    public static String getIcon(Asset asset) {
        return switch (Optional.ofNullable(asset.getMarket()).orElse("")) {
            case "US" -> getUsIcon(asset);
            case "KR" -> getKrIcon(asset);
            default -> null;
        };
    }

    static String getUsIcon(Asset asset) {
        String symbol = asset.getSymbol();
        String assetName = asset.getAssetName();
        String type = asset.getType();
        switch (Optional.ofNullable(type).orElse("")) {
            case "STOCK" -> {
                List<String> icons = new ArrayList<>();
                icons.add(String.format("https://ssl.pstatic.net/imgstock/fn/real/logo/stock/Stock%s.O.svg", symbol));
                icons.add(String.format("https://ssl.pstatic.net/imgstock/fn/real/logo/stock/Stock%s.svg", symbol));
                // check available
                for (String icon : icons) {
                    if (isIconAvailable(icon)) {
                        return icon;
                    }
                }
            }
            case "ETF" -> {
                String etfBrand = assetName.split("\\s+")[0].toLowerCase();
                return switch (etfBrand) {
                    case "spdr" -> "https://www.ssga.com/favicon.ico";
                    case "global" -> "https://www.globalxetfs.com/favicon.ico";
                    case "goldman" -> "https://cdn.gs.com/images/goldman-sachs/v1/gs-favicon.svg";
                    default -> String.format("https://s3-symbol-logo.tradingview.com/%s.svg", etfBrand);
                };
            }
        }
        return null;
    }

    static String getKrIcon(Asset asset) {
        String symbol = asset.getSymbol();
        String assetName = asset.getAssetName();
        String type = asset.getType();
        switch (Optional.ofNullable(type).orElse("")) {
            case "STOCK" -> {
                return String.format("https://ssl.pstatic.net/imgstock/fn/real/logo/stock/Stock%s.svg", symbol);
            }
            case "ETF" -> {
                String etfBrand = assetName.split("\\s+")[0];
                return switch (etfBrand) {
                    case "KODEX" -> "https://www.samsungfund.com/assets/icons/favicon.png";
                    case "TIGER" -> "https://www.tigeretf.com/common/images/favicon.ico";
                    case "KBSTAR" ->"https://www.kbstaretf.com/favicon.ico";
                    case "KOSEF" -> "https://www.kosef.co.kr/favicon.ico";
                    case "ACE" -> "https://www.aceetf.co.kr/favicon.ico";
                    case "ARIRANG" -> "http://arirangetf.com/image/common/favicon.ico";
                    case "SOL" -> "https://www.soletf.com/static/pc/img/common/favicon.ico";
                    case "TIMEFOLIO" -> "https://timefolio.co.kr/images/common/favicon.ico";
                    default -> "https://ssl.pstatic.net/imgstock/fn/real/logo/stock/StockCommonETF.svg";
                };
            }
        }
        // return default
        return null;
    }

    static boolean isIconAvailable(String icon) {
        try {
            URL url = new URL(icon);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return true;
            }
        } catch (IOException ignore) {}
        return false;
    }


}
