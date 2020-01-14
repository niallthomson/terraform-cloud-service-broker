package org.paasify.tfsb.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Offering {
    private String name;

    private String displayName;

    private String description;

    private String vcsDirectory;

    private List<String> tags;

    private Map<String, OfferingPlan> plans;
}
