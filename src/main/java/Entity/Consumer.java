package Entity;

import java.util.*;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;

import Event.InvalidOffer;
import Packet.DeviceLoadprofile;
import Packet.OfferNotification;
import Util.Log;
import start.Application;
import start.Device;
import start.Loadprofile;
import start.View;

public class Consumer {
	// uuid für jeden consumer
	@JsonView(View.Summary.class)
	private UUID uuid;

	// uuid für das verbundene device
	@JsonView(View.Summary.class)
	private UUID device = null;

	// Aktuelles Angebot
	private Offer offer = null;

	// Anzahl der 15-Minuten-Slots f�r ein Lastprofil
	private int numSlots = 4;

	// Aktuelles Stundenlastprofil (evtl. auch schon aggregiert mit anderen
	// Teilnehmern)
	private Loadprofile loadprofile = null;
	private DeviceLoadprofile deviceLoadprofile = null;

	public int getNumSlots() {
		return numSlots;
	}

	public UUID getUUID() {
		return uuid;
	}

	public Consumer() {
		uuid = UUID.randomUUID();
	}

	public double[] chargeValuesLoadprofile(double[] scheduleMinutes) {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int j = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + scheduleMinutes[i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[j] = summeMin;
				summeHour = summeHour + valuesLoadprofile[j];
				j++;
				System.out.println("Verbrauch " + j + ". 15 Minuten: " + summeMin);
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	private Loadprofile generateAggLoadprofile(Offer offer) {
		Loadprofile aggLoadprofile = new Loadprofile(loadprofile, offer.getAggLoadprofile());
		return aggLoadprofile;
	}

	private boolean testOfferDate(Offer offer) {
		Date dateOffer = offer.getAggLoadprofile().getDate();
		Date dateLoadprofile = loadprofile.getDate();

		if (dateOffer == dateLoadprofile) {
			return true;
		} else {
			return false;
		}
	}

	private boolean testOfferAverage(Offer offer) {
		Loadprofile aggLoadprofile = generateAggLoadprofile(offer);

		double deviation = loadprofile.chargeDeviationAverage();
		double aggDeviation = aggLoadprofile.chargeDeviationAverage();

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	private boolean testOfferOtherProfile(Offer offer, Loadprofile otherProfile) {
		Loadprofile aggLoadprofile = generateAggLoadprofile(offer);

		double deviation = loadprofile.chargeDeviationOtherProfile(otherProfile);
		double aggDeviation = aggLoadprofile.chargeDeviationOtherProfile(otherProfile);

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	public void receiveOfferNotification(OfferNotification offerNotification) {
		if (offerNotification.getReferenceOffer() == null) {
			Log.i(uuid + " [consumer] received offer");
			Log.i(offerNotification.toString());
		} else {
			throw new InvalidOffer();
		}
	}

	public Offer getOffer(UUID uuidOffer) {
		return offer;
	}

	public boolean confirmOffer(UUID uuidOffer) {
		// TODO Auto-generated method stub
		return true;
	}

	public void cancelOffer(UUID uuidOffer) {
		// TODO Auto-generated method stub
	}

	public void setDevice(UUID uuid) {
		device = uuid;
	}

	public void loadprofile(DeviceLoadprofile device) {
		Log.i(uuid + " [consumer] received loadprofile from device");
		Log.i(device.toString());

		this.loadprofile = new Loadprofile(device);

		this.offer = new Offer(loadprofile, this, 0.0);
		OfferNotification notification = new OfferNotification(
				"http://localhost:8080/consumers/" + uuid + "/offers/" + offer.getUUID(), null);

		RestTemplate rest = new RestTemplate();

		HttpEntity<OfferNotification> entity = new HttpEntity<OfferNotification>(notification,
				Application.getRestHeader());

		String url;

		for (Consumer c : getAllConsumers()) {
			url = "http://localhost:8080/consumers/" + c.getUUID() + "/offers";
			Log.i("#500 @ send offer: " + url);

			try {
				rest.exchange(url, HttpMethod.POST, entity, String.class);
			} catch (Exception e) {
				Log.e("loadprofile", e.getMessage());
			}
		}
	}

	private Fridge[] getAllDevices() {
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Fridge[]> devices = rest.exchange("http://localhost:8080/devices", HttpMethod.GET, null,
				Fridge[].class);

		return devices.getBody();
	}

	private Consumer[] getAllConsumers() {
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Consumer[]> consumers = rest.exchange("http://localhost:8080/consumers", HttpMethod.GET, null,
				Consumer[].class);

		return consumers.getBody();
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new HashMap<String, Object>();
		// TODO
		return map;
	}

	public Offer[] getOffers() {
		return new Offer[] { this.offer };
	}
}
