package org.paasify.tfsb.catalog.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class OfferingPlan {
    private String displayName;

    private String description;

    private Map<String, String> parameters = new HashMap<>();
}
