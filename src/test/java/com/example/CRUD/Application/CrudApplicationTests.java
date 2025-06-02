package com.example.CRUD.Application;

import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.util.ObjectBuilder;
import com.example.CRUD.model.Employee;
import com.example.CRUD.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;
import static org.mockito.Mockito.*;

class CrudApplicationTests {

	private ReactiveRedisTemplate<String, Employee> redisTemplate;
	private ReactiveValueOperations<String, Employee> valueOperations;
	private ReactiveElasticsearchClient client;
	private EmployeeRepository employeeRepository;

	@BeforeEach
	void setup() {
		redisTemplate = mock(ReactiveRedisTemplate.class);
		valueOperations = mock(ReactiveValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		client = mock(ReactiveElasticsearchClient.class);

		employeeRepository = new EmployeeRepository(redisTemplate, client);
	}

	@Test
	void findById_WhenFoundInRedis() {
		Employee expected = Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.phoneNumber("8989898989")
				.build();

		when(valueOperations.get("1")).thenReturn(Mono.just(expected));

		StepVerifier.create(employeeRepository.findById("1"))
				.expectNext(expected)
				.verifyComplete();

		verify(valueOperations).get("1");
		verifyNoInteractions(client);
	}

	@Test
	void findById_NotFoundInRedis_FoundInElasticsearch(){
		Employee expected = Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("09898979")
				.build();

		when(valueOperations.get("1")).thenReturn(Mono.empty());

		GetResponse<Employee> getResponse = GetResponse.of(r -> r.index("employees").id("1").found(true).source(expected));
		when(client.get(
				ArgumentMatchers.<Function<GetRequest.Builder, ObjectBuilder<GetRequest>>>any(),
				eq(Employee.class)
		)).thenReturn(Mono.just(getResponse));

		when(valueOperations.set("1", expected)).thenReturn(Mono.just(true));

		StepVerifier.create(employeeRepository.findById("1"))
				.expectNext(expected)
				.verifyComplete();

		verify(valueOperations).get("1");
		verify(client).get(
				ArgumentMatchers.<Function<GetRequest.Builder, ObjectBuilder<GetRequest>>>any(),
				eq(Employee.class)
		);
		verify(valueOperations).set("1", expected);
	}

	@Test
	void findById_NotFoundInRedis_NotFoundInElasticsearch() {
		String empId = "1";

		when(valueOperations.get(empId)).thenReturn(Mono.empty());

		GetResponse<Employee> getResponse = mock(GetResponse.class);
		when(getResponse.found()).thenReturn(false);

		when(client.get(any(Function.class), eq(Employee.class))).thenReturn(Mono.just(getResponse));

		StepVerifier.create(employeeRepository.findById(empId))
				.expectErrorMatches(throwable -> throwable instanceof ResponseStatusException &&
						((ResponseStatusException) throwable).getStatusCode() == HttpStatus.NOT_FOUND &&
						throwable.getMessage().contains("Not Found"))
				.verify();

		verify(valueOperations).get(empId);
		verify(client).get(any(Function.class), eq(Employee.class));
		verify(valueOperations, never()).set(eq(empId), any());
	}

	@Test
	void findById_RedisThrowsError_ElasticSearchSucceeds(){
		Employee expected=Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9009808088")
				.build();

		//redis throws error
		when(valueOperations.get("1")).thenReturn(Mono.error(new RuntimeException("Redis down")));

		//elasticSearch returns employee
		GetResponse<Employee> getResponse=GetResponse.of(r->r.index("employees").id("1").found(true).source(expected));
		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));

		when(valueOperations.set("1",expected)).thenReturn(Mono.just(true));

		StepVerifier.create(employeeRepository.findById("1"))
				.expectNext(expected)
				.verifyComplete();

		verify(valueOperations).get("1");
		verify(client).get(any(Function.class),eq(Employee.class));
		verify(valueOperations).set("1", expected);
	}

	@Test
	void findById_RedisMiss_ElasticSearchThrowsError(){
		String empId = "1";

		when(valueOperations.get(empId)).thenReturn(Mono.empty());

		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.error(new RuntimeException("Elasticsearch down")));

		StepVerifier.create(employeeRepository.findById(empId))
				.expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().contains("Elasticsearch down"))
				.verify();

		verify(valueOperations).get(empId);
		verify(client).get(any(Function.class),eq(Employee.class));
		verify(valueOperations, never()).set(eq(empId), any());
	}

	@Test
	void findById_RedisThrowError_ElasticSearchThrowError(){
		String empId= "1";

		when(valueOperations.get(empId)).thenReturn(Mono.error(new RuntimeException("Elastic down")));

		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.error(new RuntimeException("Elasticsearch down")));

		StepVerifier.create(employeeRepository.findById(empId))
				.expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().contains("Elasticsearch down"))
				.verify();

		verify(valueOperations).get(empId);
		verify(client).get(any(Function.class),eq(Employee.class));
		verify(valueOperations, never()).set(eq(empId), any());
	}

	@Test
	void save_NULLId_ShouldReturnBadRequest(){
		Employee employee=Employee.builder()
				.id(null)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		StepVerifier.create(employeeRepository.save(employee))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void save_EmptyId_ShouldReturnBadRequest(){
		Employee employee=Employee.builder()
				.id(" ")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		StepVerifier.create(employeeRepository.save(employee))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void save_SuccessfullySavedToElasticSearchAndRedis(){
		Employee employee=Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		when(client.index(any(Function.class)))
				.thenReturn(Mono.just(mock(IndexResponse.class)));
		when(valueOperations.set("1",employee)).thenReturn(Mono.just(true));

		StepVerifier.create(employeeRepository.save(employee))
				.expectNext(employee)
				.verifyComplete();

		verify(client).index(any(Function.class));
		verify(valueOperations).set("1",employee);
	}

	@Test
	void save_ElasticSearchFails_ShouldReturnServerError(){
		Employee employee=Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		when(client.index(any(Function.class)))
				.thenReturn(Mono.error(new RuntimeException("Elasticsearch down")));

		StepVerifier.create(employeeRepository.save(employee))
				.expectError(ResponseStatusException.class)
				.verify();

		verify(client).index(any(Function.class));
		verifyNoInteractions(valueOperations);
	}

	@Test
	void save_RedisFails_ShouldStillReturnEmployee(){
		Employee employee=Employee.builder()
				.id("1")
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		when(client.index(any(Function.class))).thenReturn(Mono.just(mock(IndexResponse.class)));
		when(valueOperations.set("1",employee)).thenReturn(Mono.error(new RuntimeException("Redis write failed")));

		StepVerifier.create(employeeRepository.save(employee))
				.expectNext(employee)
				.verifyComplete();

		verify(client).index(any(Function.class));
		verify(valueOperations).set("1",employee);
	}

	@Test
	void update_NullId_ShouldReturnBadRequest(){
		StepVerifier.create(employeeRepository.update(null,new Employee()))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void update_NullEmployee_ShouldReturnBadRequest(){
		StepVerifier.create(employeeRepository.update("1",null))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void update_EmployeeNotFound_ShouldReturnNotFound(){
		String id="1";
		GetResponse<Employee> response=mock(GetResponse.class);
		when(response.source()).thenReturn(null);

		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(response));

		StepVerifier.create(employeeRepository.update(id,new Employee()))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void update_SuccessfulUpdate_ShouldDeleteFromRedisAndReturnUpdatedEmployee(){
		String id="1";

		Employee input=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		Employee updated=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(input);

		UpdateResponse<Employee> updateResponse=mock(UpdateResponse.class);

		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));
		when(client.update(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(updateResponse));
		when(valueOperations.delete(id)).thenReturn(Mono.empty());

		StepVerifier.create(employeeRepository.update(id,input))
				.expectNext(updated)
				.verifyComplete();

		verify(client).update(any(Function.class),eq(Employee.class));
		verify(valueOperations).delete(id);
	}

	@Test
	void update_ElasticSearchFails_ShouldReturnServerError(){
		String id="1";
		Employee employee=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(new Employee());

		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));
		when(client.update(any(Function.class),eq(Employee.class))).thenReturn(Mono.error(new RuntimeException("Elasticsearch down")));

		StepVerifier.create(employeeRepository.update(id,new Employee()))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void deleteById_NullId_ShouldReturnBadRequest(){
		StepVerifier.create(employeeRepository.deleteById(null))
				.expectError(ResponseStatusException.class)
				.verify();
	}

	@Test
	void deleteById_SuccessfulDelete_ShouldDeleteFromElasticSearchAndRedis(){
		String id="1";
		Employee existingEmployee=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(existingEmployee);
		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));

		DeleteResponse deleteResponse=mock(DeleteResponse.class);
		when(client.delete(any(Function.class))).thenReturn(Mono.just(deleteResponse));

		when(valueOperations.delete(id)).thenReturn(Mono.empty());

		StepVerifier.create(employeeRepository.deleteById(id))
				.verifyComplete();

		verify(client).get(any(Function.class),eq(Employee.class));
		verify(client).delete(any(Function.class));
		verify(valueOperations).delete(id);
	}

	@Test
	void deleteById_EmployeeNotFoundInElasticSearch_ShouldReturnNotFound(){
		String id="1";
		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(null);
		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));

		//When & then
		StepVerifier.create(employeeRepository.deleteById(id))
				.expectError(ResponseStatusException.class)
				.verify();

		verify(client).get(any(Function.class),eq(Employee.class));
		verifyNoMoreInteractions(client);
		verifyNoInteractions(valueOperations);
	}

	@Test
	void deleteById_ElasticSearchDeleteFails_ShouldReturnInternalServerError(){
		String id="1";
		Employee employee=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(employee);
		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));

		RuntimeException deleteException=new RuntimeException("Elasticsearch delete failed");
		when(client.delete(any(Function.class))).thenReturn(Mono.error(deleteException));

		StepVerifier.create(employeeRepository.deleteById(id))
				.expectError(ResponseStatusException.class)
				.verify();

		verify(client).get(any(Function.class),eq(Employee.class));
		verify(client).delete(any(Function.class));
		verifyNoInteractions(valueOperations);
	}

	@Test
	void deleteById_RedisDeleteFails_ShouldReturnInternalServerError(){
		String id="1";
		Employee employee=Employee.builder()
				.id(id)
				.name("Manisha")
				.role("Developer")
				.department("IT")
				.phoneNumber("9898989898")
				.build();

		GetResponse<Employee> getResponse=mock(GetResponse.class);
		when(getResponse.source()).thenReturn(employee);
		when(client.get(any(Function.class),eq(Employee.class))).thenReturn(Mono.just(getResponse));

		when(client.delete(any(Function.class))).thenReturn(Mono.empty());

		when(valueOperations.delete(id)).thenReturn(Mono.error(new RuntimeException("Redis delete failed")));

		StepVerifier.create(employeeRepository.deleteById(id))
				.expectError(ResponseStatusException.class)
				.verify();

		verify(client).get(any(Function.class),eq(Employee.class));
		verify(client).delete(any(Function.class));
		verify(valueOperations).delete(id);
	}
}
