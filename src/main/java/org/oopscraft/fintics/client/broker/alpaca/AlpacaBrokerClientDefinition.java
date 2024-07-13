package org.oopscraft.fintics.client.broker.alpaca;

import org.oopscraft.fintics.client.broker.BrokerClient;
import org.oopscraft.fintics.client.broker.BrokerClientDefinition;
import org.oopscraft.fintics.client.broker.kis.KisUsBrokerClient;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.StringJoiner;

@Component
public class AlpacaBrokerClientDefinition implements BrokerClientDefinition {

    @Override
    public String getBrokerClientId() {
        return "ALPACA";
    }

    @Override
    public String getBrokerClientName() {
        return "Alpaca API";
    }

    @Override
    public Class<? extends BrokerClient> getClassType() {
        return KisUsBrokerClient.class;
    }

    @Override
    public String getPropertiesTemplate() {
        StringJoiner template = new StringJoiner("\n");
        template.add("live=false");
        template.add("appSecret=[Application Secret]");
        template.add("accountNo=[Account Number]");
        return template.toString();
    }

    @Override
    public String getMarket() {
        return "US";
    }

    @Override
    public ZoneId getTimezone() {
        return ZoneId.of("America/New_York");
    }

}
