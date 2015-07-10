package Entity;

import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;

import Event.InvalidOrOutdatedKey;
import Event.OfferAccepted;
import Event.AcceptContract;
import Event.DeclineContract;
import Event.InvalidOffer;
import Packet.OfferNotification;
import Packet.ConfirmOffer;
import Util.API;
import Util.DateTime;
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
	private Offer ownOffer = null;

	// Angebote mit DeltaLastprofilen
	private Map<UUID, Offer> deltaOffers = new HashMap<UUID, Offer>();

	// Erhaltene DeltaLastprofile nach Datum, zu welchen es noch kein Offer gibt
	private Hashtable<String, double[]> deltaLoadprofiles = new Hashtable<String, double[]>();

	// Anzahl der 15-Minuten-Slots für ein Lastprofil
	private int numSlots = 4;

	// Aktuelles Stundenlastprofil (evtl. auch schon aggregiert mit anderen
	// Teilnehmern)
	private Loadprofile loadprofile = null;
	private ConcurrentLinkedQueue<OfferNotification> notificationQueue = new ConcurrentLinkedQueue<OfferNotification>();
	private ConcurrentHashMap<UUID, Offer> offerMap = new ConcurrentHashMap<UUID, Offer>();

	private int maxMarketplaceOffersToCompare;

	// Bereits bestätigte Lastprofile

	public int getNumSlots() {
		return numSlots;
	}

	public UUID getUUID() {
		return uuid;
	}

	public Offer getOffer(UUID uuidOffer) {
		return ownOffer;
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

	public Offer[] getOffers() {
		return new Offer[] { this.ownOffer };
	}

	public Consumer() {
		uuid = UUID.randomUUID();
		this.maxMarketplaceOffersToCompare = 5;
	}

	public Consumer(int maxMarketplaceOffersToCompare) {
		this();
		this.maxMarketplaceOffersToCompare = maxMarketplaceOffersToCompare;
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

	private boolean isValidOfferDate(Offer offer) {
		GregorianCalendar dateOffer = offer.getAggLoadprofile().getDate();
		GregorianCalendar dateLoadprofile = loadprofile.getDate();

		if (DateTime.ToString(dateOffer).equals(DateTime.ToString(dateLoadprofile))) {
			return true;
		} else {
			return false;
		}
	}

	private boolean improveLoadprofileAverage(Offer offer) {
		Loadprofile aggLoadprofile = new Loadprofile(loadprofile, offer.getAggLoadprofile());

		double deviation = loadprofile.chargeDeviationAverage();
		double aggDeviation = aggLoadprofile.chargeDeviationAverage();

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	private boolean improveLoadprofileApproximation(Offer offer, Loadprofile otherProfile) {
		Loadprofile aggLoadprofile = new Loadprofile(loadprofile, offer.getAggLoadprofile());

		double deviation = loadprofile.chargeDeviationOtherProfile(otherProfile);
		double aggDeviation = aggLoadprofile.chargeDeviationOtherProfile(otherProfile);

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	private Offer getOfferFromNotification(OfferNotification notification) {
		RestTemplate rest = new RestTemplate();
		HttpEntity<Void> entityVoid = new HttpEntity<Void>(Application.getRestHeader());

		String url = notification.getLocation();
		Log.i("request offer at: " + url);

		Offer offer = null;
		try {
			ResponseEntity<Offer> responseOffer = rest.exchange(url, HttpMethod.GET, entityVoid, Offer.class);
			offer = responseOffer.getBody();
		} catch (Exception e) {
			Log.e(e.getMessage());
		}

		return offer;
	}

	public void ping() {
		OfferNotification notification = notificationQueue.poll();

		if (notification == null) {
			Log.d("No current notifications available");
			return;
		}

		Offer offer = getOfferFromNotification(notification);

		if (offer == null) {
			Log.e("offer unavailable...");
			return;
		}

		if (!offer.isValid()) {
			Log.e("offer invalid...");
			return;
		}

		if (!isOfferBetter(offer)) {
			Log.e("offer not better - but for testing continued");
			// return;
		}

		if (offer.getAllLoadprofiles().containsKey(uuid)) {
			Log.d("work on offer");
			workOnContractOffer(offer);
		} else {
			Log.d("work on notification");
			workOnLoadprofileOffer(offer);
		}
	}

	private boolean isOfferBetter(Offer offer) {
		if (!isValidOfferDate(offer)) {
			Log.e("invalid offer - offer is for different time");
			return false;
		}

		Offer[] supplies = getMarketplaceSupplies(maxMarketplaceOffersToCompare);

		for (Offer o : supplies) {
			if (improveLoadprofileApproximation(o, loadprofile)) {
				Log.d("offer is better by marketplace approximation");
				return true;
			}
		}

		if (improveLoadprofileAverage(offer)) {
			Log.d("offer is better by average");
			return true;
		}

		Log.d("offer is not better");

		return false;
	}

	private void workOnLoadprofileOffer(Offer oldOffer) {
		// offer not yet part of
		Offer newOffer = new Offer(uuid, loadprofile, new Loadprofile(loadprofile, oldOffer.getAggLoadprofile()),
				oldOffer);
		offerMap.put(newOffer.getUUID(), newOffer);

		OfferNotification notification = new OfferNotification(newOffer.getLocation(), oldOffer.getLocation());

		String url = new API().consumers(oldOffer.getAuthor()).offers().toString();
		Log.i("post offer for contract at: " + url);

		try {
			RequestEntity<OfferNotification> request = RequestEntity.post(new URI(url))
					.accept(MediaType.APPLICATION_JSON).body(notification);
			new RestTemplate().exchange(request, Void.class);
		} catch (Exception e) {
			Log.e(e.getMessage());
		}
	}

	private void workOnContractOffer(Offer offer) {
		// confirm offer
		API api = new API().consumers(offer.getAuthor()).offers(offer.getAuthor()).confirm(offer.getKey());
		Log.i("confirm offer at: " + api.toString());

		ResponseEntity<Boolean> response = null;
		try {
			HttpEntity<Void> entity = new HttpEntity<Void>(Application.getRestHeader());
			response = new RestTemplate().exchange(api.toString(), HttpMethod.GET, entity, Boolean.class);
		} catch (Exception e) {
			Log.e(e.getMessage());
		}

		Log.d("contract immediate response: " + response.getBody());

		// check response
		if (response == null || response.getBody() == false) {
			Log.d("contract " + offer.getUUID() + " declined");
			throw new DeclineContract();
		} else {
			Log.e("contract " + offer.getUUID() + " accepted");
			throw new AcceptContract();
		}
	}

	private Offer[] getMarketplaceSupplies(int i) {
		// TODO
		return new Offer[0];
	}

	public void confirmOffer(UUID uuidOffer, UUID key) {
		if (!ownOffer.getKey().equals(key)) {
			throw new InvalidOrOutdatedKey();
		} else {
			throw new OfferAccepted();
		}
	}

	public void cancelOffer(UUID uuidOffer) {
		// TODO Auto-generated method stub
	}

	public void setDevice(UUID uuid) {
		device = uuid;
	}

	public void receiveLoadprofile(Loadprofile loadprofile) {
		Log.i(uuid + " [consumer] received loadprofile from device");
		Log.i(loadprofile.toString());

		this.loadprofile = loadprofile;

		this.ownOffer = new Offer(uuid, loadprofile);
		OfferNotification notification = new OfferNotification(
				"http://localhost:8080/consumers/" + uuid + "/offers/" + ownOffer.getUUID(), null);

		RestTemplate rest = new RestTemplate();

		HttpEntity<OfferNotification> entity = new HttpEntity<OfferNotification>(notification,
				Application.getRestHeader());

		String url;

		for (Consumer c : getAllConsumers()) {
			if (c.getUUID().equals(uuid)) {
				// do not send notifications to itself
				break;
			}

			url = new API().consumers(c.getUUID()).offers().toString();
			Log.i("send offer: " + url);

			try {
				rest.exchange(url, HttpMethod.POST, entity, String.class);
			} catch (Exception e) {
				Log.e(e.getMessage());
			}
		}
	}

	public void receiveOfferNotification(OfferNotification offerNotification) {
		Log.i(uuid + " [consumer] received offer");
		Log.i(offerNotification.toString());

		notificationQueue.add(offerNotification);
	}

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.i(uuid + " [consumer] received deltaloadprofile from device");
		Log.i(deltaLoadprofile.toString());
		GregorianCalendar timeLoadprofile = deltaLoadprofile.getDate();
		GregorianCalendar timeCurrent = DateTime.now();
		double[] valuesNew = deltaLoadprofile.getValues();

		// Prüfe, ob deltaLoadprofile Änderungen für die aktuelle Stunde hat
		boolean currentHour = timeLoadprofile.get(Calendar.HOUR_OF_DAY) == timeCurrent.get(Calendar.HOUR_OF_DAY);
		if (currentHour) {
			// Prüfe, ob deltaLoadprofile Änderungen für die noch kommenden
			// Slots beinhaltet
			int minuteCurrent = timeCurrent.get(Calendar.MINUTE);
			int slot = (int) Math.floor(minuteCurrent / 15) + 1;
			double sum = 0;
			for (int i = slot; i < numSlots; i++) {
				sum = sum + valuesNew[i];
			}
			if (sum == 0) {
				return;
			} else {
				for (int j = 0; j < slot; j++) {
					valuesNew[j] = 0;
				}
			}
		}

		// Prüfe, welche Werte für die Stunde von deltaLoadprofile bereits in
		// deltaLoadprofiles hinterlegt sind
		double[] valuesOld = deltaLoadprofiles.get(DateTime.ToString(timeLoadprofile));
		double sum = 0;

		// Erstelle Summe aus valuesOld und den valuesNew vom deltaLoaprofile
		if (valuesOld != null) {
			for (int i = 0; i < numSlots; i++) {
				valuesNew[i] = valuesOld[i] + valuesNew[i];
				sum = sum + valuesNew[i];
			}
		}

		// Versende Deltalastprofile mit Summe>5 oder aus der aktuellen Stunde
		// sofort
		if (currentHour || sum >= 5) {
			deltaLoadprofile = new Loadprofile(valuesNew, timeLoadprofile);

			// Erstelle Angebot aus deltaLoadprofile, speichere es in
			// deltaOffers und verschicke es
			Offer deltaOffer = new Offer(uuid, deltaLoadprofile);
			deltaOffers.put(deltaOffer.getUUID(), deltaOffer);

			OfferNotification notification = new OfferNotification(
					"http://localhost:8080/consumers/" + uuid + "/offers/" + deltaOffer.getUUID(), null);

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
		// Sammle Deltalastprofile mit Summe<5 für die nächsten Stunden
		else {
			deltaLoadprofiles.put(DateTime.ToString(timeLoadprofile), valuesNew);
		}
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new HashMap<String, Object>();

		map.put("uuid", uuid);
		map.put("device uuid", device);
		map.put("offer uuid", ownOffer.getUUID());
		map.put("startOffer", ownOffer.getAggLoadprofile().getDate());
		map.put("numberOfDeltaOffers", deltaOffers.keySet().size());
		map.put("numberOfDeltaLoadprofiles", deltaLoadprofiles.keySet().size());
		map.put("numberInNotificationQueue", notificationQueue.size());

		return map;
	}

	public Loadprofile getLoadprofile() {
		return loadprofile;
	}

	public void confirmOfferByMarketplace(ConfirmOffer confirmOffer) {
		boolean deltaOffer = deltaOffers.get(confirmOffer.getOffer()) != null;
		if (deltaOffer) {
			deltaOffers.remove(confirmOffer.getOffer());
		} else {
			if (ownOffer.getUUID() == confirmOffer.getOffer()) {
				// Schicke Bestätigung zu Loadprofile an Device
				String date = DateTime.ToString(ownOffer.getAggLoadprofile().getDate());

				RestTemplate rest = new RestTemplate();
				HttpEntity<String> entity = new HttpEntity<String>(date, Application.getRestHeader());

				String url = "http://localhost:8080/devices/" + device + "/confirmLoadprofile";

				try {
					ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
				} catch (Exception e) {
					Log.i(url);
					Log.e(e.getMessage());
				}

				ownOffer = null;
				// TODO Speichere Lastprofil in Historie ab
				loadprofile = null;
			}
			// TODO was passiert, wenn bestätigtes Angebot weder aktuelles
			// Angebot noch Deltalastprofil?
		}
	}
}
