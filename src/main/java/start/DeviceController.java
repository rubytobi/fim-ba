package start;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import Event.DeviceNotFound;
import Event.IllegalDeviceCreation;
import Requests.DevicePutRequest;

@RestController
public class DeviceController {
	@RequestMapping(value = "/devices", method = RequestMethod.GET)
	public Device[] getAllDevices() {
		return DeviceContainer.instance().getAll();
	}

	@RequestMapping(value = "/devices", method = RequestMethod.POST)
	public boolean addDevice(@RequestBody DevicePutRequest request) {
		String className = request.getClassName();

		Object object;
		try {
			object = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new IllegalDeviceCreation();
		}

		if (object != null && object instanceof Device) {
			DeviceContainer.instance().add((Device) object);
			return true;
		} else {
			throw new IllegalDeviceCreation();
		}
	}

	@RequestMapping(value = "/devices/{uuid}", method = RequestMethod.GET)
	public Device getSingleDevice(@PathVariable UUID uuid) {
		Device device = DeviceContainer.instance().get(uuid);

		if (device == null) {
			throw new DeviceNotFound();
		}

		return device;
	}

	@RequestMapping(value = "/devices/{id}", method = RequestMethod.DELETE)
	public Boolean deleteSingleDevice(@PathVariable UUID uuid) {
		DeviceContainer.instance().delete(uuid);
		return true;
	}
}
