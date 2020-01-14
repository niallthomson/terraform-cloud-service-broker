package org.paasify.tfsb.catalog.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.paasify.tfsb.catalog.model.Offering;
import org.paasify.tfsb.catalog.model.OfferingContainer;
import org.paasify.tfsb.catalog.OfferingRepositoryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClasspathOfferingRepository {

    private final ResourceLoader resourceLoader;

    private final static String CLASSPATH_PATTERN = "classpath:offerings.yml";

    private Map<String, Offering> offerings;

    @Autowired
    public ClasspathOfferingRepository(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;

        loadOfferings();
    }

    protected void loadOfferings() {
        offerings = new HashMap<>();

        try {
            File file = ResourceUtils.getFile(CLASSPATH_PATTERN);

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            OfferingContainer container = mapper.readValue(file, OfferingContainer.class);

            for(Offering offering : container.getOfferings()) {
                this.offerings.put(offering.getName(), offering);
            }
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
}
