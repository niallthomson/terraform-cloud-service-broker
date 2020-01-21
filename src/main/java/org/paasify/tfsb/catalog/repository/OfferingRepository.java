package org.paasify.tfsb.catalog.repository;

import org.paasify.tfsb.catalog.OfferingRepositoryException;
import org.paasify.tfsb.catalog.model.Offering;

import java.util.List;

public interface OfferingRepository {
    List<Offering> getOfferings() throws OfferingRepositoryException;

    Offering getOffering(String name) throws OfferingRepositoryException;

    void refresh() throws OfferingRepositoryException;
}
