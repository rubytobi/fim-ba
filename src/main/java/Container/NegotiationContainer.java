package Container;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import Util.Negotiation;

public class NegotiationContainer {
	private Map<UUID, Negotiation> negotiations = new ConcurrentHashMap<UUID, Negotiation>();
	private static NegotiationContainer instance = null;

	private NegotiationContainer() {
	}
	
	/**
	 * Erstellt neuen Container nach Singleton-Muster 
	 * @return Container
	 */
	public static NegotiationContainer instance() {
		if (instance == null) {
			instance = new NegotiationContainer();
		}

		return instance;
	}
	
	/**
	 * Liefert Negotiation mit uebergebener UUID
	 * @param uuid UUID der gewuenschten Negotiation
	 * @return Negotiation mit uebergebener UUID
	 */
	public Negotiation get(UUID uuid) {
		return negotiations.get(uuid);
	}
	
	/**
	 * Liefert alle Negotiations des Containers
	 * @return Array mit allen Negotiations des Containers
	 */
	public Negotiation[] getAll() {
		return negotiations.values().toArray(new Negotiation[negotiations.size()]);
	}
	
	/**
	 * Fuegt neue Negotiation zum Container hinzu
	 * @param negotiation Neue Negotiation, das hinzugefuegt werden soll
	 */
	public void add(Negotiation negotiation) {
		negotiations.put(negotiation.getUUID(), negotiation);
	}
	
	/**
	 * Entfernt Negotiation mit uebergebener uuid aus dem Container
	 * @param uuid UUID des zu entferndenden Devices
	 */
	public void delete(UUID uuid) {
		negotiations.remove(uuid);
	}
}
