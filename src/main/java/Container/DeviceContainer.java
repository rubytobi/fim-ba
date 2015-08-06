package Container;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import Entity.Device;

/**
 * Container, der alle aktiven Devices enthaelt
 *
 */
public class DeviceContainer {
	private Map<UUID, Device> devices = new ConcurrentHashMap<UUID, Device>();
	private static DeviceContainer instance = null;

	private DeviceContainer() {
	}

	/**
	 * Erstellt neuen Container nach Singleton-Muster
	 * 
	 * @return Container
	 */
	public static DeviceContainer instance() {
		if (instance == null) {
			instance = new DeviceContainer();
		}

		return instance;
	}

	/**
	 * Liefert Device mit uebergebener UUID
	 * 
	 * @param uuid
	 *            UUID des gewuenschten Devices
	 * @return Device mit uebergebener UUID
	 */
	public Device get(UUID uuid) {
		return devices.get(uuid);
	}

	/**
	 * Liefert alle Devices des Containers
	 * 
	 * @return Array mit allen Devices des Containers
	 */
	public Device[] getAll() {
		return devices.values().toArray(new Device[devices.size()]);
	}

	/**
	 * Fuegt neuen Device zum Container hinzu
	 * 
	 * @param device
	 *            Neues Device, das hinzugefuegt werden soll
	 */
	public void add(Device device) {
		devices.put(device.getUUID(), device);
	}

	/**
	 * Entfernt Device mit uebergebener uuid aus dem Container
	 * 
	 * @param uuid
	 *            UUID des zu entferndenden Devices
	 */
	public void delete(UUID uuid) {
		devices.remove(uuid);
	}

	public int size() {
		return devices.size();
	}
}
