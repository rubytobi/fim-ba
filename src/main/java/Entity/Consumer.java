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
	private Offer offer = null;

	// Angebote mit DeltaLastprofilen
	private ArrayList<Offer> deltaOffers = new ArrayList<Offer>();

	// Erhaltene DeltaLastprofile nach Datum, zu welchen es noch kein Offer gibt
	private Hashtable<String, double[]> deltaLoadprofiles = new Hashtable<String, double[]>();

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

	private boolean isValidOfferDate(Offer offer) {
		GregorianCalendar dateOffer = offer.getAggLoadprofile().getDate();
		GregorianCalendar dateLoadprofile = loadprofile.getDate();

		if (dateOffer == dateLoadprofile) {
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

		if (!isValidOfferDate(offer)) {
			Log.d("offerdate invalid");
			return;
		}

		Offer[] supplies = getMarketplaceSupplies(5);
		boolean improveLoadprofileApproximation = false;

		for (Offer o : supplies) {
			if (improveLoadprofileApproximation(o, loadprofile)) {
				improveLoadprofileApproximation = true;
				break;
			}
		}

		if (!improveLoadprofileApproximation && !improveLoadprofileAverage(offer)) {
			Log.d("No improvement possible.");
			return;
		}

		Offer toBeContract = new Offer(uuid, loadprofile, new Loadprofile(loadprofile, offer.getAggLoadprofile()),
				offer);

		HttpEntity<Offer> entityOffer = new HttpEntity<Offer>(toBeContract, Application.getRestHeader());

		url = "http://localhost:8080/consumers/" + offer.getAuthor() + "/contracts";
		Log.i("post contract at: " + url);

		try {
			rest.exchange(url, HttpMethod.GET, entityOffer, Void.class);
		} catch (Exception e) {
			Log.e(e.getMessage());
		}
	}

	private Offer[] getMarketplaceSupplies(int i) {
		return new Offer[0];
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

	public void receiveLoadprofile(Loadprofile loadprofile) {
		Log.i(uuid + " [consumer] received loadprofile from device");
		Log.i(loadprofile.toString());

		this.loadprofile = loadprofile;

		this.offer = new Offer(uuid, loadprofile);
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

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.i(uuid + " [consumer] received deltaloadprofile from device");
		Log.i(loadprofile.toString());
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
			deltaOffers.add(deltaOffer);

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
		// Sammle Deltalastprofile mit Summe<5 für die nächsten Stunden
		else {
			deltaLoadprofiles.put(DateTime.ToString(timeLoadprofile), valuesNew);
		}
	}
}
