package Entity;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;

import Event.InvalidOffer;
import Packet.OfferNotification;
import Util.Log;
import start.Application;
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
	private ConcurrentLinkedQueue<OfferNotification> notificationQueue = new ConcurrentLinkedQueue<OfferNotification>();

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
		GregorianCalendar dateOffer = offer.getAggLoadprofile().getDate();
		GregorianCalendar dateLoadprofile = loadprofile.getDate();

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

			notificationQueue.add(offerNotification);
		} else {
			throw new InvalidOffer();
		}
	}

	public void ping() {
		OfferNotification notification = notificationQueue.poll();

		if (notification == null) {
			Log.d("No current notifications available");
			return;
		}

		RestTemplate rest = new RestTemplate();
		HttpEntity<Void> entity = new HttpEntity<Void>(Application.getRestHeader());

		String url = notification.getLocation();
		Log.i("request offer at: " + url);

		try {
			ResponseEntity<Offer> responseOffer = rest.exchange(url, HttpMethod.GET, entity, Offer.class);
			Log.d(responseOffer.getBody().toString());
		} catch (Exception e) {
			Log.e(e.getMessage());
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

	public void receiveDeltaLoadprofile(Loadprofile device) {
		Log.e("function not yet implemented...");
	}

	public void receiveLoadprofile(Loadprofile loadprofile) {
		Log.i(uuid + " [consumer] received loadprofile from device");
		Log.i(loadprofile.toString());

		this.loadprofile = loadprofile;

		this.offer = new Offer(loadprofile, this, 0.0);
		OfferNotification notification = new OfferNotification(
				"http://localhost:8080/consumers/" + uuid + "/offers/" + offer.getUUID(), null);

		RestTemplate rest = new RestTemplate();

		HttpEntity<OfferNotification> entity = new HttpEntity<OfferNotification>(notification,
				Application.getRestHeader());

		String url;

		for (Consumer c : getAllConsumers()) {
			url = "http://localhost:8080/consumers/" + c.getUUID() + "/offers";
			Log.i("send offer: " + url);

			try {
				rest.exchange(url, HttpMethod.POST, entity, String.class);
			} catch (Exception e) {
				Log.e(e.getMessage());
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
