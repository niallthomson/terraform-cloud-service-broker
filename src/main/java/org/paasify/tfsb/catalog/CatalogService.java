package org.paasify.tfsb.catalog;

import org.paasify.tfsb.catalog.model.Offering;
import org.paasify.tfsb.catalog.model.OfferingPlan;
import org.paasify.tfsb.catalog.repository.ClasspathOfferingRepository;
import org.paasify.tfsb.catalog.repository.OfferingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.model.catalog.Catalog;
import org.springframework.cloud.servicebroker.model.catalog.Plan;
import org.springframework.cloud.servicebroker.model.catalog.ServiceDefinition;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CatalogService implements org.springframework.cloud.servicebroker.service.CatalogService {

    private final OfferingRepository offeringRepository;

    @Autowired
    public CatalogService(OfferingRepository offeringRepository) {
        this.offeringRepository = offeringRepository;
    }

    @Override
    public Mono<Catalog> getCatalog() {
        try {
            List<ServiceDefinition> defs = offeringRepository.getOfferings()
                    .stream()
                    .map(this::createServiceDefinition)
                    .collect(Collectors.toList());

            return Mono.just(Catalog.builder().serviceDefinitions(defs).build());
        } catch (OfferingRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    public Offering getOffering(String name) {
        try {
            return this.offeringRepository.getOffering(name);
        } catch (OfferingRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private ServiceDefinition createServiceDefinition(Offering offering) {
        return ServiceDefinition.builder()
                .id(offering.getName())
                .name(offering.getName())
                .bindable(true)
                .description(offering.getDescription())
                .tags(offering.getTags())
                .plans(createPlans(offering))
                .build();
    }

    private List<Plan> createPlans(Offering offering) {
        List<Plan> plans = new ArrayList<>();

        for(String name : offering.getPlans().keySet()) {
            OfferingPlan plan = offering.getPlans().get(name);

            plans.add(Plan.builder()
                    .id(name)
                    .description(plan.getDescription())
                    .name(plan.getDisplayName())
                    .free(true)
                    .build());
        }

        return plans;
    }

    @Override
    public Mono<ServiceDefinition> getServiceDefinition(String serviceId) {
        try {
            return Mono.just(createServiceDefinition(this.offeringRepository.getOffering(serviceId)));
        } catch (OfferingRepositoryException e) {
            throw new RuntimeException(e);
        }
    }
}