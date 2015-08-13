package Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.annotation.JsonView;

import Packet.OfferNotification;
import Packet.AnswerChangeRequest;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;
import Packet.ChangeRequestSchedule;
import Util.Score;
import Util.View;
import Util.API;
import Util.DateTime;
import Util.Log;

public class Consumer implements Identifiable {
	final class DeviationOfferComparator implements Comparator<Offer> {

		@Override
		public int compare(Offer o1, Offer o2) {
			double d = o1.getAggLoadprofile().chargeDeviationOtherProfile(o2.getAggLoadprofile());

			if (d < 0) {
				return -1;
			} else if (d > 0) {
				return 1;
			} else {
				return 0;
			}
		}
	}

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

	private HashMap<UUID, HashMap<UUID, ChangeRequestLoadprofile>> allOfferContributions = new HashMap<UUID, HashMap<UUID, ChangeRequestLoadprofile>>();

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
		Offer offer = getOfferFromUrl(offerNotification.getConsumer(), offerNotification.getOffer());
		// check offer at its location if valid, date and if it is better

		// confirm offer
		API<Void, Boolean> api2 = new API<Void, Boolean>(Boolean.class);
		api2.consumers(offer.getAuthor()).offers(offer.getUUID()).confirm(offer.getAuthKey());
		api2.call(this, HttpMethod.GET, null);

		Log.d(this.uuid, "contract immediate response: " + api2.getResponse());

		// check response
		if (api2.getResponse() == null || api2.getResponse() != true) {
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

			API<Void, Void> api2_replace = new API<Void, Void>(Void.class);
			api2_replace.consumers(consumerUUID).offers(respondedOffer.getUUID()).replace(offer.getUUID());
			api2_replace.call(this, HttpMethod.GET, null);
		}

		// Lastprofil muss beim Gerät bestätigt werden, Deltalastprofile nicht
		HashMap<UUID, HashMap<UUID, Loadprofile>> allLoadprofiles = respondedOffer.getAllLoadprofiles();
		if (allLoadprofiles == null) {
			return;
		}
		for (Loadprofile lp : allLoadprofiles.get(uuid).values()) {
			if (!lp.hasPrices()) {
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
	 *            Angebots-ID des zu ersetzende Angebots
	 * @param newOffer
	 *            neue Angebots-ID
	 */
	public void replaceOffer(UUID oldOffer, UUID newOffer) {
		if (getOfferIntern(oldOffer) == null)
			return;

		// das neue Angebot besorgen
		Offer offer = getOfferFromUrl(getOfferIntern(oldOffer).getAuthor(), newOffer);

		if (offer == null) {
			// TODO what? darf nicht sein!
			Log.e(uuid, "angebot konnte nicht ersetzt werden");
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
	 *            Angebotskey
	 * @return Angebot wurde angenommen ja/nein
	 */
	public boolean confirmOfferByConsumer(UUID uuidOffer, UUID authKey) {
		Log.d(uuid, "-- START confirmOfferByConsumer --");
		// Offer offer = allOffers.get(uuidOffer);
		Offer offer = getOfferIntern(uuidOffer);

		// Prüfen ob "AuthKey" übereinstimmt
		if (!offer.getAuthKey().equals(authKey)) {
			Log.d(uuid, "Consumer moechte mit ungueltigen authKey Angebot bestaetigen");
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

			API<Void, Void> api2 = new API<Void, Void>(Void.class);
			api2.consumers(consumerUUID).offers(vX.getUUID()).replace(offer.getUUID());
			api2.call(this, HttpMethod.GET, null);
		}

		OfferNotification notification = new OfferNotification(offer.getAuthor(), offer.getUUID());
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
	 * @param answerOffer
	 *            das bestätigte Angebot
	 */
	public void confirmOfferByMarketplace(AnswerToOfferFromMarketplace answerOffer) {
		// Offer offer = allOffers.get(confirmOffer.getUuid());
		Offer offer = getOfferIntern(answerOffer.getUuid());

		for (Loadprofile lp : offer.getAllLoadprofiles().get(uuid).values()) {
			if (lp.hasPrices()) {
				// Schicke Bestätigung zu Loadprofile an Device
				API<Void, Void> api2 = new API<Void, Void>(Void.class);
				api2.devices(device).confirmLoadprofile().toString();
				api2.call(this, HttpMethod.POST, null);
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
		API<Void, Consumer[]> api2 = new API<Void, Consumer[]>(Consumer[].class);
		api2.consumers();
		api2.call(this, HttpMethod.GET, null);

		Consumer[] list = new Consumer[api2.getResponse().length - 1];

		int i = 0;
		for (Consumer c : api2.getResponse()) {
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
		API<Void, Offer[]> api2 = new API<Void, Offer[]>(Offer[].class);
		api2.marketplace().supplies(i);
		api2.call(this, HttpMethod.GET, null);

		if (api2.getResponse() == null) {
			return new Offer[] {};
		}

		return api2.getResponse();
	}

	public int getNumSlots() {
		return numSlots;
	}

	private Offer getOfferFromUrl(UUID consumer, UUID offer) {
		API<Void, Offer> api2 = new API<Void, Offer>(Offer.class);
		api2.consumers(consumer).offers(offer);
		api2.call(this, HttpMethod.GET, null);

		return api2.getResponse();
	}

	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Sucht ein Angbeot aus dem gleichen Zeitslot bei dem der aktuelle Consumer
	 * auch Author ist und daher verhandeln darf.
	 * 
	 * @param date
	 *            entsprechender Zeitslot
	 * @return Angebot
	 */
	public Offer[] getOfferWithPrivileges(GregorianCalendar date) {
		ArrayList<Offer> list = new ArrayList<Offer>();

		for (Offer o : allOffers.values()) {
			if (o.isAuthor(uuid) && o.getDate().equals(date)) {
				list.add(o);
			}
		}

		return list.toArray(new Offer[list.size()]);
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

		if (object == null) {
			// Log.d(uuid, "notification queue empty");

			Offer[] offerList = getOfferWithPrivileges(DateTime.now());

			if (offerList.length == 0) {
				// Log.d(uuid, "no offerWithPrivileges for sending
				// notifications");
				return;
			} else {
				// Log.d(uuid, "send notification for current offer");

				int minimum = 0;
				int maximum = offerList.length - 1;
				int i = minimum + (int) (Math.random() * maximum);

				OfferNotification notification = new OfferNotification(offerList[i].getAuthor(),
						offerList[i].getUUID());
				sendOfferNotificationToAllConsumers(notification);
				return;
			}
		}

		OfferAction action = (OfferAction) object[0];
		OfferNotification notification = (OfferNotification) object[1];

		if (action.equals(OfferAction.SEND)) {
			Log.d(uuid, "send notification due to notificationQueue");
			sendOfferNotificationToAllConsumers(notification);
			return;
		}

		Offer receivedOffer = getOfferFromUrl(notification.getConsumer(), notification.getOffer());

		// Angebot nicht mehr vorhanden
		if (receivedOffer == null) {
			Log.d(uuid, "offer not anymore available");
			return;
		}

		// Angebot nicht mehr gültig
		if (!receivedOffer.isValid()) {
			Log.d(uuid, "invalid offer!");
			return;
		}

		// wenn kein eigenes Lastprofil mehr behandelt werden muss, nicht
		// reagieren auf Angebot
		Offer[] offerWithPrivileges = getOfferWithPrivileges(receivedOffer.getDate());

		if (offerWithPrivileges.length == 0) {
			Log.d(uuid, "not able to deal: " + this.allOffers);
			return;
		}

		TreeSet<Score> scorecard = new TreeSet<Score>();
		for (Offer marketplace : getMarketplaceSupplies(maxMarketplaceOffersToCompare)) {
			for (Offer own : offerWithPrivileges) {
				scorecard.add(new Score(marketplace, own, null, null));
			}
		}

		if (scorecard.isEmpty()) {
			API<Void, double[]> api2 = new API<Void, double[]>(double[].class);
			api2.marketplace().prediction();
			api2.call(this, HttpMethod.GET, null);

			double[] prediction = api2.getResponse();
			Loadprofile lp = new Loadprofile(prediction, DateTime.currentTimeSlot());
			Offer marketplace = new Offer(api2.getSenderUUID(), lp);

			for (Offer own : offerWithPrivileges) {
				scorecard.add(new Score(marketplace, own, null, null));
			}
		}

		Log.d(uuid, "Scorecard: " + scorecard);

		if (scorecard.isEmpty()) {
			Log.e(uuid, "getMarketplaceSupplies returns null?");
			return;
		}

		// auslesen des bisherigen besten angebots
		Score bestScore = scorecard.first();

		// besten score um erhaltenes angbeot erweitern
		Score s = bestScore.clone();
		s.setReceivedOffer(receivedOffer);
		scorecard.add(s);

		Offer newOffer = null;

		if (bestScore.equals(scorecard.first())) {
			Log.d(uuid, "versuche das eigene lastprofil anzupassen, um besser zu werden");
			HashMap<UUID, ChangeRequestLoadprofile> contributions = improveOwnOffer(bestScore.getOwn(), receivedOffer,
					bestScore);

			if (contributions == null) {
				Log.d(uuid, "nichts konnte erreicht werden");
				return;
			} else {
				newOffer = new Offer(new Offer(bestScore.getOwn(), receivedOffer), contributions);
				allOfferContributions.put(newOffer.getUUID(), contributions);
			}
		} else {
			// neues Angebot erstellen
			newOffer = bestScore.getMerge();
		}

		// neues Angebot ablegen
		addOffer(newOffer);
		allOfferMerges.put(newOffer.getUUID(), bestScore.getOwn());

		// neue notification erstellen
		OfferNotification newNotification = new OfferNotification(newOffer.getAuthor(), newOffer.getUUID());

		// notification versenden
		API<OfferNotification, Void> api2 = new API<OfferNotification, Void>(Void.class);
		api2.consumers(receivedOffer.getAuthor()).offers(receivedOffer.getUUID()).answer();
		api2.call(this, HttpMethod.POST, newNotification);
	}

	private HashMap<UUID, ChangeRequestLoadprofile> improveOwnOffer(Offer ownOffer, Offer receivedOffer,
			Score bestScore) {
		// der zusammenschluss ist primär nicht gut, da die abweichung
		// erhöht wird. versuche durch anpassung von lastprofilen eine
		// besserung zu erreichen
		Log.d(uuid, "mögliche verbesserung eruieren");

		// berechnen der abweichung
		HashMap<UUID, ChangeRequestLoadprofile> contributions = new HashMap<UUID, ChangeRequestLoadprofile>();
		ChangeRequestLoadprofile aim = new ChangeRequestLoadprofile(ownOffer.getUUID(), bestScore.getDelta());

		for (UUID c : ownOffer.getAllLoadprofiles().keySet()) {
			Log.d(c, "Ziel CR: " + aim.toString());
			if (aim.isZero()) {
				Log.d(uuid, "changerequest ziel zu 100% erreicht");
				break;
			}

			API<ChangeRequestLoadprofile, ChangeRequestLoadprofile> api2 = new API<ChangeRequestLoadprofile, ChangeRequestLoadprofile>(
					ChangeRequestLoadprofile.class);
			api2.consumers(c).offers(ownOffer.getUUID()).changeRequest();
			api2.call(this, HttpMethod.POST, aim);

			Log.d(uuid, "device ermöglicht folgenden changerequest: " + api2.getResponse());

			if (!api2.getResponse().isZero()) {
				contributions.put(c, api2.getResponse());
			}

			if (aim.equals(api2.getResponse())) {
				Log.d(uuid, "device ermöglicht genau das angefragte. gesamt cr konnte ermöglicht werden");
				break;
				// anfrage wurde bestätigt
				// gesamt cr konnte reserviert werden
			}

			// ziel um zugesagte änderung in der antwort verringern
			for (int i = 0; i < 4; i++) {
				aim.getChange()[i] -= api2.getResponse().getChange()[i];
			}
		}

		if (contributions.size() > 0) {
			Log.d(uuid, "eine verbesserung konnte erreicht werden");
		}

		// eigenes angebot um erhaltene CRs erweitern
		Offer contributionOffer = null;

		for (UUID c : contributions.keySet()) {
			if (contributionOffer == null) {
				contributionOffer = contributions.get(c).toLoadprofile(ownOffer.getDate()).toOffer(c);
			} else {
				contributionOffer = new Offer(contributionOffer,
						contributions.get(c).toLoadprofile(ownOffer.getDate()).toOffer(c));
			}
		}

		// neue abweichung des erweiterten angebots gegenüber dem
		// marktplatzangebot berechnen
		// TODO logik prüfen!
		Score newScore = new Score(bestScore.getMarketplace(), bestScore.getOwn(), receivedOffer, contributionOffer);
		if (newScore.getScore() >= bestScore.getScore()) {
			Log.d(uuid, "abweichung konnte nicht verringert werden, angebot nicht gut und wird verworfen");

			// change requests aufheben
			API<Void, Void> api2_decline = new API<Void, Void>(Void.class);
			for (UUID c : contributions.keySet()) {
				// TODO pfad muss noch geklärt werden!
				api2_decline.consumers(c).offers(ownOffer.getUUID()).changeRequest().decline();
				api2_decline.call(this, HttpMethod.GET, null);
				api2_decline.clear();

			}

			return null;
		}

		return contributions;
	}

	public void priceChangeRequest(AnswerToOfferFromMarketplace answerOffer, UUID negotiation) {
		// TODO Behandle Anfrage nach Preisänderung von Negotiation

		// TODO Sende Antwort an Negotiation

	}

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.d(this.uuid, uuid + "received deltaloadprofile [" + deltaLoadprofile.toString() + "]");
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
				sum = sum + Math.abs(valuesNew[i]);
			}
		}

		// Versende Deltalastprofile mit Summe>5 oder aus der aktuellen Stunde
		// sofort
		if (currentHour || sum >= 5) {
			deltaLoadprofile = new Loadprofile(valuesNew, timeLoadprofile);

			// Erstelle Angebot aus deltaLoadprofile, speichere es in
			// deltaOffers und verschicke es
			Offer deltaOffer = new Offer(uuid, deltaLoadprofile);
			allOffers.put(deltaOffer.getUUID(), deltaOffer);

			OfferNotification notification = new OfferNotification(uuid, deltaOffer.getUUID());

			API<OfferNotification, Void> api2 = new API<OfferNotification, Void>(Void.class);

			for (Consumer c : getAllConsumers()) {
				api2.consumers(c.getUUID()).offers();
				api2.call(this, HttpMethod.POST, notification);
				api2.clear();
			}
		}
		// Sammle Deltalastprofile mit Summe<5 für die nächsten Stunden
		else {
			deltaLoadprofiles.put(DateTime.ToString(timeLoadprofile), valuesNew);
		}
	}

	public ChangeRequestLoadprofile receiveChangeRequestLoadprofile(ChangeRequestLoadprofile cr) {
		Offer affectedOffer = allOffers.get(cr.getOffer());

		if (affectedOffer == null) {
			Log.d(uuid, "angebot nicht vorhanden");
			return new ChangeRequestLoadprofile(cr.getOffer(), new double[] { 0.0, 0.0, 0.0, 0.0 });
		}

		// Frage eigenes Device nach Änderung
		// ( und passe noch benötigte Änderung an )
		API<ChangeRequestSchedule, AnswerChangeRequest> api2 = new API<ChangeRequestSchedule, AnswerChangeRequest>(
				AnswerChangeRequest.class);
		api2.devices(device);
		api2.call(this, HttpMethod.DELETE, new ChangeRequestSchedule(DateTime.currentTimeSlot(), cr.getChange()));

		AnswerChangeRequest answer = api2.getResponse();

		Log.d(uuid, "request [" + Arrays.toString(cr.getChange()) + "], response ["
				+ Arrays.toString(answer.getPossibleChanges()) + "]");

		return new ChangeRequestLoadprofile(cr.getOffer(), answer.getPossibleChanges());
		// }
		//
		// ChangeRequestLoadprofile possibleChange = new
		// ChangeRequestLoadprofile(cr.getOffer(), remainingChange);

		// TODO Autor für übergebenes Angebot?
		// Wenn ja: Frage alle anderen beteiligten Consumer der Reihe nach nach
		// Änderung für deren Lastprofil, passe Angebot jeweils gleich an und
		// benachrichtige Marketplace am Ende
		// Wenn nein: Versende Antwort als cr an Autor
		// if (!affectedOffer.isAuthor(uuid)) {
		// // kann nichts weiteres tun, muss absagen
		// return new ChangeRequestLoadprofile(cr.getOffer(), new double[] {
		// 0.0, 0.0, 0.0, 0.0 });
		// }
		//
		// Frage alle beteiligten Consumer nach Änderung für deren
		// Lastprofil
		// for (UUID consumer : affectedOffer.getAllLoadprofiles().keySet()) {
		// API2<ChangeRequestLoadprofile, Boolean> api = new
		// API2<ChangeRequestLoadprofile, Boolean>(Boolean.class);
		// api.consumers(consumer);
		// TODO Sende cr an consumer und passe cr nach Antwort an
		// }

		// Antworte Marketplace mit vorgenommener Änderung
		// API2<ChangeRequestLoadprofile, Void> api = new
		// API2<ChangeRequestLoadprofile, Void>(Void.class);
		// api.marketplace().offers(cr.getOffer()).receiveAnswerChangeRequestLoadprofile();
		// api.call(this, HttpMethod.POST, possibleChange);
		//
		// return new ChangeRequestLoadprofile(cr.getOffer(), new double[] {
		// 0.0, 0.0, 0.0, 0.0 });
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
		if (loadprofile.hasPrices()) {
			receiveDeltaLoadprofile(loadprofile);
			return;
		}

		Log.d(this.uuid, loadprofile.toString());

		Offer offer = new Offer(uuid, loadprofile);
		addOffer(offer);

		OfferNotification notification = new OfferNotification(offer.getAuthor(), offer.getUUID());

		sendOfferToMarketplace(offer);
		sendOfferNotificationToAllConsumers(notification);
	}

	private void sendOfferToMarketplace(Offer offer) {
		API<Offer, Void> api2 = new API<>(Void.class);
		api2.marketplace().demand();
		api2.call(this, HttpMethod.POST, offer);
	}

	private void sendOfferNotificationToAllConsumers(OfferNotification notification) {
		API<OfferNotification, Void> api2 = new API<>(Void.class);

		for (Consumer c : getAllConsumers()) {
			api2.consumers(c.getUUID()).offers().toString();
			api2.call(this, HttpMethod.POST, notification);
			api2.clear();
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

	public void receiveChangeRequestDecline(UUID uuidOffer, UUID author) {
		if (!allOffers.containsKey(uuidOffer) || !allOffers.get(uuidOffer).isAuthor(author)) {
			Log.e(uuid, "receiveChangeRequestDecline abbrechen");
			return;
		}

		API<Void, Void> api2 = new API<Void, Void>(Void.class);
		api2.devices(device).changeRequest().decline();
		api2.call(this, HttpMethod.GET, null);
	}
}
