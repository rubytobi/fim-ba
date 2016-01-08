package Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.sun.research.ws.wadl.Application;

import Container.NegotiationContainer;
import Packet.OfferNotification;
import Packet.SearchParams;
import Packet.AnswerChangeRequestLoadprofile;
import Packet.AnswerChangeRequestSchedule;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;
import Packet.ChangeRequestSchedule;
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
	 * Die aktuelle Antwort eines anderen Angebots auf eine Lastprofiländerung
	 */
	@JsonView(View.Detail.class)
	private AnswerChangeRequestLoadprofile currentAnswerChangeLoadprofile;

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
			Log.d(uuid, "Angebot wurde angenommen.");
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
		api2.consumers(uuidConsumer).offers(uuidOffer).changeRequestConsumer();
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
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
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

		if (offer.getAuthKey().equals(authKey)) {
			Log.d(uuid, "Angebot wurde bestätigt.");
		} else {
			Log.d(uuid, "Consumer möchte mit ungültigem Authkey das Angebot bestätigen");
			return new ResponseBuilder<Boolean>(this).body(false).build();
		}

		if (allOfferContributions.containsKey(uuidOffer)) {
			for (UUID u : allOfferContributions.get(uuidOffer).keySet()) {
				Log.d(uuid, "Bestätige dem Consumer [" + u + "] den nötig gewordenen ChangeRequest.");
				API<Void, Void> api = new API<Void, Void>(Void.class);
				api.consumers(u).offers(uuidOffer).changeRequest().confirm();
				api.call(this, HttpMethod.GET, null);
			}
		} else {
			Log.d(uuid, "Keine ChangeRequests zu bestätigen.");
		}

		// Alte Angebot suchen, mit dem das hier akzeptierte Angebot erweitert
		// wurde
		Offer mergedOffer = allOfferMerges.remove(offer.getUUID());
		removeOffer(mergedOffer.getUUID());

		// Consumer des alten Angebots müssen mit dem neuen Angebot versorgt
		// werden
		for (UUID consumerUUID : mergedOffer.getAllLoadprofiles().keySet()) {
			if (consumerUUID.equals(uuid)) {
				// sich selber überspringen
				continue;
			}

			API<Void, Void> api2 = new API<Void, Void>(Void.class);
			api2.consumers(consumerUUID).offers(mergedOffer.getUUID()).replace(offer.getUUID());
			api2.call(this, HttpMethod.GET, null);
		}

		invalidateOfferAtMarketplace(mergedOffer);
		Log.d(uuid, "Sende neues Angebot [" + offer.getUUID() + "] an den Marktplatz.");
		sendOfferToMarketplace(offer);

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
		Log.d(uuid, "Der Consumer erhält die Bestätigung des Angebots: " + answerOffer.getPrice());

		// Hole das Angebot, für das die Bestätigung gilt
		Offer offer = getOfferIntern(answerOffer.getOffer());

		// Prüfe, ob das Angebot vorhanden ist
		if (offer == null) {
			Log.e(uuid, "Marktplatz möchte ein Angebot bestätigen, welches nicht vorhanden ist");
			return;
		}

		Map<UUID, Loadprofile> loadprofiles = offer.getAllLoadprofiles().get(uuid);
		Set<UUID> setLP = loadprofiles.keySet();
		for (UUID uuidLP : setLP) {
			Loadprofile lp = loadprofiles.get(uuidLP);
			if (!lp.isDelta()) {
				// Schicke Bestätigung an Device
				API<String, Void> api2 = new API<String, Void>(Void.class);
				api2.devices(device).confirm(offer.getDate());
				api2.call(this, HttpMethod.POST, offer.getDate());
				break;
			}
		}

		// Prüfe, ob Autor von Angebot
		if (uuid == offer.getAuthor()) {
			Set<UUID> set = offer.getAllLoadprofiles().keySet();
			if (set != null) {
				for (UUID current : set) {
					// An sich selbst wird keine Bestätigung gesendet
					if (current == uuid) {
						continue;
					}
					// Alle anderen Teilnehmer werden informiert
					API<AnswerToOfferFromMarketplace, Void> api1 = new API<AnswerToOfferFromMarketplace, Void>(
							Void.class);
					api1.consumers(current).offers(offer.getUUID()).confirmByMarketplace();
					api1.call(this, HttpMethod.POST, answerOffer);
				}
			}
		} else {
			return;
		}

		removeOffer(offer.getUUID());
	}

	private void distributeNewOffer(Offer toBeReplaced, Offer newOffer) {
		if (toBeReplaced == null || toBeReplaced.getAllLoadprofiles() == null) {
			Log.e(uuid, "Der kann nicht ersetzt werden!?");
		}

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

	private Loadprofile getInitialLoadprofile() {
		for (UUID o : allOffers.keySet()) {
			for (UUID c : allOffers.get(o).getAllLoadprofiles().keySet()) {

				for (UUID l : allOffers.get(o).getAllLoadprofiles().get(c).keySet()) {
					if (allOffers.get(o).getAllLoadprofiles().get(c).get(l).getType()
							.equals(Loadprofile.Type.INITIAL)) {
						return allOffers.get(o).getAllLoadprofiles().get(c).get(l);
					}
				}
			}
		}

		return null;
	}

	private Offer getMarketplacePrediction() {
		API<Void, double[]> api2 = new API<Void, double[]>(double[].class);
		api2.marketplace().prediction();
		api2.call(this, HttpMethod.GET, null);

		double[] prediction = api2.getResponse();
		Loadprofile lp = new Loadprofile(prediction, DateTime.ToString(DateTime.currentTimeSlot()),
				Loadprofile.Type.MIXED);
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
	public Offer[] getOfferWithPrivileges(String date) {
		ArrayList<Offer> list = new ArrayList<Offer>();

		for (Offer o : allOffers.values()) {
			String a = o.getDate();
			String b = date;

			if (o.isAuthor(uuid) && a.equals(b)) {
				list.add(o);
			} else {
				Log.d(uuid, "Author mismatch (" + o.getAuthor() + ") or date mismatch (" + a + " " + b + ")");
				continue;
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

		if (ownOffer.getDate().compareTo(DateTime.ToString(DateTime.nextTimeSlot())) == -1) {
			Log.d(uuid, "Kann Angbeote der aktuellen Stunde nicht verbessern.");
			return null;
		}

		// Berechnen der Abweichung
		HashMap<UUID, AnswerChangeRequestLoadprofile> contributions = new HashMap<UUID, AnswerChangeRequestLoadprofile>();
		ChangeRequestLoadprofile aim = new ChangeRequestLoadprofile(ownOffer.getUUID(), bestScore.getDelta(),
				ownOffer.getDate());

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
					System.out.println("Consumer 479: ImproveOwnOffer");
					contributionOffer = new Offer(contributionOffer, contributions.get(c).getLoadprofile().toOffer(c));
				} catch (OffersPriceborderException e) {
					Log.d(uuid,
							"Aufgrund der Preisgrenzen, konnte die Änderung nicht zusammengeführt werden. Der Beitrag des Consumers wird abgelehnt.");
				} catch (IllegalArgumentException e) {
					Log.d(uuid,
							"Aufgrund unterschiedlicher Zeiten, konnte die Änderung nicht zusammengeführt werden. Der Beitrag des Consumers wird abgelehnt.");
				} finally {
					rejectConsumersContribution(c, ownOffer.getUUID());
				}
			}
		}

		// neue abweichung des erweiterten angebots gegenüber dem
		// marktplatzangebot berechnen
		// TODO Logik prüfen!
		Offer newMerge = null;
		Score newScore = null;

		if (contributionOffer != null) {
			try {
				newMerge = new Offer(bestScore.getMerge(), contributionOffer);
				newScore = new Score(newMerge, bestScore.getMarketplace(), bestScore.getOwn(), receivedOffer,
						contributionOffer);
			} catch (OffersPriceborderException e) {
				Log.d(uuid, "Angebote konnten nicht verknüpft werden.");
			} catch (IllegalArgumentException e) {
				Log.e(uuid, "Angebote konnten nicht verknüpft werden. Kein Preisfehler!");
			}
		}

		if (newScore == null) {
			Log.d(uuid, "Angebot konnte nicht verbessert werden. Vereinbarte Veränderungen werden verworfen.");

			for (UUID c : contributions.keySet()) {
				API<Void, Void> api2_decline = new API<Void, Void>(Void.class);
				api2_decline.consumers(c).offers(ownOffer.getUUID()).changeRequest().decline();
				api2_decline.call(this, HttpMethod.POST, null);
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

			Offer[] offerList = getOfferWithPrivileges(DateTime.ToString(DateTime.currentTimeSlot()));

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
			return;
		}

		Scorecard scorecard = new Scorecard(this, receivedOffer, offerWithPrivileges);

		if (scorecard.isEmpty()) {
			Log.d(uuid, "Keine Angebote am Marktplatz vorrätig. Nutze die Vorhersage zur Optimierung.");
			Offer marketplace = getMarketplacePrediction();
			for (Offer own : offerWithPrivileges) {
				try {
					System.out.println("Consumer 578: PING");
					scorecard.add(new Score(new Offer(own, receivedOffer), marketplace, own, receivedOffer, null));
				} catch (OffersPriceborderException e) {
					Log.d(uuid, "Preisgrenzen stimmen nicht überein.");
				}
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
			Log.d(uuid, "merge = own [" + scorecard.first().getOwn() + "] + received [" + receivedOffer + "] ");

		} else if (scorecard.size() == 1) {
			Log.d(uuid, "Keine Angebote als Vergleich vorhanden.");
			return;
			// newOffer = scorecard.first().getMerge();
		} else if (scorecard.first().getOwn().getDate().compareTo(DateTime.ToString(DateTime.nextTimeSlot())) == -1) {
			Log.d(uuid, "Angebot liegt in der aktuellen Stunde. Keine Verbesserung durch CRs möglich.");
			newOffer = scorecard.first().getMerge();
		} else {
			Log.d(uuid, "Versuche das eigene Angebot anzupassen um besser zu werden.");
			HashMap<UUID, AnswerChangeRequestLoadprofile> contributions = improveOwnOffer(scorecard.first().getOwn(),
					receivedOffer, scorecard.first());

			if (contributions == null) {
				Log.d(uuid, "Optimierung nicht möglich.");
				return;
			}

			newOffer = null;

			try {
				Offer own = scorecard.first().getOwn();
				Offer merge = new Offer(own, receivedOffer);

				Log.d(uuid, "Erzeuge neues Angebot basierend auf merge [" + merge + "] und contributions ["
						+ contributions + "]");
				newOffer = new Offer(merge, contributions);
				allOfferContributions.put(newOffer.getUUID(), contributions);
			} catch (OffersPriceborderException e) {
				Log.d(uuid, "OffersPriceborderException");
			}
		}

		newOffer.generateAuthKey();

		Log.d(uuid, "Neues Angebot [" + newOffer.getUUID() + "] erreicht: " + newOffer);
		addOffer(newOffer);
		allOfferMerges.put(newOffer.getUUID(), scorecard.first().getOwn());

		OfferNotification newNotification = new OfferNotification(newOffer.getAuthor(), newOffer.getUUID());
		Log.d(uuid, "Antworte auf das eingetroffene Angebot. " + newNotification);

		if (!newOffer.getAllLoadprofiles().keySet().containsAll(receivedOffer.getAllLoadprofiles().keySet())) {
			// Log.e(uuid, "Lastprofile verloren.");
			// return;
		}

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
		Log.d(uuid, "Consumer hat priceChangeRequest erhalten, UUID: " + negotiation);
		double price = answerOffer.getPrice();

		// Prüfe, dass der neue Preis innerhalb der Preisgrenzen des Angebotes
		// liegt
		// Liegt er dazwischen, bestätige ihn.
		// Liegt er nicht dazwischen, bestätige das Minimum bzw. das Maximum
		Offer offer = allOffers.get(answerOffer.getOffer());
		if (offer == null) {
			price = Double.POSITIVE_INFINITY;

			// Sende Antwort an Negotiation
			AnswerToPriceChangeRequest answer = new AnswerToPriceChangeRequest(uuid, price);
			Negotiation negotiationWhole = NegotiationContainer.instance().get(negotiation);
			if (negotiationWhole != null) {
				API<AnswerToPriceChangeRequest, Void> api = new API<AnswerToPriceChangeRequest, Void>(Void.class);
				api.negotiation().answerToPriceChangeRequest(negotiation);
				api.call(negotiationWhole, HttpMethod.POST, answer);
			}
			return;
		}
		double min = offer.getMinPrice();
		double max = offer.getMaxPrice();

		if (price < min) {
			price = min;
		}
		if (price > max) {
			price = max;
		}

		// Sende Antwort an Negotiation
		AnswerToPriceChangeRequest answer = new AnswerToPriceChangeRequest(uuid, price);
		Negotiation negotiationWhole = NegotiationContainer.instance().get(negotiation);
		if (negotiationWhole == null) {
			return;
		}

		API<AnswerToPriceChangeRequest, Void> api = new API<AnswerToPriceChangeRequest, Void>(Void.class);
		api.negotiation().answerToPriceChangeRequest(negotiation);
		Log.d(uuid, "Sende Antwort an Negotiation [" + api + "]: AnswerToPriceChangeRequest [" + answer + "]");
		api.call(negotiationWhole, HttpMethod.POST, answer);
	}

	/**
	 * Ändert die Variable currentAnswerChangeLoadprofile, sobald eine Antwort
	 * von einem anderen Consumer auf eine Änderungsanfrage eingetroffen ist und
	 * informiert über die Akualisierung
	 * 
	 * @param answer
	 *            Eingetroffene Antwort, die in currentAnswerChange Loadprofile
	 *            hinterlegt wird
	 */
	public void receiveAnswerChangeLoadprofile(AnswerChangeRequestLoadprofile answer, UUID offer) {
		currentAnswerChangeLoadprofile = answer;
		return;

	}

	public void receiveChangeRequestDecline(UUID uuidOffer) {
		if (!allOffers.containsKey(uuidOffer)) {
			return;
		}
		// Hole das betroffene Angebo
		Offer offer = allOffers.get(uuidOffer);

		// Erstelle einen ChangeRequestLoadprofile-Dummy nur mit der UUID des
		// Angebots
		ChangeRequestLoadprofile cr = new ChangeRequestLoadprofile(uuidOffer, null, null);

		// Wenn dieses Gerät Autor des Angebot, informiere alle weiteren
		// Teilnehmer über Absage
		if (uuid == offer.getAuthor()) {
			HashMap<UUID, HashMap<UUID, Loadprofile>> map = offer.getAllLoadprofiles();
			Set<UUID> uuidsConsumer = map.keySet();
			if (uuidsConsumer.size() != 0) {
				for (UUID current : uuidsConsumer) {
					if (current == uuid) {
						continue;
					}
					// Absage für ChangeRequest an Consumer
					API<Void, Void> api1 = new API<Void, Void>(Void.class);
					api1.consumers(current).offers(uuidOffer).changeRequest().decline();
					api1.call(this, HttpMethod.POST, null);
				}
			}
		}

		// Consumer informiert sein eigenes Gerät über Absage der Änderungen
		API<Boolean, Void> api2 = new API<Boolean, Void>(Void.class);
		api2.devices(device).changeRequest().decline();
		api2.call(this, HttpMethod.POST, false);
	}

	public void receiveChangeRequestConfirm(UUID uuidOffer) {
		if (!allOffers.containsKey(uuidOffer)) {
			Log.d(uuid, "receiveChangeRequestConfirm abbrechen");
			return;
		}
		// Hole das betroffene Angebot
		Offer offer = allOffers.get(uuidOffer);

		// Erstelle einen ChangeRequestLoadprofile-Dummy nur mit der UUID des
		// Angebots
		ChangeRequestLoadprofile cr = new ChangeRequestLoadprofile(uuidOffer, null, null);

		// Wenn dieses Gerät Autor des Angebot, informiere alle weiteren
		// Teilnehmer über Absage
		if (uuid == offer.getAuthor()) {
			HashMap<UUID, HashMap<UUID, Loadprofile>> map = offer.getAllLoadprofiles();
			Set<UUID> uuidsConsumer = map.keySet();
			for (UUID current : uuidsConsumer) {
				if (current == uuid) {
					continue;
				}
				// Zusage für ChangeRequest an Consumer
				API<ChangeRequestLoadprofile, Void> api1 = new API<ChangeRequestLoadprofile, Void>(Void.class);
				api1.consumers(current).offers(uuidOffer).changeRequest().confirm();
				api1.call(this, HttpMethod.GET, cr);
			}
		}

		// Consumer informiert sein eigenes Gerät über Zusage der Änderung
		API<Boolean, Void> api2 = new API<Boolean, Void>(Void.class);
		api2.devices(device).changeRequest().confirm();
		api2.call(this, HttpMethod.GET, true);
	}

	public AnswerChangeRequestLoadprofile receiveChangeRequestLoadprofile(ChangeRequestLoadprofile cr) {
		Log.d(uuid, "Änderungsanfrage vom Autor erhalten.");

		// Hole das betroffene Angebot aus der Hashmap aller Angebote
		Offer affectedOffer = allOffers.get(cr.getOffer());

		if (affectedOffer == null) {
			Log.e(uuid, "Das betroffene Angebot ist nicht vorhanden. Änderungen daher nicht möglich.");
			return new AnswerChangeRequestLoadprofile(cr.getOffer(), null);
		}

		AnswerChangeRequestLoadprofile answerNoChange = new AnswerChangeRequestLoadprofile(cr.getOffer(), null);

		Log.d(uuid, "Zeit für Änderungen passt, frage Devices");

		// Frage eigenes Device nach Änderung
		// (und passe noch benötigte Änderung an)
		AnswerChangeRequestSchedule answer = askDeviceForChange(
				new ChangeRequestSchedule(cr.getTime(), cr.getChange()));

		if (answer == null) {
			Log.e(uuid, "Keine Antwort erhalten?");
		}

		Log.d(uuid, "Angefragte Änderung: [" + Arrays.toString(cr.getChange()) + "]");
		Log.d(uuid, "Erhaltene Änderung: [" + Arrays.toString(answer.getChanges()) + "]");

		Loadprofile initialLoadprofile = getInitialLoadprofile();

		// Prüfe, ob ein initiales Lastprofil gefunden werden kann und wenn ja,
		// berechne dessen Summe
		if (initialLoadprofile == null) {
			Log.e(uuid, "Kein initiales Lastprofil zu der übergebenen Änderung vorhanden.");

			// Informiere Autor darüber, dass keine Änderung möglich
			return answerNoChange;
		}

		double sumInitialLoadprofile = 0;
		for (int i = 0; i < numSlots; i++) {
			sumInitialLoadprofile += initialLoadprofile.getValues()[i];
		}

		// Berechne den Preis der Änderung und die daraus resultierenden neuen
		// Minima und Maxima auf Basis des initialen Lastprofils
		double priceChange = answer.getPriceFactor() * affectedOffer.getPriceSugg();
		priceChange = priceChange + answer.getSumPenalty();

		double newMax, newMin;
		if (sumInitialLoadprofile < 0) {
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
			if (newMin > newMax) {
				// TODO Was soll hier passieren ??
				// Dann ändern sich Grenzen nicht
				newMin = initialLoadprofile.getMinPrice();
				Log.d(uuid, "Preis für Änderung war nicht möglich");
			}
		}

		// Lege neuen Preisvorschlag für das Lastprofil fest
		double newPriceSugg = initialLoadprofile.getPriceSugg();
		if (newPriceSugg < newMin) {
			newPriceSugg = newMin;
		}
		if (newPriceSugg > newMax) {
			newPriceSugg = newMax;
		}

		// Erstelle Antwort auf Change Request
		Loadprofile changedLoadprofile = new Loadprofile(answer.getChanges(), initialLoadprofile.getDate(),
				newPriceSugg, newMin, newMax, Loadprofile.Type.CHANGE_REQUEST);
		AnswerChangeRequestLoadprofile answerOfChange = new AnswerChangeRequestLoadprofile(cr.getOffer(),
				changedLoadprofile);

		// Informiere Autor über mögliche Änderungen
		return answerOfChange;
	}

	public void receiveChangeRequestLoadprofileFromMarketplace(ChangeRequestLoadprofile cr) {
		Log.d(uuid, "Änderungsanfrage vom Marktplatz erhalten");

		// Hole das betroffene Angebot aus der Hashmap aller Angebote
		Offer affectedOffer = allOffers.get(cr.getOffer());
		if (affectedOffer == null) {
			Log.e(uuid, "Das betroffene Angebot ist nicht vorhanden. Änderungen daher nicht möglich.");
			// Sende Antwort an Marketplace
			double[] allChanges = { 0, 0, 0, 0 };
			double currentPriceSugg = 0;
			ChangeRequestLoadprofile answerToMarketplace = new ChangeRequestLoadprofile(cr.getOffer(), allChanges,
					cr.getTime(), currentPriceSugg);
			API<ChangeRequestLoadprofile, Void> api = new API<ChangeRequestLoadprofile, Void>(Void.class);
			api.marketplace().receiveAnswerChangeRequestLoadprofile();
			api.call(this, HttpMethod.POST, answerToMarketplace);
			return;
		}
		Log.d(uuid, "Das Angebot wurde gefunden und eine Änderung wird erfragt");
		double currentMinPrice = affectedOffer.getMinPrice();
		double currentMaxPrice = affectedOffer.getMaxPrice();
		double currentPriceSugg = affectedOffer.getPriceSugg();

		// Hole die Informationen der erhaltenen ChangeRequest
		double[] requestedChanges = cr.getChange();

		// Speichere alle bereits vorgenommenen Änderungen
		double[] allChanges = { 0, 0, 0, 0 };

		// Holle die UUIDs aller beteiligten Consmer am Angebot und frage sie
		// nach Anpassung
		HashMap<UUID, HashMap<UUID, Loadprofile>> allLP = affectedOffer.getAllLoadprofiles();
		Set<UUID> uuidConsumers = allLP.keySet();
		for (UUID current : uuidConsumers) {
			// Schicke Anfrage an den aktuellen Consumer
			API<ChangeRequestLoadprofile, AnswerChangeRequestLoadprofile> api = new API<ChangeRequestLoadprofile, AnswerChangeRequestLoadprofile>(
					AnswerChangeRequestLoadprofile.class);
			api.consumers(current).offers(cr.getOffer()).changeRequestConsumer();
			api.call(this, HttpMethod.POST, cr);

			// Warte auf eine Antwort des Consumers
			/*
			 * UUID testUUID = UUID.randomUUID(); AnswerChangeRequestLoadprofile
			 * test = new AnswerChangeRequestLoadprofile(testUUID, null);
			 * currentAnswerChangeLoadprofile = test; synchronized
			 * (currentAnswerChangeLoadprofile) { if
			 * (currentAnswerChangeLoadprofile.getUUIDOffer() ==
			 * test.getUUIDOffer()) { System.out.println(
			 * "Noch keine Antwort erhalten"); } while
			 * (currentAnswerChangeLoadprofile.getUUIDOffer() ==
			 * test.getUUIDOffer()) { try {
			 * currentAnswerChangeLoadprofile.wait(); } catch
			 * (InterruptedException e) { e.printStackTrace(); } } }
			 */

			// Prüfe, dass Antwort auch zu passendem Angebot ist
			// if
			// (!currentAnswerChangeLoadprofile.getUUIDOffer().equals(cr.getOffer()))
			// {
			// continue;
			// }

			// Hole die Änderungen und die dafür verlangten Preise in answerLP
			Loadprofile answerLP = api.getResponse().getLoadprofile();

			// Wenn kein Lastprofil übergeben wurde, fahre mit dem nächsten
			// Consumer fort
			if (answerLP == null) {
				continue;
			}
			double answerPrice = answerLP.getPriceSugg();
			double[] answerChanges = answerLP.getValues();

			// Prüfe, ob die Preisgrenzen passen
			double answerMin = answerLP.getMinPrice();
			double answerMax = answerLP.getMaxPrice();
			if (answerMin > currentMaxPrice || answerMax < currentMinPrice) {
				Log.d(uuid, "Absage an Consumer für Änderungen wegen Preis");

				// Absage für ChangeRequest an Consumer
				API<ChangeRequestLoadprofile, Void> api1 = new API<ChangeRequestLoadprofile, Void>(Void.class);
				api1.consumers(current).offers(current).changeRequest().decline();
				api1.call(this, HttpMethod.POST, cr);
				continue;
			} else {
				currentMaxPrice = answerMax;
				currentMinPrice = answerMin;
				currentPriceSugg = (answerPrice + currentPriceSugg) / 2;
			}
			if (currentPriceSugg < currentMinPrice) {
				currentPriceSugg = currentMinPrice;
			}
			if (currentPriceSugg > currentMaxPrice) {
				currentPriceSugg = currentMaxPrice;
			}

			// Prüfe, ob Änderungen sinnvoll
			double[] changesBefore = cr.getChange();
			double sumChangesBefore = 0;
			double sumAnswerChanges = 0;
			for (int i = 0; i < numSlots; i++) {
				sumChangesBefore += Math.abs(changesBefore[i]);
				sumAnswerChanges += Math.abs(requestedChanges[i] - answerChanges[i]);
			}
			// Wenn noch benötigte Änderungen jetzt größer als zuvor, sage ab
			if (sumAnswerChanges > sumChangesBefore) {
				Log.d(uuid, "Absage an Consumer für Änderungen wegen schlechten Änderungen");

				// Absage für ChangeRequest an Consumer
				API<ChangeRequestLoadprofile, Void> api1 = new API<ChangeRequestLoadprofile, Void>(Void.class);
				api1.consumers(current).offers(current).changeRequest().decline();
				api1.call(this, HttpMethod.POST, cr);
				continue;
			}

			// Passe noch benötigte und bereits vorgenommene Änderungen an
			for (int i = 0; i < numSlots; i++) {
				requestedChanges[i] = requestedChanges[i] - answerChanges[i];
				allChanges[i] += answerChanges[i];
			}

			// Passe noch benötigte Änderungen in ChangeRequest an
			cr.setChange(requestedChanges);
		}

		// Sende Antwort an Marketplace
		ChangeRequestLoadprofile answerToMarketplace = new ChangeRequestLoadprofile(cr.getOffer(), allChanges,
				cr.getTime(), currentPriceSugg);
		API<ChangeRequestLoadprofile, Void> api = new API<ChangeRequestLoadprofile, Void>(Void.class);
		api.marketplace().receiveAnswerChangeRequestLoadprofile();
		api.call(this, HttpMethod.POST, answerToMarketplace);
	}

	public void receiveDeltaLoadprofile(Loadprofile deltaLoadprofile) {
		Log.d(this.uuid, "Deltalastprofil erhalten: " + deltaLoadprofile);
		String timeLoadprofile = deltaLoadprofile.getDate();
		String timeCurrent = DateTime.ToString(DateTime.now());
		double[] valuesNew = deltaLoadprofile.getValues();

		// Prüfe, ob deltaLoadprofile Änderungen für die aktuelle Stunde hat
		boolean currentHour = DateTime.parse(timeLoadprofile).get(Calendar.HOUR_OF_DAY) == DateTime.parse(timeCurrent)
				.get(Calendar.HOUR_OF_DAY);
		if (currentHour) {
			// Prüfe, ob deltaLoadprofile Änderungen für die noch kommenden
			// Slots beinhaltet
			int minuteCurrent = DateTime.parse(timeCurrent).get(Calendar.MINUTE);
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
		double[] valuesOld = deltaLoadprofiles.get(timeLoadprofile);
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
			deltaLoadprofiles.put(timeLoadprofile, valuesNew);
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

		Log.d(uuid, "Sende Angebot [" + offer.getUUID() + "] and Marktplatz.");
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
		// Log.d(this.uuid, offerNotification.toString());
		notificationQueue.add(offerNotification);
	}

	private void rejectConsumersContribution(UUID uuidConsumer, UUID uuidOffer) {
		API<Void, Void> api2 = new API<Void, Void>(Void.class);
		api2.consumers(uuidConsumer).offers(uuidOffer).changeRequest().decline();
		api2.call(this, HttpMethod.POST, null);
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
	 * @param uuidOldOffer
	 *            Angebots-ID des zu ersetzende Angebots
	 * @param uuidNewOffer
	 *            neue Angebots-ID
	 */
	public void replaceOffer(UUID uuidOldOffer, UUID uuidNewOffer) {
		Offer oldOffer = getOfferIntern(uuidOldOffer);
		if (oldOffer == null) {
			Log.e(uuid, "Altes zu ersetzendes Angebot nicht mehr vorhanden?!");
			return;
		}

		// das neue Angebot besorgen
		Offer newOffer = getOfferFromUrl(oldOffer.getAuthor(), uuidNewOffer);

		if (newOffer == null) {
			Log.e(uuid, "Angebot konnte nicht ersetzt werden da extern nicht vorhanden?!");
			return;
		}

		Log.d(uuid, "Ersetze Angebot [" + uuidOldOffer + "] durch das neue Angebot [" + uuidNewOffer + "]");

		// altes Angebot entfernen
		removeOffer(uuidOldOffer);

		// neues Angebot einfügen
		addOffer(newOffer);
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

	private void invalidateOfferAtMarketplace(Offer offer) {
		API<Offer, Void> api2 = new API<>(Void.class);
		api2.marketplace().offers(offer.getUUID()).invalidate();
		api2.call(this, HttpMethod.GET, offer);
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

	private String valuesToString(double[] values) {
		String s = ": ";
		for (int i = 0; i < values.length; i++) {
			s = s + "[" + values[i] + "]";
		}
		return s;
	}
}
