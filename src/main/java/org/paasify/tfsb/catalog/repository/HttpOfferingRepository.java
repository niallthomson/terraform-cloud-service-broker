package org.paasify.tfsb.catalog.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.paasify.tfsb.catalog.OfferingRepositoryException;
import org.paasify.tfsb.catalog.model.Offering;
import org.paasify.tfsb.catalog.model.OfferingContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpOfferingRepository implements OfferingRepository {

    private String offeringCatalogUrl;

    private Map<String, Offering> offerings;

    public HttpOfferingRepository(String offeringCatalogUrl) {
        this.offeringCatalogUrl = offeringCatalogUrl;

        loadOfferings();
    }

    protected void loadOfferings() {
        try {
            File file = File.createTempFile("catalog", null);

            FileUtils.copyURLToFile(new URL(this.offeringCatalogUrl), file);

            Map<String, Offering> offerings = new HashMap<>();

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            OfferingContainer container = mapper.readValue(file, OfferingContainer.class);

            for(Offering offering : container.getOfferings()) {
                offerings.put(offering.getName(), offering);
            }

            this.offerings = offerings;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse offering yaml", e);
        }
    }

    public List<Offering> getOfferings() throws OfferingRepositoryException {
        return List.copyOf(offerings.values());
    }

    public Offering getOffering(String name) throws OfferingRepositoryException {
        return this.offerings.get(name);
    }

    @Override
    public void refresh() throws OfferingRepositoryException {
        this.loadOfferings();
    }
}
