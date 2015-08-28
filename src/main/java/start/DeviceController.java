package start;

import java.util.GregorianCalendar;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;

import Container.DeviceContainer;
import Entity.BHKW;
import Entity.Device;
import Entity.Fridge;
import Packet.AnswerChangeRequestSchedule;
import Packet.ChangeRequestSchedule;
import Packet.FridgeCreation;
import Util.ResponseBuilder;
import Util.View;

@RestController
public class DeviceController {

	/**
	 * Gibt alle aktuellen Geraete zurueck
	 * 
	 * @return Device[]
	 */
	@JsonView(View.Summary.class)
	@RequestMapping(value = "/devices", method = RequestMethod.GET)
	public Device[] getAllDevices() {
		return DeviceContainer.instance().getAll();
	}

	/**
	 * Ein neues Geraet wird erstellt mit Daten aus FridgeCreation
	 * 
	 * @param params
	 *            Initialisierungsdaten
	 * @return Device-ID
	 */
	@RequestMapping(value = "/devices/fridge", method = RequestMethod.POST)
	public @ResponseBody UUID addDevice(@RequestBody FridgeCreation params) {
		Fridge fridge = new Fridge(params.getMaxTemp1(), params.getMaxTemp2(), params.getMinTemp1(),
				params.getMinTemp2(), params.getFallCooling(), params.getRiseWarming(), params.getConsCooling(),
				params.getCurrTemp());

		DeviceContainer.instance().add(fridge);
		return fridge.getUUID();
	}

	/**
	 * Ein neues Geraet wird erstellt mit Daten aus FridgeCreation
	 * 
	 * @param params
	 *            Initialisierungsdaten
	 * @return Device-ID
	 */
	@RequestMapping(value = "/devices/bhkw", method = RequestMethod.POST)
	public @ResponseBody UUID addDevice(@RequestBody BHKWCreation params) {
		BHKW bhkw = new BHKW(params.getChpCoefficient(), params.getPriceFuel(), params.getConsFuelPerKWh(),
				params.getSizeHeatReservoir(), params.getMaxLoad());

		DeviceContainer.instance().add(bhkw);
		return bhkw.getUUID();
	}

	/**
	 * Verbindet ein Geraet mit seinem Consumer
	 * 
	 * @param deviceUUID
	 *            Geraet-ID
	 * @param consumerUUID
	 *            Consumer-ID
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/devices/{deviceUUID}/link/{consumerUUID}", method = RequestMethod.POST)
	public ResponseEntity<Void> linkDevice(@PathVariable UUID deviceUUID, @PathVariable UUID consumerUUID) {
		DeviceContainer.instance().get(deviceUUID).setConsumer(consumerUUID);
		return ResponseBuilder.returnVoid(DeviceContainer.instance().get(deviceUUID));
	}

	/**
	 * Gibt ein spezielles Device zurueck. Sollte das Geraet nicht vorhanden
	 * sein null
	 * 
	 * @param uuid
	 *            Device-ID
	 * @return Device
	 */
	@RequestMapping(value = "/devices/{uuid}", method = RequestMethod.GET)
	public Device getSingleDevice(@PathVariable UUID uuid) {
		return DeviceContainer.instance().get(uuid);
	}

	/**
	 * Stoesst ein Geraet an, um regelmäßige Aufgaben zu machen
	 * 
	 * @param uuid
	 *            Device-ID
	 */
	@RequestMapping(value = "/devices/{uuid}/ping", method = RequestMethod.GET)
	public void pingDevice(@PathVariable UUID uuid) {
		if (DeviceContainer.instance().get(uuid) == null) {
			DeviceContainer.instance().get(uuid);
			return;
		}
		DeviceContainer.instance().get(uuid).ping();
	}

	/**
	 * Ein Geraet erhaelt eine Aufforderung sein Lastprofil zu aendern
	 * 
	 * @param cr
	 *            ChangeRequest
	 * @param uuid
	 *            Device-ID
	 * @return Antwort auf CD
	 */
	@RequestMapping(value = "/devices/{uuid}", method = RequestMethod.DELETE)
	public ResponseEntity<AnswerChangeRequestSchedule> receiveChangeRequest(@RequestBody ChangeRequestSchedule cr,
			@PathVariable UUID uuid) {
		AnswerChangeRequestSchedule body = DeviceContainer.instance().get(uuid).receiveChangeRequestSchedule(cr);

		return new ResponseBuilder<AnswerChangeRequestSchedule>(DeviceContainer.instance().get(uuid)).body(body)
				.build();
	}

	/**
	 * Ein Lastprofil wird durch den Consumer bestaetigt
	 * 
	 * @param time
	 *            Zeit des Lastprofils
	 * @param uuid
	 *            Device-ID
	 * @return Leere Antwort
	 */
	@RequestMapping(value = "/devices/{uuid}/confirm/{time}", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveChangeRequest(@RequestBody GregorianCalendar time, @PathVariable UUID uuid) {
		DeviceContainer.instance().get(uuid).confirmLoadprofile(time);
		return ResponseBuilder.returnVoid(DeviceContainer.instance().get(uuid));

	}

	@RequestMapping(value = "/devices/{uuid}/changeRequest/confirm", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveChangeRequestConfirmation(@PathVariable UUID uuid) {
		DeviceContainer.instance().get(uuid).receiveAnswerChangeRequest(true);
		return ResponseBuilder.returnVoid(DeviceContainer.instance().get(uuid));

	}

	@RequestMapping(value = "/devices/{uuid}/changeRequest/decline", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveChangeRequest(@PathVariable UUID uuid) {
		DeviceContainer.instance().get(uuid).receiveAnswerChangeRequest(false);
		return ResponseBuilder.returnVoid(DeviceContainer.instance().get(uuid));

	}
}
