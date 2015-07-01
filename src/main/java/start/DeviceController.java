package start;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import Event.DeviceNotFound;
import Event.IllegalDeviceCreation;
import Event.UnsupportedDeviceType;

@RestController
public class DeviceController {
	@RequestMapping(value = "/devices", method = RequestMethod.GET)
	public Device[] getAllDevices() {
		return DeviceContainer.instance().getAll();
	}

	@RequestMapping(value = "/devices", method = RequestMethod.POST)
	public @ResponseBody UUID addDevice(@RequestBody Fridge fridge) {
		DeviceContainer.instance().add(fridge);
		return fridge.getUUID();
	}

	@RequestMapping(value = "/devices/{uuid}", method = RequestMethod.GET)
	public Device getSingleDevice(@PathVariable UUID uuid) {
		Device device = DeviceContainer.instance().get(uuid);

		if (device == null) {
			throw new DeviceNotFound();
		}

		return device;
	}

	@RequestMapping(value = "/devices/{uuid}/ping", method = RequestMethod.GET)
	public void pingDevice(@PathVariable UUID uuid) {
		Device device = DeviceContainer.instance().get(uuid);
		device.ping();
	}

	@RequestMapping(value = "/devices/{id}", method = RequestMethod.DELETE)
	public Boolean deleteSingleDevice(@PathVariable UUID uuid) {
		DeviceContainer.instance().delete(uuid);
		return true;
	}
}
