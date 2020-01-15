package org.paasify.tfsb.catalog.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class OfferingPlan {
    private String name;

    private String displayName;

    private String description;

    private List<String> bullets = new ArrayList<>();

    private Map<String, String> parameters = new HashMap<>();
}
