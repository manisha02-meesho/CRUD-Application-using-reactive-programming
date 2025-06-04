package com.example.CRUD.repository;

import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Repository;
import com.example.CRUD.model.Employee;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Repository
public class EmployeeRepository {
    private static final Logger log = LoggerFactory.getLogger(EmployeeRepository.class);
    private final ReactiveValueOperations<String, Employee> employeeCache;
    private final ReactiveElasticsearchClient client;

    public EmployeeRepository(ReactiveRedisTemplate<String, Employee> redisTemplate,
                              ReactiveElasticsearchClient client) {
        this.employeeCache = redisTemplate.opsForValue();
        this.client = client;
    }

    public Mono<Employee> findById(String id) {
        return employeeCache.get(id)
                .doOnError(e -> log.error("Redis cache error: {}", e.getMessage()))
                .onErrorResume(err -> Mono.empty())
                .switchIfEmpty(Mono.defer(() -> {
                            var result = client.get(req -> req.index("employees").id(id), Employee.class);
                            return result == null
                                    ? Mono.error(new IllegalStateException("client.get() return null"))
                                    : Mono.from(result);
                        }).flatMap(res -> {
                            Employee employee = res.source();
                            if (employee == null) {
                                return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: " + id));
                            }
                            return employeeCache.set(id, employee)
                                    .doOnError(e -> log.warn("Redis cache write error: {}", e.getMessage()))
                                    .onErrorResume(err -> Mono.empty())
                                    .thenReturn(employee);
                        }).doOnError(e -> log.error("Elasticsearch error: {}", e.getMessage()))
                );
    }

    public Mono<Employee> save(Employee employee) {
        return client.get(req -> req.index("employees").id(employee.getId()), Employee.class)
                .flatMap(existing -> {
                    if (existing.found() && existing.source() != null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT, "Employee with ID already exists: " + employee.getId()));
                    }
                    double random = Math.random();
                    Mono<Employee> storeEmployeeInElasticSearch = client.index(i -> i
                                    .index("employees")
                                    .id(employee.getId())
                                    .document(employee))
                            .doOnError(e -> log.error("Elasticsearch save failed: {}", e.getMessage()))
                            .onErrorMap(e -> new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to save employee to Elasticsearch",
                                    e))
                            .thenReturn(employee);
                    if (random > 0.5) {
                        return storeEmployeeInElasticSearch.flatMap(savedEmployee ->
                                employeeCache.set(savedEmployee.getId(), savedEmployee)
                                        .doOnError(e -> log.warn("Save to Redis failed: {}", e.getMessage()))
                                        .onErrorResume(e -> Mono.empty())
                                        .thenReturn(employee)
                        );
                    } else {
                        return storeEmployeeInElasticSearch;
                    }
                });
    }

    public Mono<Employee> update(String id, Employee employee) {
        return client.get(req -> req.index("employees").id(id), Employee.class)
                .flatMap(response -> {
                    if (response.source() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + id));
                    }
                    return client.update(u -> u
                                    .index("employees")
                                    .id(id)
                                    .doc(employee),
                            Employee.class
                    ).flatMap(res -> {
                        log.info("Employee updated in Elasticsearch: {}", id);
                        return employeeCache.delete(id)
                                .doOnError(e -> log.warn("Redis cache delete failed for id {}: {}", id, e.getMessage()))
                                .onErrorResume(e -> employeeCache.set(id, employee, Duration.ofSeconds(300)))
                                .thenReturn(employee);
                    }).onErrorResume(e ->
                            Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update employee in Elasticsearch", e))
                    );
                }).onErrorResume(e ->
                        Mono.error((e instanceof ResponseStatusException)
                                ? e
                                : new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", e))
                );
    }

    public Mono<Void> deleteById(String id) {
        return client.get(req -> req.index("employees").id(id), Employee.class)
                .flatMap(response -> {
                    Employee employee = response.source();
                    if (employee == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + id));
                    }

                    return employeeCache.delete(id)
                            .doOnError(e -> log.warn("Redis cache delete failed for ID {}: {}", id, e.getMessage()))
                            .onErrorResume(e ->
                                    employeeCache.set(id, employee, Duration.ofSeconds(300))
                                            .doOnError(ttlErr -> log.error("Failed to set TTL for Redis key {}: {}", id, ttlErr.getMessage()))
                                            .then(Mono.empty())
                            )
                            .then(client.delete(req -> req.index("employees").id(id))
                                    .doOnError(e -> log.error("Failed to delete from Elasticsearch for ID {}: {}", id, e.getMessage()))
                                    .onErrorMap(e -> new ResponseStatusException(
                                            HttpStatus.INTERNAL_SERVER_ERROR,
                                            "Failed to delete employee from Elasticsearch", e))
                            )
                            .then();
                })
                .onErrorResume(e -> {
                    if (e instanceof ResponseStatusException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", e));
                });
    }
}
