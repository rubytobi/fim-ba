package start;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConsumerContainer {
	private Map<UUID, Consumer> consumers = new HashMap<UUID, Consumer>();
	private static ConsumerContainer instance = null;

	private ConsumerContainer() {
	}

	public static ConsumerContainer instance() {
		if (instance == null) {
			instance = new ConsumerContainer();
		}

		return instance;
	}

	public Consumer get(UUID uuid) {
		return consumers.get(uuid);
	}

	public Consumer[] getAll() {
		return consumers.values().toArray(new Consumer[consumers.size()]);
	}

	public void add(Consumer consumer) {
		consumers.put(consumer.getId(), consumer);
	}
}
