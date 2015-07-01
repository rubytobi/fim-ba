package start;

import java.util.UUID;

public class Device {

	private final UUID id;
	private final String content;

	public Device(String content) {
		this.id = UUID.randomUUID();
		this.content = content;
	}

	public UUID getId() {
		return id;
	}

	public String getContent() {
		return content;
	}
}
