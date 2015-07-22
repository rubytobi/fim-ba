package Entity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;

import Packet.OfferNotification;
import Packet.ConfirmOffer;
import Util.API;
import Util.DateTime;
import Util.Log;
import Util.OfferAction;
import start.Application;
import start.Loadprofile;
import start.View;

public class Consumer {
	/**
	 * Alle Angebote mit denen verhandelt wird oder wurde
	 */
	@JsonView(View.Detail.class)
	private ConcurrentHashMap<UUID, Offer> allOffers = new ConcurrentHashMap<UUID, Offer>();

	/**
	 * Enhaltene DeltaLastprofile nach Datum, zu welchen es noch kein Angebot
	 * gibt
	 */
	@JsonView(View.Detail.class)
	private HashMap<String, double[]> deltaLoadprofiles = new HashMap<String, double[]>();

	/**
	 * Geräte-ID des zu verantwortenden Geräts
	 */
	@JsonView(View.Summary.class)
	private UUID device = null;

	/**
	 * Aktuelles Stundenlastprofil (evtl. auch schon aggregiert mit anderen
	 * Teilnehmern)
	 */
	// private Loadprofile loadprofile = null;

	/**
	 * Maximale Anzahl der zu vergleichenden Martplatz-Angebote
	 */
	private int maxMarketplaceOffersToCompare;

	/**
	 * Queue an Benachrichtigungen über Angebote in Reinfolge der Ankunft
	 */
	private ConcurrentLinkedQueue<Object[]> notificationQueue = new ConcurrentLinkedQueue<Object[]>();

	/**
	 * Anzahl der 15-Minuten-Slots für ein Lastprofil
	 */
	private int numSlots = 4;

	/**
	 * Consumer-ID
	 */
	@JsonView(View.Summary.class)
	private UUID uuid;

	private HashMap<UUID, Offer> allOfferMerges = new HashMap<UUID, Offer>();

	/**
	 * Anlage eines neuen Consumers. ID und maxMarketplaceOffersToCompare werden
	 * standardmäßig gesetzt
	 */
	public Consumer() {
		uuid = UUID.randomUUID();
		this.maxMarketplaceOffersToCompare = 5;
	}

	/**
	 * Anlage eines neuen Consumers. ID wird standardmäßig gesetzt
	 * 
	 * @param maxMarketplaceOffersToCompare
	 *            Maximale Anzahl an zu vergleichenden Angeboten
	 */
	public Consumer(int maxMarketplaceOffersToCompare) {
		this();
		this.maxMarketplaceOffersToCompare = maxMarketplaceOffersToCompare;
	}

	/**
	 * Prüft ein Angebot auf Gültigkeit bzgl. verfügbarkeit, gültigkeit,
	 * zeitenslotgleichheit und verbesserung
	 * 
	 * @param externalOffer
	 *            Vergleichsangebot
	 */
	private boolean validateOffer(Offer externalOffer, Offer internalOffer) {
		if (externalOffer == null) {
			return false;
		}

		if (!externalOffer.isValid()) {
			Log.e(this.uuid, "offer invalid...");
			return false;
		}

		String offerDate = DateTime.ToString(externalOffer.getDate());
		// String loadprofileDate = DateTime.ToString(loadprofile.getDate());
		String loadprofileDate = DateTime.ToString(internalOffer.getDate());
		if (!offerDate.equals(loadprofileDate)) {
			Log.e(this.uuid, "offer dates different...");
			return false;
		}

		if (!isOfferBetter(externalOffer, internalOffer)) {
			Log.e(this.uuid, "offer not better - but for testing continued");
			// return false;
		}

		return true;
	}

	private void addOffer(Offer offer) {
		Log.d(uuid, "add offer " + offer.getUUID());
		this.allOffers.put(offer.getUUID(), offer);
	}

	private Offer getOfferIntern(UUID offer) {
		// Log.d(uuid, "get offer" + offer);
		return this.allOffers.get(offer);
	}

	public Offer getOffer(UUID offer) {
		return getOfferIntern(offer);
	}

	/**
	 * Auf ein versandtes Angbeot hat ein anderer Consumer geantwortet. Es wird
	 * geprüft ob das Angebot auch für diesen Consumer einen Mehrwehrt bietet.
	 * Wenn ja, wird das Angebot via consumers/uuid/offers/uuid/confirm
	 * bestätigt.
	 * 
	 * @param respondedOfferUUID
	 *            ID des Angebots auf das reagiert wurde
	 * @param offerNotification
	 *            Angebotsbenachrichtigung
	 */
	public void answerOffer(UUID respondedOfferUUID, OfferNotification offerNotification) {
		Offer offer = getOfferFromUrl(offerNotification.getLocation());
		// check offer at its location if valid, date and if it is better

		// confirm offer
		API api = new API().consumers(offer.getAuthor()).offers(offer.getUUID()).confirm(offer.getAuthKey());
		Log.d(this.uuid, "confirm offer at: " + api.toString());

		ResponseEntity<Boolean> response = null;
		try {
			HttpEntity<Void> entity = new HttpEntity<Void>(Application.getRestHeader());
			response = new RestTemplate().exchange(api.toString(), HttpMethod.GET, entity, Boolean.class);
		} catch (Exception e) {
			Log.e(this.uuid, e.getMessage());
		}

		Log.d(this.uuid, "contract immediate response: " + response.getBody());

		// check response
		if (response == null || response.getBody() != true) {
			// Angebot wurde abgelehnt.
			Log.d(this.uuid, "contract " + offer.getUUID() + " declined");
			return;
		}

		// Angbeot wurde angenommen
		Log.e(this.uuid, "contract " + offer.getUUID() + " accepted");

		// initiales Angebot muss am Marktplatz invalidiert werden.
		// api = new
		// API().marketplace().demand(respondedOfferUUID).invalidate();
		// try {
		// RequestEntity.get(new
		// URI(api.toString())).accept(MediaType.APPLICATION_JSON);
		// } catch (URISyntaxException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		// Das neue Angebot wird hinterlegt.
		addOffer(offer);

		// Angebot das initial verschickt wurde muss entfernt werden.
		Offer respondedOffer = getOfferIntern(respondedOfferUUID);
		removeOffer(respondedOfferUUID);

		// Consumer des alten Angebots müssen mit dem neuen Angebot versorgt
		// werden
		for (UUID consumerUUID : respondedOffer.getAllLoadprofiles().keySet()) {
			if (consumerUUID.equals(uuid)) {
				// sich selber überspringen und den author des letzten vertrags
				// (eigene anpassung durch confirm)
				continue;
			}

			String url = new API().consumers(consumerUUID).offers(respondedOffer.getUUID()).replace(offer.getUUID())
					.toString();
			RestTemplate rest = new RestTemplate();
			Log.d(uuid, url);
			HttpHeaders headers = new HttpHeaders();
			headers.add("author", uuid.toString());
			HttpEntity<Void> entity = new HttpEntity<>(headers);
			rest.exchange(url, HttpMethod.GET, entity, Void.class);
		}

		// Lastprofil muss beim Gerät bestätigt werden, Deltalastprofile nicht
		HashMap<UUID, HashMap<UUID, Loadprofile>> allLoadprofiles = respondedOffer.getAllLoadprofiles();
		if (allLoadprofiles == null) {
			return;
		}
		for (Loadprofile lp : allLoadprofiles.get(uuid).values()) {
			if (!lp.isDelta()) {
				// TODO
			} else {
				// TODO
			}
		}
	}

	public void cancelOffer(UUID uuidOffer) {
		Log.d(this.uuid, uuid + " bekommt Absage zu Angebot " + uuidOffer);
		// TODO
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

	/**
	 * Der Author innerhalb eines Angebots kann neue Angebote aushandeln. Jeder
	 * Consumer muss hinterher den neuen Vertrag erhalten. Der bestehende
	 * Vertrag wird einfach ausgetauscht. Dabei muss das neue Angebot vom Author
	 * des alten Angebots geschickt werden.
	 * 
	 * @param oldOffer
	 * @param newOffer
	 */
	public void replaceOffer(UUID oldOffer, UUID newOffer, UUID author) {
		Log.d(uuid, new API().consumers(uuid).offers(oldOffer).replace(newOffer).toString() + " by " + author);

		if (getOfferIntern(oldOffer) == null)
			// if (allOffers.get(oldOffer) == null)
			return;

		// prüfen ob neues angebot vom author des alten kommt
		if (!getOfferIntern(oldOffer).getAuthor().equals(author))
			// if (!allOffers.get(oldOffer).getAuthor().equals(author))
			return;

		// das neue Angebot besorgen
		String url = new API().consumers(getOfferIntern(oldOffer).getAuthor()).offers(newOffer).toString();
		Offer offer = getOfferFromUrl(url);

		if (offer == null) {
			// TODO what? darf nicht sein!
			Log.e(uuid, url);
			return;
		}

		// altes Angebot entfernen
		removeOffer(oldOffer);

		// neues Angebot einfügen
		addOffer(offer);
	}

	/**
	 * Ein Consumer möchte ein Angbeot dieses Consumers bestätigen. Er übergibt
	 * hierzu den AuthKey des Angebots. Das Vorgängerangebot muss nun
	 * invalidiert werden und entfernt werde. Mitglieder des Vorgängerangebots
	 * müssen mit dem neuen Angbeot versorgt werden.
	 * 
	 * @param uuidOffer
	 *            Angebots-ID welches bestätigt wird
	 * @param authKey
	 *            AuthKey um bestätigung zu verifizieren
	 * @return
	 */
	public boolean confirmOfferByConsumer(UUID uuidOffer, UUID authKey) {
		Log.d(uuid, "-- START confirmOfferByConsumer --");
		// Offer offer = allOffers.get(uuidOffer);
		Offer offer = getOfferIntern(uuidOffer);

		// Prüfen ob "AuthKey" übereinstimmt
		if (!offer.getAuthKey().equals(authKey)) {
			return false;
		}

		// Alte Angebot suchen, mit dem das hier akzeptierte Angebot erweitert
		// wurde
		Offer v2 = getOfferIntern(uuidOffer);
		Offer vX = allOfferMerges.remove(v2.getUUID());
		removeOffer(vX.getUUID());

		// Consumer des alten Angebots müssen mit dem neuen Angebot versorgt
		// werden
		for (UUID consumerUUID : vX.getAllLoadprofiles().keySet()) {
			if (consumerUUID.equals(uuid)) {
				// sich selber überspringen
				continue;
			}

			String url = new API().consumers(consumerUUID).offers(vX.getUUID()).replace(offer.getUUID()).toString();
			Log.d(uuid, url);
			RestTemplate rest = new RestTemplate();
			HttpHeaders header = new HttpHeaders();
			header.add("author", this.uuid.toString());
			HttpEntity<Void> entity = new HttpEntity<Void>(header);
			rest.exchange(url, HttpMethod.GET, entity, Void.class);
		}

		OfferNotification notification = new OfferNotification(offer.getLocation(), offer.getUUID());
		sendOfferNotificationToAllConsumers(notification);

		Log.d(uuid, "-- END confirmOfferByConsumer --");
		return true;
	}

	private void removeOffer(UUID offer) {
		Log.d(uuid, "remove offer: " + offer);
		this.allOffers.remove(offer);
	}

	/**
	 * Der Marktplatz bestätigt ein Angebot
	 * 
	 * @param confirmOffer
	 *            das bestätigte Angebot
	 */
	public void confirmOfferByMarketplace(ConfirmOffer confirmOffer) {
		// Offer offer = allOffers.get(confirmOffer.getUuid());
		Offer offer = getOfferIntern(confirmOffer.getUuid());

		for (Loadprofile lp : offer.getAllLoadprofiles().get(uuid).values()) {
			if (lp.isDelta()) {
				// Schicke Bestätigung zu Loadprofile an Device
				String date = DateTime.ToString(offer.getAggLoadprofile().getDate());

				RestTemplate rest = new RestTemplate();
				HttpEntity<String> entity = new HttpEntity<String>(date, Application.getRestHeader());

				String url = new API().devices(device).confirmLoadprofile().toString();

				try {
					rest.exchange(url, HttpMethod.POST, entity, Void.class);
				} catch (Exception e) {
					Log.d(this.uuid, url);
					Log.e(this.uuid, e.getMessage());
				}
			}

			// TODO Speichere Lastprofil in Historie ab
		}

		removeOffer(offer.getUUID());
	}

	/**
	 * Ermittelt alle Consumer ohne sich selber
	 * 
	 * @return Consumers
	 */
	private Consumer[] getAllConsumers() {
		RestTemplate rest = new RestTemplate();

		String url = new API().consumers().toString();
		ResponseEntity<Consumer[]> consumers = null;
		try {
			consumers = rest.exchange(url, HttpMethod.GET, null, Consumer[].class);
		} catch (Exception e) {
			Log.e(this.uuid, url);
		}

		Consumer[] list = new Consumer[consumers.getBody().length - 1];

		int i = 0;
		for (Consumer c : consumers.getBody()) {
			if (!c.getUUID().equals(uuid)) {
				list[i] = c;
				i++;
			}
		}

		return list;
	}

	public Offer[] getAllOffers() {
		return allOffers.values().toArray(new Offer[allOffers.size()]);
	}

	private Offer[] getMarketplaceSupplies(int i) {
		// TODO
		return new Offer[0];
	}

	public int getNumSlots() {
		return numSlots;
	}

	private Offer getOfferFromUrl(String url) {
		RestTemplate rest = new RestTemplate();
		HttpEntity<Void> entityVoid = new HttpEntity<Void>(Application.getRestHeader());

		Offer offer = null;
		try {
			ResponseEntity<Offer> responseOffer = rest.exchange(url, HttpMethod.GET, entityVoid, Offer.class);
			offer = responseOffer.getBody();
		} catch (Exception e) {
			Log.e(this.uuid, e.getMessage());
		}

		return offer;
	}

	public UUID getUUID() {
		return uuid;
	}

	private boolean improveLoadprofileApproximation(Offer offer, Loadprofile otherProfile, Offer internalOffer) {
		Loadprofile aggLoadprofile = new Loadprofile(internalOffer.getAggLoadprofile(), offer.getAggLoadprofile());

		double deviation = internalOffer.getAggLoadprofile().chargeDeviationOtherProfile(otherProfile);
		double aggDeviation = aggLoadprofile.chargeDeviationOtherProfile(otherProfile);

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	private boolean improveLoadprofileAverage(Offer offer, Offer internalOffer) {
		Loadprofile aggLoadprofile = new Loadprofile(internalOffer.getAggLoadprofile(), offer.getAggLoadprofile());

		double deviation = internalOffer.getAggLoadprofile().chargeDeviationAverage();
		double aggDeviation = aggLoadprofile.chargeDeviationAverage();

		if (aggDeviation < deviation) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Prüft ob das Angebot einen mehrwert für den Consumer liefert. Validiert
	 * wird zuerst das Datum. Anschließend wird die Verbesserung gegen Angebeote
	 * am Marktplatz geprüft und anschließend eine begradigung der Lastkurve.
	 * 
	 * @param offer
	 * @return
	 */
	private boolean isOfferBetter(Offer offer, Offer internalOffer) {
		if (!isValidOfferDate(offer, internalOffer)) {
			Log.e(this.uuid, "invalid offer - offer is for different time");
			return false;
		}

		Offer[] supplies = getMarketplaceSupplies(maxMarketplaceOffersToCompare);

		for (Offer o : supplies) {
			if (improveLoadprofileApproximation(o, internalOffer.getAggLoadprofile(), internalOffer)) {
				Log.d(this.uuid, "offer is better by marketplace approximation");
				return true;
			}
		}

		if (improveLoadprofileAverage(offer, internalOffer)) {
			Log.d(this.uuid, "offer is better by average");
			return true;
		}

		Log.d(this.uuid, "offer is not better");
		return false;
	}

	/**
	 * Prüft ob Zeitpunkt des Angebots mit dem des Lastprofils übereinstimmt
	 * 
	 * @param offer
	 *            Angebot
	 * @return true/false
	 */
	private boolean isValidOfferDate(Offer offer, Offer internalOffer) {
		GregorianCalendar dateOffer = offer.getAggLoadprofile().getDate();
		GregorianCalendar dateLoadprofile = internalOffer.getDate();

		if (DateTime.ToString(dateOffer).equals(DateTime.ToString(dateLoadprofile))) {
			return true;
		} else {
			return false;
		}
	}

	public String toString() {
		return "[uuid=" + uuid + "]";
	}

	/**
	 * Sucht ein Angbeot aus dem gleichen Zeitslot bei dem der aktuelle Consumer
	 * auch Author ist und daher verhandeln darf.
	 * 
	 * @param date
	 *            entsprechender Zeitslot
	 * @param offerToBeExcluded
	 *            Angebot das ausgeschlossen werden soll
	 * @return Angebot
	 */
	public Offer getOfferWithPrivileges(GregorianCalendar date) {
		for (Offer o : allOffers.values()) {
			if (o.isAuthor(uuid) && o.getDate().equals(date)) {
				return o;
			}
		}

		return null;
	}

	/**
	 * Arbeitet im ping-Rhythmus eingetroffene Benachrichtigungen über Angebote
	 * ab. Die Benachrichtigung enthält die Adresse des Angebots. Es wird
	 * geprüft, ob das Angebot gültig ist, den richtigen Zeitslot beinhaltet und
	 * ob es besser ist. Wenn ja, wird ein neues Angebot erstellt, dieses
	 * abgelegt und zurück gesandt an consumers/uuid/answer.
	 */
	public void ping() {
		Object[] object = notificationQueue.poll();

		if (object == null)
			return;

		OfferAction action = (OfferAction) object[0];
		OfferNotification notification = (OfferNotification) object[1];

		if (action.equals(OfferAction.SEND)) {
			sendOfferNotificationToAllConsumers(notification);
			return;
		}

		Offer offer = getOfferFromUrl(notification.getLocation());

		// Angbeot nicht mehr vorhanden
		if (offer == null)
			return;

		// wenn kein eigenes Lastprofil mehr behandelt werden muss, nicht
		// reagieren auf Angebot
		Offer offerWithPrivileges = getOfferWithPrivileges(offer.getDate());

		if (offerWithPrivileges == null) {
			Log.d(uuid, " not able to deal");
			return;
		}

		if (!validateOffer(offer, offerWithPrivileges)) {
			Log.d(this.uuid, "Offer could not be validated in improvement, time, ...");
			return;
		}

		Log.e(this.uuid, offer.toString());

		// neues Angebot erstellen und ablegen
		Offer newOffer = new Offer(offerWithPrivileges, offer);
		addOffer(newOffer);
		allOfferMerges.put(newOffer.getUUID(), offerWithPrivileges);

		// neue notification erstellen
		OfferNotification newNotification = new OfferNotification(newOffer.getLocation(), newOffer.getUUID());

		// notification versenden
		String url = new API().consumers(offer.getAuthor()).offers(offer.getUUID()).answer().toString();

		try {
			RequestEntity<OfferNotification> request = RequestEntity.post(new URI(url))
					.accept(MediaType.APPLICATION_JSON).body(newNotification);
			new RestTemplate().exchange(request, Void.class);
		} catch (Exception e) {
			Log.e(this.uuid, e.getMessage());
		}
	}

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.d(this.uuid,
				uuid + " [consumer] received deltaloadprofile [" + deltaLoadprofile.toString() + "] from device");
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
			// TODO Preisbestimmung muss angepasst werden
			deltaLoadprofile = new Loadprofile(valuesNew, timeLoadprofile, 0.0);

			// Erstelle Angebot aus deltaLoadprofile, speichere es in
			// deltaOffers und verschicke es
			Offer deltaOffer = new Offer(uuid, deltaLoadprofile);
			allOffers.put(deltaOffer.getUUID(), deltaOffer);

			OfferNotification notification = new OfferNotification(
					new API().consumers(uuid).offers(deltaOffer.getUUID()).toString(), null);

			RestTemplate rest = new RestTemplate();

			HttpEntity<OfferNotification> entity = new HttpEntity<OfferNotification>(notification,
					Application.getRestHeader());

			String url;

			for (Consumer c : getAllConsumers()) {
				url = "http://localhost:8080/consumers/" + c.getUUID() + "/offers";
				Log.d(this.uuid, "send offer: " + url);

				try {
					rest.exchange(url, HttpMethod.POST, entity, String.class);
				} catch (Exception e) {
					Log.e(this.uuid, e.getMessage());
				}
			}
		}
		// Sammle Deltalastprofile mit Summe<5 für die nächsten Stunden
		else {
			deltaLoadprofiles.put(DateTime.ToString(timeLoadprofile), valuesNew);
		}
	}

	/**
	 * Der Consumer erhält ein Lastprofil seines eigenen Gerätes. Das Lastprofil
	 * wird abgelegt und ein Angebot dafür erstellt. Für das Angebot wird nun
	 * eine Benachrichtigung erstellt, intern abgelegt und an alle bekannten
	 * anderen Consumer verschickt.
	 * 
	 * @param loadprofile
	 *            Lastprofil des Gerätes
	 */
	public void receiveLoadprofile(Loadprofile loadprofile) {
		if (loadprofile.isDelta()) {
			// skip delta loadprofiles
			return;
		}

		Log.d(this.uuid, loadprofile.toString());

		Offer offer = new Offer(uuid, loadprofile);
		addOffer(offer);

		OfferNotification notification = new OfferNotification(offer.getLocation(), offer.getUUID());

		sendOfferNotificationToAllConsumers(notification);
	}

	private void sendOfferNotificationToAllConsumers(OfferNotification notification) {
		RestTemplate rest = new RestTemplate();

		HttpEntity<OfferNotification> entity = new HttpEntity<OfferNotification>(notification,
				Application.getRestHeader());

		String url;

		for (Consumer c : getAllConsumers()) {
			url = new API().consumers(c.getUUID()).offers().toString();

			try {
				rest.exchange(url, HttpMethod.POST, entity, String.class);
			} catch (Exception e) {
				Log.e(this.uuid, e.getMessage());
			}
		}
	}

	/**
	 * Eine Angebotsbenachrichtigung trifft ein und wird zur asynchronen
	 * Weiterbehandlung abgelegt.
	 * 
	 * @param offerNotification
	 *            Benachrichtigung
	 */
	public void receiveOfferNotification(OfferNotification offerNotification) {
		Log.d(this.uuid, offerNotification.toString());
		notificationQueue.add(new Object[] { OfferAction.RECEIVE, offerNotification });
	}

	public void setDevice(UUID uuid) {
		device = uuid;
	}
}
