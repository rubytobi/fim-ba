package start;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeviceContainer {
	private Map<UUID, Device> devices = new HashMap<UUID, Device>();
	private static DeviceContainer instance = null;

	private DeviceContainer() {
	}

	public static DeviceContainer instance() {
		if (instance == null) {
			instance = new DeviceContainer();
			instance.add(new Device("asd"));
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
		devices.put(device.getId(), device);
	}

	public void delete(UUID uuid) {
		devices.remove(uuid);
	}
}
