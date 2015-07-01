package start;

import java.util.UUID;

public class Consumer {

	private final UUID id;
	private final String content;

	public Consumer(String content) {
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
