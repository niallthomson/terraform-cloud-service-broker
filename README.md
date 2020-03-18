# Terraform Cloud Service Broker

WARNING: This code is prototype-quality and the documentation is in the very early stages

This project provides a Open Service Broker API implementation for interacting 
with Terraform Cloud. This allows self-service, on-demand provisioning of infrastructure 
using standard Terraform templates, using any provider supported by Terraform Cloud.

Usage modes that this should work with:
- Terraform Cloud (https://app.terraform.io)
- Terraform Enterprise (self-hosted Terraform Cloud)

The best way to integrate with this project is via:
- [CloudFoundry Marketplace](https://docs.cloudfoundry.org/services/)
- [Kubernetes Service Catalog](https://kubernetes.io/docs/concepts/extend-kubernetes/service-catalog/)

## How it works

The service broker assumes a VCS repository containing a catalog of services expressed 
as Terraform templates. You can see an example here:

https://github.com/niallthomson/terraform-service-catalog-example

Multiple services and plans can be exposed from the same repository by providing  
inputs to the Terraform templates, as different inputs can be passed by each plan.

When a service is provisioned via the broker, a new Terraform Cloud workspace will 
be created that references the appropriate template in the repository, and a run will 
automatically be queued to build the infrastructure.

When a service binding is created, all of the outputs produced by the Terraform configuration
are returned as credentials via the OSB API spec. This allows endpoints and credentials 
created to be consumed by applications.

## Running

TODO
