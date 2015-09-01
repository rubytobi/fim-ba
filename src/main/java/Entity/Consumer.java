package Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import Packet.OfferNotification;
import Packet.SearchParams;
import Packet.AnswerChangeRequestLoadprofile;
import Packet.AnswerChangeRequestSchedule;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;
import Packet.ChangeRequestSchedule;
import Packet.EndOfNegotiation;
import Packet.AnswerToPriceChangeRequest;
import Util.Score;
import Util.Scorecard;
import Util.View;
import Util.API;
import Util.DateTime;
import Util.Log;
import Util.Negotiation;
import Event.OffersPriceborderException;
import Util.ResponseBuilder;

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
	 * Queue an Benachrichtigungen über Angebote in Reinfolge der Ankunft
	 */
	private ConcurrentLinkedQueue<OfferNotification> notificationQueue = new ConcurrentLinkedQueue<OfferNotification>();

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

	private HashMap<UUID, HashMap<UUID, AnswerChangeRequestLoadprofile>> allOfferContributions = new HashMap<UUID, HashMap<UUID, AnswerChangeRequestLoadprofile>>();

	/**
	 * Anlage eines neuen Consumers. ID und maxMarketplaceOffersToCompare werden
	 * standardmäßig gesetzt
	 */
	public Consumer() {
		uuid = UUID.randomUUID();
	}

	private void addOffer(Offer offer) {
		Log.d(uuid, "Füge Angebot [" + offer.getUUID() + "] hinzu.");
		this.allOffers.put(offer.getUUID(), offer);
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
		Log.d(uuid,
				"Auf das Angebot [" + respondedOfferUUID + "] wurde eingegangen; folgendes Angebot erhalten: " + offer);

		// TODO check offer at its location if valid, date and if it is better

		Log.d(uuid, "Bestätige nun das Angebot [" + offer.getUUID() + "]");
		API<Void, Boolean> api2 = new API<Void, Boolean>(Boolean.class);
		api2.consumers(offer.getAuthor()).offers(offer.getUUID()).confirm(offer.getAuthKey());
		api2.call(this, HttpMethod.GET, null);

		// check response
		if (api2.getResponse() == null || api2.getResponse() != true) {
			Log.d(uuid, "Angebotsbestätigung wurde abgelehnt.");
			return;
		} else {
			Log.d(uuid, "Angbeot wurde angenommen.");
		}

		Log.d(uuid, "Invalidiere altes Angebot am Marktplatz");
		API<Void, Void> api = new API<Void, Void>(Void.class);
		api.marketplace().offers(respondedOfferUUID).invalidate();
		api.call(this, HttpMethod.GET, null);

		// Das neue Angebot wird hinterlegt.
		addOffer(offer);

		// Angebot das initial verschickt wurde muss entfernt werden.
		Offer respondedOffer = getOfferIntern(respondedOfferUUID);
		removeOffer(respondedOfferUUID);

		// Consumer des alten Angebots müssen mit dem neuen Angebot versorgt
		// werden
		distributeNewOffer(respondedOffer, offer);
	}

	private AnswerChangeRequestLoadprofile askConsumerForChange(UUID uuidConsumer, UUID uuidOffer,
			ChangeRequestLoadprofile changeRequestLoadprofile) {
		API<ChangeRequestLoadprofile, AnswerChangeRequestLoadprofile> api2 = new API<ChangeRequestLoadprofile, AnswerChangeRequestLoadprofile>(
				AnswerChangeRequestLoadprofile.class);
		api2.consumers(uuidConsumer).offers(uuidOffer).changeRequest();
		api2.call(this, HttpMethod.POST, changeRequestLoadprofile);
		return api2.getResponse();
	}

	private AnswerChangeRequestSchedule askDeviceForChange(ChangeRequestSchedule changeRequestSchedule) {
		API<ChangeRequestSchedule, AnswerChangeRequestSchedule> api2 = new API<ChangeRequestSchedule, AnswerChangeRequestSchedule>(
				AnswerChangeRequestSchedule.class);
		api2.devices(device);
		api2.call(this, HttpMethod.DELETE, changeRequestSchedule);
		return api2.getResponse();
	}

	public void cancelOffer(UUID uuidOffer) {
		Log.d(this.uuid, "Absage zu Angebot [" + uuidOffer + "] erhalten.");
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
				// System.out.println("Verbrauch " + j + ". 15 Minuten: " +
				// summeMin);
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	private void confirmDeltaLoadprofiles(Offer respondedOffer) {
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
	public ResponseEntity<Boolean> confirmOfferByConsumer(UUID uuidOffer, UUID authKey) {
		Log.d(uuid, "-- START confirmOfferByConsumer --");
		Offer offer = getOfferIntern(uuidOffer);
	
		if (!offer.getAuthKey().equals(authKey)) {
			Log.d(uuid, "Consumer möchte mit ungültigem Authkey das Angebot bestätigen");
			return new ResponseBuilder<Boolean>(this).body(false).build();
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
		return new ResponseBuilder<Boolean>(this).body(true).build();
	}

	/**
	 * Der Marktplatz bestätigt ein Angebot
	 * 
	 * @param answerOffer
	 *            das bestätigte Angebot
	 */
	public void confirmOfferByMarketplace(AnswerToOfferFromMarketplace answerOffer) {
		// TODO bestätige ChangeRequests die durch den Marktplatz vorher
		// angefragt wurden, möglicherweise müssen diese irgendwo
		// zwischengelagert werden
		// Offer offer = allOffers.get(confirmOffer.getUuid());
		Offer offer = getOfferIntern(answerOffer.getOffer());
	
		if (offer == null) {
			Log.e(uuid, "Marktplatz möchte ein Angebot bestätigen, welches nicht vorhanden ist!");
			return;
		}
	
		for (Loadprofile lp : offer.getAllLoadprofiles().get(uuid).values()) {
			if (lp.isDelta()) {
				// Schicke Bestätigung zu Loadprofile an Device
				API<Void, Void> api2 = new API<Void, Void>(Void.class);
				api2.devices(device).confirmLoadprofile().toString();
				api2.call(this, HttpMethod.POST, null);
			}
	
			// TODO Speichere Lastprofil in Historie ab
		}
	
		removeOffer(offer.getUUID());
	}

	private void distributeNewOffer(Offer toBeReplaced, Offer newOffer) {
		for (UUID consumerUUID : toBeReplaced.getAllLoadprofiles().keySet()) {
			if (consumerUUID.equals(uuid)) {
				// sich selber überspringen und den author des letzten vertrags
				// (eigene anpassung durch confirm)
				continue;
			}
	
			API<Void, Void> api2_replace = new API<Void, Void>(Void.class);
			api2_replace.consumers(consumerUUID).offers(toBeReplaced.getUUID()).replace(newOffer.getUUID());
			api2_replace.call(this, HttpMethod.GET, null);
		}
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

	@JsonIgnore
	public ResponseEntity<Offer[]> getAllOffers() {
		return new ResponseBuilder<Offer[]>(this).body(allOffers.values().toArray(new Offer[allOffers.size()])).build();
	}

	private Offer getMarketplacePrediction() {
		API<Void, double[]> api2 = new API<Void, double[]>(double[].class);
		api2.marketplace().prediction();
		api2.call(this, HttpMethod.GET, null);
	
		double[] prediction = api2.getResponse();
		Loadprofile lp = new Loadprofile(prediction, DateTime.currentTimeSlot(), Loadprofile.Type.MIXED);
		Offer marketplace = new Offer(api2.getSenderUUID(), lp);
		return marketplace;
	}

	public int getNumSlots() {
		return numSlots;
	}

	public ResponseEntity<Offer> getOffer(UUID offer) {
		return new ResponseBuilder<Offer>(this).body(getOfferIntern(offer)).build();
	}

	private Offer getOfferFromUrl(UUID consumer, UUID offer) {
		API<Void, Offer> api2 = new API<Void, Offer>(Offer.class);
		api2.consumers(consumer).offers(offer);
		api2.call(this, HttpMethod.GET, null);
	
		return api2.getResponse();
	}

	private Offer getOfferIntern(UUID offer) {
		// Log.d(uuid, "get offer" + offer);
		return this.allOffers.get(offer);
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

	public UUID getUUID() {
		return uuid;
	}

	private HashMap<UUID, AnswerChangeRequestLoadprofile> improveOwnOffer(Offer ownOffer, Offer receivedOffer,
			Score bestScore) {
		// der zusammenschluss ist primär nicht gut, da die abweichung
		// erhöht wird. versuche durch anpassung von lastprofilen eine
		// besserung zu erreichen
		Log.d(uuid, "Das eigene Angebot muss verbessert werden.");
	
		// Berechnen der Abweichung
		HashMap<UUID, AnswerChangeRequestLoadprofile> contributions = new HashMap<UUID, AnswerChangeRequestLoadprofile>();
		ChangeRequestLoadprofile aim = new ChangeRequestLoadprofile(ownOffer.getUUID(), bestScore.getDelta());
	
		Log.d(uuid, "Verbesserungsziel: " + Arrays.toString(aim.getChange()));
	
		if (aim.isZero()) {
			Log.d(uuid, "Verbesserungsziel ist [0.0, 0.0, 0.0, 0.0]?");
			return null;
		}
	
		for (UUID c : ownOffer.getAllLoadprofiles().keySet()) {
			if (aim.isZero()) {
				Log.d(uuid, "Verbesserungsziel zu 100% erreicht.");
				break;
			}
	
			AnswerChangeRequestLoadprofile answer = askConsumerForChange(c, ownOffer.getUUID(), aim);
	
			Log.d(uuid, "Consumer [" + c + "] ermöglicht folgende Veränderung: " + answer);
			contributions.put(c, answer);
	
			aim.sub(answer);
		}
	
		// Eigenes Angebot um erhaltene Veränderungen erweitern
		Offer contributionOffer = null;
	
		for (UUID c : contributions.keySet()) {
			if (contributionOffer == null) {
				contributionOffer = contributions.get(c).getLoadprofile().toOffer(c);
			} else {
				try {
					contributionOffer = new Offer(contributionOffer, contributions.get(c).getLoadprofile().toOffer(c));
				} catch (OffersPriceborderException e) {
					Log.e(uuid, "Aufgrund der Preisgrenzen, konnte die Änderung nicht zusammengeführt werden.");
					Log.e(uuid, e.getMessage());
				}
			}
		}
	
		// neue abweichung des erweiterten angebots gegenüber dem
		// marktplatzangebot berechnen
		// TODO logik prüfen!
		Offer newMerge = null;
		Score newScore = null;
	
		if (contributionOffer != null) {
			try {
				newMerge = new Offer(bestScore.getMerge(), contributionOffer);
				newScore = new Score(newMerge, bestScore.getMarketplace(), bestScore.getOwn(), receivedOffer,
						contributionOffer);
			} catch (OffersPriceborderException e) {
				Log.d(uuid, "Angebote konnten nicht verknüpft werden.");
			}
		}
	
		if (newScore == null) {
			Log.d(uuid, "Angebot konnte nicht verbessert werden. Vereinbarte Veränderungen werden verworfen.");
	
			for (UUID c : contributions.keySet()) {
				API<Void, Void> api2_decline = new API<Void, Void>(Void.class);
				api2_decline.consumers(c).offers(ownOffer.getUUID()).changeRequest().decline();
				api2_decline.call(this, HttpMethod.GET, null);
				api2_decline.clear();
			}
	
			return null;
		}
		return contributions;
	}

	/**
	 * Arbeitet im ping-Rhythmus eingetroffene Benachrichtigungen über Angebote
	 * ab. Die Benachrichtigung enthält die Adresse des Angebots. Es wird
	 * geprüft, ob das Angebot gültig ist, den richtigen Zeitslot beinhaltet und
	 * ob es besser ist. Wenn ja, wird ein neues Angebot erstellt, dieses
	 * abgelegt und zurück gesandt an consumers/uuid/answer.
	 */
	public void ping() {
		OfferNotification notification = notificationQueue.poll();
	
		if (notification == null) {
			Log.d(uuid, "Keine Benachrichtigungen in der Warteschlange.");
	
			Offer[] offerList = getOfferWithPrivileges(DateTime.currentTimeSlot());
	
			if (offerList.length == 0) {
				Log.d(uuid, "Kein Angebot als Autor für aktuellen Zeitslot.");
				return;
			} else {
				Log.d(uuid, "Verschicke Benachrichtigung als Autor für ein zufälliges Angebot.");
	
				int minimum = 0;
				int maximum = offerList.length - 1;
				int i = minimum + (int) (Math.random() * maximum);
	
				sendOfferNotificationToAllConsumers(OfferNotification.parseOffer(offerList[i]));
				return;
			}
		}
	
		Offer receivedOffer = getOfferFromUrl(notification.getConsumer(), notification.getOffer());
	
		if (receivedOffer == null) {
			Log.d(uuid, "Angebot ist nicht mehr vorhanden.");
			return;
		}
	
		if (!receivedOffer.isValid()) {
			Log.d(uuid, "Ungültiges Angebot bekommen.");
			return;
		}
	
		Offer[] offerWithPrivileges = getOfferWithPrivileges(receivedOffer.getDate());
	
		if (offerWithPrivileges.length == 0) {
			Log.d(uuid, "Consumer ist nicht Autor in einem seiner Angebote und kann daher nicht verhandeln: "
					+ this.allOffers);
			return;
		}
	
		Scorecard scorecard = new Scorecard(this, receivedOffer, offerWithPrivileges);
	
		if (scorecard.isEmpty()) {
			Log.d(uuid, "Keine Angebote am Marktplatz vorrätig. Nutze die Vorhersage zur Optimierung.");
			Offer marketplace = getMarketplacePrediction();
			for (Offer own : offerWithPrivileges) {
				scorecard.add(new Score(own, marketplace, own, null, null));
			}
		}
	
		Log.d(uuid, scorecard.toString());
	
		if (scorecard.isEmpty()) {
			Log.e(uuid, "Marktplatz gibt keine Vorhersage zurück?");
			return;
		}
	
		Offer newOffer = null;
	
		if (scorecard.first().hasReceivedOffer()) {
			Log.d(uuid, "Bestmögliches Angebot enthält bereits das externe Angebot.");
			newOffer = scorecard.first().getMerge();
		} else {
			Log.d(uuid, "Versuche das eigene Angebot anzupassen um besser zu werden.");
			HashMap<UUID, AnswerChangeRequestLoadprofile> contributions = improveOwnOffer(scorecard.first().getOwn(),
					receivedOffer, scorecard.first());
	
			if (contributions == null) {
				Log.d(uuid, "Es konnte keine Verbesserung erreicht werden.");
				return;
			} else {
				newOffer = new Offer(scorecard.first().getMerge(), contributions);
				allOfferContributions.put(newOffer.getUUID(), contributions);
			}
		}
	
		newOffer.generateAuthKey();
	
		Log.d(uuid, "Neues Angebot	 erreicht: " + newOffer);
		addOffer(newOffer);
		allOfferMerges.put(newOffer.getUUID(), scorecard.first().getOwn());
	
		Log.d(uuid, "Antworte auf das eingetroffene Angebot.");
		OfferNotification newNotification = new OfferNotification(newOffer.getAuthor(), newOffer.getUUID());
	
		API<OfferNotification, Void> api2 = new API<OfferNotification, Void>(Void.class);
		api2.consumers(receivedOffer.getAuthor()).offers(receivedOffer.getUUID()).answer();
		api2.call(this, HttpMethod.POST, newNotification);
	}

	/**
	 * Behandelt die Preisänderungsanfrage von einer Verhandlung. Liegt der
	 * Preis in den festgelegten Grenzen des Angebotes, wird er bestätigt,
	 * andernfalls wird das jeweilige Minimum/ Maximum bestätigt
	 * 
	 * @param answerOffer
	 *            Die Preisanfrage vom Marktplatz, beinhaltet den Prei und die
	 *            UUID des betreffenden Angebots
	 * @param negotiation
	 *            Die UUID der aktuellen Verhandlung, von welcher die
	 *            Preisanfrage stammt.
	 */
	public void priceChangeRequest(AnswerToOfferFromMarketplace answerOffer, UUID negotiation) {
		double newPrice = answerOffer.getPrice();
	
		// Prüfe, dass der neue Preis innerhalb der Preisgrenzen des Angebotes
		// liegt
		// Liegt er dazwischen, bestätige ihn.
		// Liegt er nicht dazwischen, bestätige das Minimum bzw. das Maximum
		Offer offer = allOffers.get(answerOffer.getOffer());
		double min = offer.getMinPrice();
		double max = offer.getMaxPrice();
	
		if (newPrice < min) {
			newPrice = min;
		}
		if (newPrice > max) {
			newPrice = max;
		}
	
		// Sende Antwort an Negotiation
		AnswerToPriceChangeRequest answer = new AnswerToPriceChangeRequest(uuid, newPrice);
		Marketplace marketplace = Marketplace.instance();
		Negotiation negotiationWhole = marketplace.getNegotiationsMap().get(negotiation);
	
		API<AnswerToPriceChangeRequest, Void> api = new API<AnswerToPriceChangeRequest, Void>(Void.class);
		api.negotiation().answerToPriceChangeRequest(negotiation);
		api.call(negotiationWhole, HttpMethod.POST, answer);
	
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

	public ResponseEntity<AnswerChangeRequestLoadprofile> receiveChangeRequestLoadprofile(ChangeRequestLoadprofile cr) {
		Offer affectedOffer = allOffers.get(cr.getOffer());
	
		if (affectedOffer == null) {
			Log.d(uuid, "Das betroffene Angebot ist nicht vorhanden. Änderungen daher nicht möglich.");
			return null;
		}
	
		// Frage eigenes Device nach Änderung
		// ( und passe noch benötigte Änderung an )
		AnswerChangeRequestSchedule answer = askDeviceForChange(
				new ChangeRequestSchedule(DateTime.currentTimeSlot(), cr.getChange()));
	
		Log.d(uuid, "Angefragte Änderung: [" + Arrays.toString(cr.getChange()) + "]");
		Log.d(uuid, "Erhaltene Änderung: [" + Arrays.toString(answer.getChanges()) + "]");
	
		// TODO Berechnung neue Grenzen
		// TODO ANswerChangeRequestLoadprofile ändern
	
		// Suche das initiale Lastprofil, das zu dieser Änderung gehört
		Set<UUID> loadprofiles = affectedOffer.getAllLoadprofiles().get(this.getUUID()).keySet();
		Loadprofile initialLoadprofile = null;
		for (UUID uuidLoadprofile : loadprofiles) {
			Loadprofile currentLoadprofile = affectedOffer.getAllLoadprofiles().get(this.getUUID())
					.get(uuidLoadprofile);
			if (currentLoadprofile.getType() == Loadprofile.Type.INITIAL) {
				initialLoadprofile = currentLoadprofile;
			}
		}
	
		if (initialLoadprofile == null) {
			Log.e(uuid, "Kein initiales Lastprofil zu der übergebenen Änderung vorhanden.");
			return null;
		}
	
		// Berechne die Summe des alten Lastprofiles
		double sumOldLoadprofile = 0;
		for (int i = 0; i < numSlots; i++) {
			sumOldLoadprofile += initialLoadprofile.getValues()[i];
		}
	
		// Berechne den Preis der Änderung und die daraus resultierenden neuen
		// Minima und Maxima
		double priceChange = answer.getPriceFactor() * affectedOffer.getPriceSugg();
		priceChange = priceChange + answer.getSumPenalty();
	
		double newMax, newMin;
		if (sumOldLoadprofile < 0) {
			newMax = initialLoadprofile.getMaxPrice() - priceChange;
			newMin = initialLoadprofile.getMinPrice();
			if (newMax < newMin) {
				// TODO Was soll hier passieren??
				// Dann ändern sich Grenzen nicht
				newMax = initialLoadprofile.getMaxPrice();
				Log.d(uuid, "Preis für Änderung war nicht möglich");
			}
		} else {
			newMin = initialLoadprofile.getMinPrice() + priceChange;
			newMax = initialLoadprofile.getMaxPrice();
			if (newMin < newMax) {
				// TODO Was soll hier passieren ??
				// Dann ändern sich Grenzen nicht
				newMin = initialLoadprofile.getMinPrice();
				Log.d(uuid, "Preis für Änderung war nicht möglich");
			}
		}
	
		// Lege neuen Preisvorschlag fest
		double newPriceSugg = initialLoadprofile.getPriceSugg();
		if (newPriceSugg < newMin) {
			newPriceSugg = newMin;
		}
		if (newPriceSugg > newMax) {
			newPriceSugg = newMax;
		}
	
		Loadprofile changedLoadprofile = new Loadprofile(answer.getChanges(), initialLoadprofile.getDate(),
				newPriceSugg, newMin, newMax, Loadprofile.Type.CHANGE_REQUEST);
		return new ResponseBuilder<AnswerChangeRequestLoadprofile>(this)
				.body(new AnswerChangeRequestLoadprofile(cr.getOffer(), changedLoadprofile)).build();
	}

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.d(this.uuid, "Deltalastprofil erhalten: " + deltaLoadprofile);
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
			deltaLoadprofile = new Loadprofile(valuesNew, timeLoadprofile, Loadprofile.Type.DELTA);
	
			// Erstelle Angebot aus deltaLoadprofile, speichere es in
			// deltaOffers und verschicke es
			Offer deltaOffer = new Offer(uuid, deltaLoadprofile);
			allOffers.put(deltaOffer.getUUID(), deltaOffer);
	
			OfferNotification notification = new OfferNotification(uuid, deltaOffer.getUUID());
			sendOfferToMarketplace(deltaOffer);
			sendOfferNotificationToAllConsumers(notification);
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

	/**
	 * Eine Angebotsbenachrichtigung trifft ein und wird zur asynchronen
	 * Weiterbehandlung abgelegt.
	 * 
	 * @param offerNotification
	 *            Benachrichtigung
	 */
	public void receiveOfferNotification(OfferNotification offerNotification) {
		Log.d(this.uuid, offerNotification.toString());
		notificationQueue.add(offerNotification);
	}

	private void removeOffer(UUID offer) {
		Log.d(uuid, "Entferne Angebot [" + offer + "]");
		this.allOffers.remove(offer);
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
			Log.e(uuid, "Angebot konnte nicht ersetzt werden da extern nicht vorhanden?!");
			return;
		}

		// altes Angebot entfernen
		removeOffer(oldOffer);

		// neues Angebot einfügen
		addOffer(offer);
	}

	public Offer[] searchMarketplace(SearchParams params) {
		API<SearchParams, Offer[]> api2 = new API<SearchParams, Offer[]>(Offer[].class);
		api2.marketplace().search();
		api2.call(this, HttpMethod.POST, params);

		if (api2.getResponse() == null) {
			return new Offer[] {};
		}

		return api2.getResponse();
	}

	private void sendOfferToMarketplace(Offer offer) {
		API<Offer, Void> api2 = new API<>(Void.class);
		api2.marketplace().offers();
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

	public void setDevice(UUID uuid) {
		device = uuid;
	}
}
