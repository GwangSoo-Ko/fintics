package org.oopscraft.fintics.client.asset.market;

import org.oopscraft.arch4j.core.common.support.RestTemplateBuilder;
import org.oopscraft.fintics.client.asset.AssetClient;
import org.oopscraft.fintics.client.asset.AssetClientProperties;
import org.oopscraft.fintics.model.Asset;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpbitAssetClient extends AssetClient {

    private final RestTemplate restTemplate;

    public UpbitAssetClient(AssetClientProperties assetClientProperties) {
        super(assetClientProperties);

        // rest template
        this.restTemplate = RestTemplateBuilder.create()
                .retryCount(3)
                .build();
    }

    @Override
    public List<Asset> getAssets() {
        RequestEntity<Void> requestEntity = RequestEntity
                .get("https://api.upbit.com/v1/market/all")
                .build();
        ResponseEntity<List<Map<String, String>>> responseEntity = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return responseEntity.getBody().stream()
                .map(map -> {
                    return Asset.builder()
                            .assetId(toAssetId("UPBIT", map.get("market")))
                            .name(map.get("english_name"))
                            .market("UPBIT")
                            .exchange("UPBIT")
                            .type("CRYPTO")
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSupportAssetDetail(Asset asset) {
        return false;
    }

    @Override
    public void applyAssetDetail(Asset asset) {
        // void
    }

}
