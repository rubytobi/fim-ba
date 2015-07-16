package Util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorLog {
	@JsonProperty("id")
	private static AtomicInteger id = new AtomicInteger(0);

	@JsonProperty("uuid")
	UUID uuid;

	@JsonProperty("message")
	String message;

	@JsonProperty("timestamp")
	String timestamp;

	@JsonProperty("functionName")
	String functionName;

	ErrorLog(UUID uuid, String message, String functionName) {
		this.uuid = uuid;
		this.message = message;

		// remove package name
		functionName = functionName.substring(functionName.indexOf(".") + 1);
		// remove class name
		functionName = functionName.substring(functionName.indexOf(".") + 1);

		this.functionName = functionName;
		this.timestamp = DateTime.timestamp();
	}

	public int getId() {
		return id.getAndIncrement();
	}

	public String toString() {
		return timestamp + "\t" + uuid + "\t" + functionName + "\t" + message;
	}
}