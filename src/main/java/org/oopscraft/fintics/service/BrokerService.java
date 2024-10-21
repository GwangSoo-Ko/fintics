package org.oopscraft.fintics.service;

import lombok.RequiredArgsConstructor;
import org.oopscraft.arch4j.core.common.data.IdGenerator;
import org.oopscraft.arch4j.core.common.pbe.PbePropertiesUtil;
import org.oopscraft.fintics.dao.BrokerEntity;
import org.oopscraft.fintics.dao.BrokerRepository;
import org.oopscraft.fintics.model.Broker;
import org.oopscraft.fintics.model.BrokerSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrokerService {

    private final BrokerRepository brokerRepository;

    public Page<Broker> getBrokers(BrokerSearch brokerSearch, Pageable pageable) {
        Page<BrokerEntity> brokerEntityPage = brokerRepository.findAll(brokerSearch, pageable);
        List<Broker> brokers = brokerEntityPage.getContent().stream()
                .map(Broker::from)
                .toList();
        long total = brokerEntityPage.getTotalElements();
        return new PageImpl<>(brokers, pageable, total);
    }

    public Optional<Broker> getBroker(String brokerId) {
        return brokerRepository.findById(brokerId)
                .map(Broker::from);
    }

    @Transactional
    public Broker saveBroker(Broker broker) {
        BrokerEntity brokerEntity;
        if (broker.getBrokerId() == null) {
            brokerEntity = BrokerEntity.builder()
                    .brokerId(IdGenerator.uuid())
                    .build();
        } else {
            brokerEntity = brokerRepository.findById(broker.getBrokerId())
                    .orElseThrow();
        }
        brokerEntity.setName(broker.getName());
        brokerEntity.setBrokerClientId(broker.getBrokerClientId());
        brokerEntity.setBrokerClientProperties(Optional.ofNullable(broker.getBrokerClientProperties())
                .map(PbePropertiesUtil::encodePropertiesString)
                .orElse(null));
        BrokerEntity savedBrokerEntity = brokerRepository.saveAndFlush(brokerEntity);
        return Broker.from(savedBrokerEntity);
    }

    @Transactional
    public void deleteBroker(String brokerId) {
        BrokerEntity brokerEntity = brokerRepository.findById(brokerId)
                        .orElseThrow();
        brokerRepository.delete(brokerEntity);
        brokerRepository.flush();
    }

}
