package start;

import java.util.UUID;
import Util.DateTime;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures;

import Container.ConsumerContainer;
import Container.DeviceContainer;
import Entity.Fridge;
import Event.DeviceNotFound;
import Event.IllegalDeviceCreation;
import Event.UnsupportedDeviceType;
import Packet.ChangeRequest;
import Packet.FridgeCreation;

@RestController
public class DeviceController {

	@JsonView(View.Summary.class)
	@RequestMapping(value = "/devices", method = RequestMethod.GET)
	public Device[] getAllDevices() {
		return DeviceContainer.instance().getAll();
	}

	@RequestMapping(value = "/devices", method = RequestMethod.POST)
	public @ResponseBody UUID addDevice(@RequestBody FridgeCreation fC) {
		Fridge fridge = new Fridge(fC.getMaxTemp1(), fC.getMaxTemp2(), fC.getMinTemp1(), fC.getMinTemp2(),
				fC.getFallCooling(), fC.getRiseWarming(), fC.getConsCooling(), fC.getCurrTemp());

		DeviceContainer.instance().add(fridge);
		//System.out.println("#" + fridge.getSchedulesFixed());
		//System.out.println("#" + fridge.getLoadprofilesFixed());
		//System.out.println("#" + fridge.getScheduleMinutes());
		return fridge.getUUID();
	}

	@RequestMapping(value = "/devices/{deviceUUID}/link/{consumerUUID}", method = RequestMethod.POST)
	public void linkDevice(@PathVariable UUID deviceUUID, @PathVariable UUID consumerUUID) {
		DeviceContainer.instance().get(deviceUUID).setConsumer(consumerUUID);
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

	@RequestMapping(value = "/devices/{uuid}", method = RequestMethod.DELETE)
	public Boolean deleteSingleDevice(@PathVariable UUID uuid) {
		DeviceContainer.instance().delete(uuid);
		return true;
	}

	@RequestMapping(value = "/devices/{uuid}/", method = RequestMethod.DELETE)
	public Boolean receiveChangeRequest(@RequestBody ChangeRequest cr, @PathVariable UUID uuid) {
		DeviceContainer.instance().get(uuid).changeLoadprofile(cr);
		return true;
	}
	
	@RequestMapping(value = "/devices/{uuid}/confirmLoadprofile", method = RequestMethod.DELETE)
	public Boolean receiveChangeRequest(@RequestBody String time, @PathVariable UUID uuid) {
		DeviceContainer.instance().get(uuid).confirmLoadprofile(time);
		return true;
	}
}
