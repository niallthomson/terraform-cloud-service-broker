package org.paasify.tfsb.catalog.model;

import lombok.Data;

import java.util.List;

@Data
public class OfferingContainer {
    private List<Offering> offerings;
}
