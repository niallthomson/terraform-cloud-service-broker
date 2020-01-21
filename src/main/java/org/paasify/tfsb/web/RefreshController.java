package org.paasify.tfsb.web;

import org.paasify.tfsb.catalog.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefreshController {

    @Autowired
    private CatalogService catalogService;

    @PostMapping("/refresh")
    public String refresh() {
        catalogService.refresh();

        return "OK";
    }
}
