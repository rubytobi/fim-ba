package Util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorLog {
	@JsonIgnore
	private Pattern errorPattern = Pattern.compile("([a-zA-Z]+).([a-zA-Z]+).([><a-zA-Z]+).\\(([a-zA-Z.:0-9]+)\\)");

	@JsonProperty("id")
	private static AtomicInteger id = new AtomicInteger(0);

	@JsonProperty("instance")
	private static final UUID instance = UUID.randomUUID();

	@JsonProperty("uuid")
	UUID uuid;

	@JsonProperty("message")
	String message;

	@JsonProperty("timestamp")
	String timestamp;

	@JsonProperty("functionName")
	String functionName;

	@JsonProperty("className")
	String className;

	@JsonProperty("occurence")
	private String occurence;

	@JsonProperty("packageName")
	private String packageName;

	ErrorLog(UUID uuid, String message, String stack) {
		this.uuid = uuid;
		this.message = message;

		String[] values = stack.split("\\(");
		this.packageName = values[0].split("\\.")[0];
		this.className = values[0].split("\\.")[1];
		this.functionName = values[0].split("\\.")[2];
		this.occurence = values[1].split("\\)")[0];
		this.timestamp = DateTime.timestamp();
	}

	public int getId() {
		return id.getAndIncrement();
	}

	public String getInstance() {
		return instance.toString();
	}

	public String toString() {
		return timestamp + "\t" + uuid + "\t" + occurence + "\t" + message;
	}
}