package Container;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import Entity.Consumer;

/**
 * Container, der alle aktiven Consumer enthaelt
 *
 */
public class ConsumerContainer {
	private Map<UUID, Consumer> consumers = new ConcurrentHashMap<UUID, Consumer>();
	private static ConsumerContainer instance = null;

	private ConsumerContainer() {
	}
	
	/**
	 * Erstellt neuen Container nach Singleton-Muster
	 * @return Container
	 */
	public static ConsumerContainer instance() {
		if (instance == null) {
			instance = new ConsumerContainer();
		}

		return instance;
	}
	
	/**
	 * Liefert Consumer mit uebergebener UUID
	 * @param uuid UUID des gewuenschten Consumers
	 * @return	Consumer mit uebergebener UUID
	 */
	public Consumer get(UUID uuid) {
		return consumers.get(uuid);
	}
	
	/**
	 * Liefert alle Consumer des Containers
	 * @return Array mit allen Consumer des Containers
	 */
	public Consumer[] getAll() {
		return consumers.values().toArray(new Consumer[consumers.size()]);
	}
	
	/**
	 * Fuegt neuen Consumer zum Container hinzu
	 * @param consumer Neuer Consumer, der hinzugefuegt werden soll
	 */
	public void add(Consumer consumer) {
		consumers.put(consumer.getUUID(), consumer);
	}
}
