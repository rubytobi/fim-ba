package Container;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import start.Device;

public class DeviceContainer {
	private Map<UUID, Device> devices = new HashMap<UUID, Device>();
	private static DeviceContainer instance = null;

	private DeviceContainer() {
	}

	public static DeviceContainer instance() {
		if (instance == null) {
			instance = new DeviceContainer();
		}

		return instance;
	}

	public Device get(UUID uuid) {
		return devices.get(uuid);
	}

	public Device[] getAll() {
		return devices.values().toArray(new Device[devices.size()]);
	}

	public void add(Device device) {
		devices.put(device.getUUID(), device);
	}

	public void delete(UUID uuid) {
		devices.remove(uuid);
	}
}
