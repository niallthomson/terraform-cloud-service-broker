curl http://localhost:8080/v2/service_instances/abc1234?accepts_incomplete=true -d '{
  "service_id": "test",
  "plan_id": "dev",
  "context": {
    "platform": "cloudfoundry",
    "some_field": "some-contextual-data"
  },
  "organization_guid": "org-guid-here",
  "space_guid": "space-guid-here",
  "parameters": {
    "parameter1": 1,
    "parameter2": "foo"
  }
}' -X PUT -H "X-Broker-API-Version: 2.14" -H "Content-Type: application/json"

=================================================

curl http://localhost:8080/v2/service_instances/abc1234/service_bindings/abc1234?accepts_incomplete=true -d '{
  "context": {
    "platform": "cloudfoundry",
    "some_field": "some-contextual-data"
  },
  "service_id": "test",
  "plan_id": "dev",
  "bind_resource": {
    "app_guid": "app-guid-here"
  },
  "parameters": {
    "parameter1-name-here": 1,
    "parameter2-name-here": "parameter2-value-here"
  }
}' -X PUT -H "X-Broker-API-Version: 2.14" -H "Content-Type: application/json"


===================================================


curl http://localhost:8080/v2/service_instances/abc1234/last_operation?operation=1 -H "X-Broker-API-Version: 2.14"

curl 'http://localhost:8080/v2/service_instances/abc1234?accepts_incomplete=true&service_id=test&plan_id=dev' -X DELETE -H "X-Broker-API-Version: 2.14"