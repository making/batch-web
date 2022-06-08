package com.example.batch;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class JobExecutionController {
	private final JobLauncher jobLauncher;

	private final ApplicationContext context;

	private final JobExplorer jobExplorer;

	public JobExecutionController(JobLauncher jobLauncher, ApplicationContext context, JobExplorer jobExplorer) {
		this.jobLauncher = jobLauncher;
		this.context = context;
		this.jobExplorer = jobExplorer;
	}

	/**
	 * curl -H "Content-Type: application/json" -X POST -d '{"name":"job", "jobParameters": {"foo":"bar", "baz":"quix"}}' http://localhost:8080/jobexecutions
	 */
	@PostMapping(path = "/jobexecutions")
	public ResponseEntity<Map<String, Object>> runJob(@RequestBody JobLaunchRequest request, UriComponentsBuilder uriComponentsBuilder) throws Exception {
		final Job job = this.context.getBean(request.getName(), Job.class);
		final JobParameters jobParameters = new JobParametersBuilder(request.getJobParameters(), this.jobExplorer)
				.getNextJobParameters(job)
				.toJobParameters();
		final JobExecution jobExecution = this.jobLauncher.run(job, jobParameters);
		final Long executionId = jobExecution.getId();
		final URI uri = uriComponentsBuilder.replacePath("jobexecutions/{executionId}").build(executionId);
		return ResponseEntity.created(uri).body(Map.of("id", executionId));
	}

	@GetMapping(path = "/jobexecutions/{executionId}")
	public ResponseEntity<Map<String, Object>> getJobExecution(@PathVariable("executionId") Long executionId) {
		final JobExecution jobExecution = this.jobExplorer.getJobExecution(executionId);
		if (jobExecution == null) {
			return ResponseEntity.status(NOT_FOUND).body(Map.of("error", NOT_FOUND, "message", "The given execution id is not found: " + executionId + ""));
		}
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", jobExecution.getId());
		body.put("version", jobExecution.getVersion());
		body.put("startTime", Objects.requireNonNullElse(jobExecution.getStartTime(), ""));
		body.put("endTime", Objects.requireNonNullElse(jobExecution.getEndTime(), ""));
		body.put("lastUpdated", Objects.requireNonNullElse(jobExecution.getLastUpdated(), ""));
		body.put("status", jobExecution.getStatus());
		body.put("exitStatus", jobExecution.getExitStatus());
		body.put("job", jobExecution.getJobInstance());
		body.put("jobParameters", jobExecution.getJobParameters());
		return ResponseEntity.ok(body);
	}

	public static class JobLaunchRequest {
		private String name;

		private Properties jobParameters;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Properties getJobParamsProperties() {
			return jobParameters;
		}

		public void setJobParamsProperties(Properties jobParameters) {
			this.jobParameters = jobParameters;
		}

		public JobParameters getJobParameters() {
			Properties properties = new Properties();
			properties.putAll(this.jobParameters);
			return new JobParametersBuilder(properties)
					.toJobParameters();
		}
	}
}
