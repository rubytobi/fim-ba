package Entity;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import Packet.AnswerToOfferFromMarketplace;
import Packet.EndOfNegotiation;
import Packet.SearchParams;
import Packet.ChangeRequestLoadprofile;
import Util.MatchedOffers;
import Util.API;
import Util.ConfirmedOffer;
import Util.DateTime;
import Util.Log;
import Util.PossibleMatch;
import Util.ResponseBuilder;
import Util.Negotiation;
import Util.sortOfferPriceSupplyLowToHigh;
import start.Application;
import Util.sortOfferPriceDemandHighToLow;

/**
 * Marktplatz, auf welchem alle Angebote eintreffen und zusammengefuehrt werden
 */
public class Marketplace implements Identifiable {
	/**
	 * Map, die nach Startzeit alle schon erfolglos verhandelten
	 * Angebotskombinationen enthaelt
	 */
	private Map<String, ArrayList<PossibleMatch>> blackListPossibleMatches = new TreeMap<String, ArrayList<PossibleMatch>>();
	
	/**
	 * Map, die nach Startzeit alle bestätigten Angebote enthaelt
	 */
	private Map<String, ArrayList<ConfirmedOffer>> confirmedOffers = new TreeMap<String, ArrayList<ConfirmedOffer>>();
	
	private ChangeRequestLoadprofile currentAnswer;

	/**
	 * Maps, die alle noch nicht zusammengefuehrten Angebote des Marktplatzes
	 * beinhaltet
	 */
	private HashMap<String, ArrayList<Offer>> demand = new HashMap<String, ArrayList<Offer>>();
	/**
	 * Aktueller eexPreis
	 */
	private static final double eexPrice = 20;

	private static Marketplace instance = null;

	/**
	 * Map, die nach Startzeit alle moeglichen Angebotskombinationen enthaelt
	 */
	private HashMap<String, ArrayList<PossibleMatch>> listPossibleMatches = new HashMap<String, ArrayList<PossibleMatch>>();

	/**
	 * Aktuell geduldete Abweichung. (5% von erwartetem Gesamtvolumen: 100)
	 */
	private double maxDeviation = 0.05 * 100;

	/**
	 * Map, die alle bisher zusammengefuehrten Angebote nach Zeitslot beinhaltet
	 */
	private HashMap<String, ArrayList<MatchedOffers>> matchedOffers = new HashMap<String, ArrayList<MatchedOffers>>();

	/**
	 * Minute, zu welcher die zweite Phase des Marktplatzes starten soll
	 */
	private int minuteOfSecondPhase = Application.Params.marketplaceMinuteOfSecondPhase;

	/**
	 * Map, die alle Angebote beinhaltet, ueber deren Preis gerade mit den
	 * Consumern verhandelt wird
	 */
	private HashMap<UUID, Negotiation> negotiatingOffers = new HashMap<UUID, Negotiation>();

	/**
	 * Startzeit des naechsten Slots, der geplant werden muss
	 */
	private GregorianCalendar nextSlot;

	/**
	 * Anzahl an 15-Minuten-Slots der Angebote
	 */
	private int numSlots = 4;

	/**
	 * Map, die die Prognose fuer den jeweiligen Zeitslot beinhaltet
	 */
	private HashMap<String, double[]> prediction = new HashMap<String, double[]>();

	/**
	 * Einheitspreis mit Strafe, zu welchem der jeweilige Slot bestätigt wurde.
	 * Enthält Preis für Demand (0) und für Supply (1)
	 */
	private HashMap<String, double[]> unitPricesWithPenalty = new HashMap<String, double[]>();

	/**
	 * Map, die die Summe der Abweichungen aller zusammengefuehrter Angebote
	 * nach Zeitslot beinhaltet
	 */
	private HashMap<String, double[]> sumLoadprofilesConfirmedOffers = new HashMap<String, double[]>();

	/**
	 * Map, die die Summe der Abweichungen aller auf dem Marktplatz
	 * verfuegbaren, aller gerade verhandelten und aller vom Marktplatz
	 * zusammengefuehrten Angebote nach Zeitslot beinhaltet
	 */
	private HashMap<String, double[]> sumLoadprofilesAllOffers = new HashMap<String, double[]>();

	private HashMap<String, ArrayList<Offer>> supply = new HashMap<String, ArrayList<Offer>>();

	private UUID uuid = UUID.randomUUID();

	/**
	 * Erstellt einen neuen Marktplatz mit Prognose = 0 fuer die naechsten 24h
	 */
	private Marketplace() {
		// Lege den Startzeitpunkt fest
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		now.add(Calendar.HOUR_OF_DAY, 1);
		this.nextSlot = now;

		System.out.println("Now: " + DateTime.ToString(now));

		// Setzt die Vorhersage fuer die naechsten 24h = 0
		GregorianCalendar count = (GregorianCalendar) now.clone();
		double[] zeroValues = { 0, 0, 0, 0 };
		for (int i = 0; i < 24; i++) {
			prediction.put(DateTime.ToString(count), zeroValues);
			System.out.println(DateTime.ToString(count) + ": " + prediction.get(DateTime.ToString(count))[0]);
			count.add(Calendar.HOUR_OF_DAY, 1);
		}
	}

	public static Marketplace instance() {
		if (instance == null) {
			instance = new Marketplace();
		}
		return instance;
	}

	/**
	 * ueberprueft, ob schon minuteOfSecondPhase oder groeßer erreicht ist und
	 * macht ggf. den naechsten Slot.
	 * 
	 */
	public void BKV() {
		// Hole die aktuelle Zeit des Systems
		GregorianCalendar now = DateTime.now();
		System.out.println("Aktuelle Zeit: " + DateTime.ToString(now));
		System.out.println("Next Slot: " +DateTime.ToString(nextSlot));

		// Berechne den Startzeitpunkt der als letztes gematchten Stunde
		GregorianCalendar slotLastMatched = (GregorianCalendar) nextSlot.clone();
		slotLastMatched.add(Calendar.HOUR_OF_DAY, -1);

		// Berechne aktuellen Slot
		int slot;
		double minute = now.get(Calendar.MINUTE);
		if (now.get(Calendar.HOUR_OF_DAY) == slotLastMatched.get(Calendar.HOUR_OF_DAY)) {
			slot = (int) Math.floor(minute / 15);
		} else {
			slot = 0;
		}
		System.out.println("Slot: " + slot);

		// Bestätige die Angebote zum Einheitspreis, die jetzt fällig sind
		confirmAllRemainingOffersWithOnePrice(slotLastMatched, slot, false);

		System.out.println(minute + " Minuten >= " + minuteOfSecondPhase + ": " + (minute >= minuteOfSecondPhase));

		// Prüfe, ob die aktuelle Zeit schon nah genug am nächsten zur
		// matchenden Slot dran ist oder ob der Slot sogar schon erreicht ist.
		// Wenn ja, matche den nächsten Slot.

		if (now.get(Calendar.HOUR_OF_DAY) + 1 == nextSlot.get(Calendar.HOUR_OF_DAY) && minute >= minuteOfSecondPhase
				|| now.get(Calendar.HOUR_OF_DAY) + 1 > nextSlot.get(Calendar.HOUR_OF_DAY)) {
			System.out.println("MatchNextSlot");
			matchNextSlot();
		}

	}

	/**
	 * Berechnet die Abweichung von sumLoadprofilesConfirmedOffers von der
	 * Prognose.
	 * 
	 * @param start
	 *            Start der Stunde, fuer die die Abweichung berechnet wird
	 * @return Array mit der Abweichung von sumLoadprofilesConfirmed von der
	 *         Prognose in 15-Minuten-Einheiten
	 */
	private double[] chargeDeviationConfirmed(GregorianCalendar start) {
		double[] deviation = new double[numSlots];
		double[] currentPrediction = prediction.get(DateTime.ToString(start));
		double[] currentConfirmed = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(start));
		if (currentConfirmed == null) {
			currentConfirmed = new double[numSlots];
			for (int i = 0; i < numSlots; i++) {
				currentConfirmed[i] = 0;
			}
		}
		for (int i = 0; i < numSlots; i++) {
			deviation[i] = currentPrediction[i] - currentConfirmed[i];
		}
		return deviation;
	}

	/**
	 * Berechnet die Abweichung von sumLoadprofilesAllOffers von der Prognose
	 * 
	 * @param start
	 *            Start der Stunde, fuer die die Abweichung berechnet wird
	 * @return Array mit der Abwecihung von sumLoadprofilesAllOffers von der
	 *         Prognose in 15-Minuten-Einheiten
	 */
	private double[] chargeDeviationAll(GregorianCalendar start) {
		this.marketplaceToString();
		System.out.println("ChargeDeviationAll");
		System.out.println("Für: " + DateTime.ToString(start));

		double[] deviationAll = new double[numSlots];
		double[] currentPrediction = prediction.get(DateTime.ToString(start));
		double[] currentAll = sumLoadprofilesAllOffers.get(DateTime.ToString(start));
		Set<String> set = sumLoadprofilesAllOffers.keySet();
		System.out.println(set.toString());

		if (currentPrediction == null) {
			currentPrediction = new double[4];
			for (int i = 0; i < numSlots; i++) {
				currentPrediction[i] = 0;
			}
		}
		System.out.println("Prediction 0: " + currentPrediction[0]);

		if (currentAll == null) {
			currentAll = new double[4];
			for (int i = 0; i < numSlots; i++) {
				currentAll[i] = 0;
			}
		}
		System.out.println("currentAll: " +valuesToString(currentAll));

		for (int i = 0; i < numSlots; i++) {
			deviationAll[i] = Math.round(100.00 * (currentAll[i] - currentPrediction[i])) / 100.00;
		}
		return deviationAll;
	}

	/**
	 * Bestaetigt alle verbliebenen Angebot mit uebergebenem Start und bei
	 * welchen die Summe der Lastprofilwerte bis einschließlich des uebergebenen
	 * Slots > 0 sind zu einem Einheitspreis mit Strafe
	 * 
	 * @param date
	 *            Startzeit, fuer welche alle verbliebenen Angebote bestaetigt
	 *            werden sollen
	 * @param slot
	 *            Anzahl der Slots, ueber die die Summe gebildet werden muss
	 */
	private void confirmAllRemainingOffersWithOnePrice(GregorianCalendar date, int slot, boolean save) {
		marketplaceToString();

		String dateString = DateTime.ToString(date);
		System.out.println("Einheitspreis: " + dateString + " Slot: " + slot);
		System.out.println("Date: " + dateString);

		// Prüfe, ob noch alte Angebote (vor date) vorliegen und wenn ja,
		// bestätige diese auch durch den Einheitspreis
		Set<String> allDates = demand.keySet();
		for (String currentDate : allDates) {
			GregorianCalendar current = DateTime.stringToCalendar(currentDate);
			if (current.before(date)) {
				if (demand.get(current).size() == 0) {
					System.out.println("Entferne Zeit: " +current);
					demand.remove(current);
				}
				else {
					System.out.println("Es liegen noch ältere Angebote für " + currentDate + " vor.");
					confirmAllRemainingOffersWithOnePrice(current, numSlots - 1, false);
				}
			}
		}

		// Prüfe, dass slot nicht zu groß ist und passe es ggf. an
		if (slot > numSlots - 1) {
			slot = numSlots - 1;
		}

		// Hole alle Angebote zu dem uebergebenen Datum und pruefe, ob wirklich
		// Angebote vorliegen
		ArrayList<Offer> allDemandsAtDate = demand.get(dateString);
		ArrayList<Offer> allSuppliesAtDate = supply.get(dateString);

		if (allDemandsAtDate == null && allSuppliesAtDate == null) {
			System.out.println("Keine Angebote für dieses Date");
			return;
		}

		// Erstelle Arrays, in welchen alle Angebote gesammelt werden, welche
		// zum Einheitspreis bestätigt werden sollen
		ArrayList<Offer> demandOnePrice = new ArrayList<Offer>();
		ArrayList<Offer> supplyOnePrice = new ArrayList<Offer>();
		double volumeDemand = 0;
		double volumeSupply = 0;
		double sumPricesDemand = 0;
		double sumPricesSupply = 0;

		if (allDemandsAtDate != null) {
			for (Offer currentOffer : allDemandsAtDate) {
				double sumUntilCurrentSlot = 0;
				// Berechne die Summe des Lastprofils einschließlich des
				// aktuellen Slots
				for (int i = 0; i <= slot; i++) {
					sumUntilCurrentSlot += Math.abs(currentOffer.getAggLoadprofile().getValues()[i]);
				}

				// Nur, wenn die oben berechnete Summe des Lastprofils ungleich
				// 0 ist, muss das Angebot zum Einheitspreis mit Strafe
				// bestaetigt werden.
				// Andernfalls nicht, da es noch Zeit hat einen Partner zu
				// finden.
				if (sumUntilCurrentSlot != 0) {
					demandOnePrice.add(currentOffer);
					volumeDemand += currentOffer.getSumAggLoadprofile();
					sumPricesDemand += currentOffer.getSumAggLoadprofile() * currentOffer.getPriceSugg();
				}
			}

			System.out.println("\nSumPricesDemand: " + sumPricesDemand);
			System.out.println("VolumeDemand: " + volumeDemand);
		}

		if (allSuppliesAtDate != null) {
			for (Offer currentOffer : allSuppliesAtDate) {
				double sumUntilCurrentSlot = 0;
				// Berechne die Summe des Lastprofils einschließlich des
				// aktuellen Slots
				for (int i = 0; i == slot; i++) {
					sumUntilCurrentSlot += Math.abs(currentOffer.getAggLoadprofile().getValues()[i]);
				}

				// Nur, wenn die oben berechnete Summe des Lastprofils ungleich
				// 0 ist, muss das Angebot zum Einheitspreis mit Strafe
				// bestaetigt werden.
				// Andernfalls nicht, da es noch Zeit hat einen Partner zu
				// finden.
				if (sumUntilCurrentSlot != 0) {
					supplyOnePrice.add(currentOffer);
					volumeSupply += currentOffer.getSumAggLoadprofile();
					sumPricesSupply += currentOffer.getSumAggLoadprofile() * currentOffer.getPriceSugg();
				}
			}

			System.out.println("\nSumPricesSupply: " + sumPricesSupply);
			System.out.println("VolumeSupply: " + volumeSupply);
		}

		// Beende alle aktuellen Verhandlungen der Angebote, die zum
		// Einheitspreis zusammengeführt werden sollen
		Set<UUID> allNegotiations = negotiatingOffers.keySet();
		if (allNegotiations.size() != 0) {
			System.out.println(allNegotiations.size() + " Negotiations werden beendet.");
			for (UUID negotiation : allNegotiations) {
				Negotiation currentNeg = negotiatingOffers.get(negotiation);
				Offer[] offers = currentNeg.getOffers();
				Offer offer1 = offers[0];
				Offer offer2 = offers[1];
				if (supplyOnePrice.contains(offer1) || demandOnePrice.contains(offer1)
						|| supplyOnePrice.contains(offer2) || demandOnePrice.contains(offer2)) {
					EndOfNegotiation end = new EndOfNegotiation(currentNeg.getUUID(), 0, 0, false);
					endOfNegotiation(end);
				}
			}
		}

		double middle = (Math.abs(sumPricesDemand) + Math.abs(sumPricesSupply)) / 2;
		System.out.println("\nMittelpreis: " + middle);

		// Berechne Strafe und schlage sie auf den berechneten Preis auf
		double penalty = middle * 0.1;
		double priceDemand, priceSupply;
		System.out.println("Strafe: " + penalty);
		if (volumeDemand == 0) {
			priceDemand = 0;
			priceSupply = -penalty * 2;
		} else if (volumeSupply == 0) {
			priceDemand = eexPrice + penalty * 2;
			priceSupply = 0;
		} else {
			priceDemand = Math.abs((middle + penalty) / volumeDemand);
			priceSupply = (middle - penalty) / volumeSupply;
		}
		System.out.println("Preis Demand: " + priceDemand);
		System.out.println("Preis Suuply: " + priceSupply);

		// Speichere die Einheitspreise mit Strafe für den jeweiligen Slot
		if (save) {
			System.out.println("Errechnete Preise werden gespeichert");
			double[] prices = { priceDemand, priceSupply };
			unitPricesWithPenalty.put(DateTime.ToString(date), prices);
		}

		// Bestaetige alle Angebote des Zeitraums mit den errechneten Preisen
		if (demandOnePrice.size() != 0) {
			for (Offer demand : demandOnePrice) {
				confirmOffer(demand, priceDemand, ConfirmedOffer.Type.UNITPRICE);
			}
		} else {
			System.out.println("Demands At Date leer");
		}

		if (supplyOnePrice.size() != 0) {
			for (Offer supply : supplyOnePrice) {
				confirmOffer(supply, priceSupply, ConfirmedOffer.Type.UNITPRICE);
			}
		} else {
			System.out.println("Keine Supplies At Date");
		}

		System.out.println("Einheitspreis beendet.");
	}

	/**
	 * Schickt an alle Consumer des Angebots eine Bestaetigung mit dem neuen
	 * Preis
	 * 
	 * @param offer
	 *            Angebot, das bestaetigt wird
	 * @param newPrice
	 *            Neuer, vom Marktplatz festgelegter Preis des Angebots
	 */
	private void confirmOffer(Offer offer, double newPrice, ConfirmedOffer.Type type) {
		System.out.println("Angebot wird bestätigt zu: " + newPrice);
		double[] valuesLP = offer.getAggLoadprofile().getValues();
		System.out.println("Zeit: " + DateTime.ToString(offer.getDate()));
		System.out.println("[" + valuesLP[0] + "][" + valuesLP[1] + "][" + valuesLP[2] + "][" + valuesLP[3] + "]");
		
		// Speichere Angebot unter confirmedOffers
		ArrayList<ConfirmedOffer> confirmedAtDate = confirmedOffers.get(DateTime.ToString(offer.getDate()));
		if (confirmedAtDate == null) {
			confirmedAtDate = new ArrayList<ConfirmedOffer>();
		}
		ConfirmedOffer newConfirmedOffer = new ConfirmedOffer(offer, newPrice, type);
		confirmedAtDate.add(newConfirmedOffer);
		//confirmedOffers.put(DateTime.ToString(offer.getDate()), confirmedAtDate);

		// Entferne offer von Marktplatz
		removeOffer(offer.getUUID(), true);

		// Nehme Lastprofil von offer in die Summe der Lastprofile aller
		// bestaetigten Angebote auf
		double[] values = offer.getAggLoadprofile().getValues().clone();
		
		// Hole die Anpassungen des Angebots
		double[] changes = offer.getChanges();
		if (changes == null) {
			changes = new double[numSlots];
			for (int i=0; i<numSlots; i++) {
				changes[i] = 0;
			}
		}
		else {
			// Aktualisiere die Summe der Lastprofile aller Angebote, falls eine Anpassung vorgenommen wurde
			double[] oldAllValues = sumLoadprofilesAllOffers.get(DateTime.ToString(offer.getDate()));
			if (oldAllValues == null) {
				Log.e(uuid, "Es lag keine Sumem aller Lastprofile vor");
			}
			else {
				for (int i=0; i<numSlots; i++) {
					oldAllValues[i] += changes[i];
				}
				sumLoadprofilesAllOffers.put(DateTime.ToString(offer.getDate()), oldAllValues);
			}
		}
		
		// Aktualisiere die Summer aller Lastprofile aller bestätigten Angebote
		double[] oldValues = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(offer.getDate()));
		if (oldValues == null) {
			oldValues = new double[numSlots];
			for (int i = 0; i < numSlots; i++) {
				oldValues[i] = 0;
			}
		}
		for (int i = 0; i < numSlots; i++) {
			values[i] += oldValues[i] + changes[i];
		}
		sumLoadprofilesConfirmedOffers.put(DateTime.ToString(offer.getDate()), values);

		AnswerToOfferFromMarketplace answerOffer = new AnswerToOfferFromMarketplace(offer.getUUID(), newPrice);

		// Informiere Autor des Angebots über Bestätigung
		UUID author = offer.getAuthor();
		API<AnswerToOfferFromMarketplace, Void> api2 = new API<AnswerToOfferFromMarketplace, Void>(Void.class);
		api2.consumers(author).offers(offer.getUUID()).confirmByMarketplace();
		api2.call(this, HttpMethod.POST, answerOffer);
	}

	/**
	 * Wird von Negotiation aufgerufen, wenn eine Verhandlung beendet ist. Ist
	 * die Verhandlung erfolgreich beendet worden, so werden beide Angebote zu
	 * den neuen Preisen bestaetigt. Ist die Verhandlung nicht erfolgreich
	 * beendet worden, so werden beide Angebote wieder auf den Marktplatz
	 * gestellt. Die Negotiation wird aus der Liste negotiatingOffers entfernt.
	 * 
	 * @param end
	 *            Verhandlung, die beendet wurde
	 */
	public void endOfNegotiation(EndOfNegotiation end) {
		UUID negotiation = end.getNegotiation();
		double newPrice1 = end.getNewPrice1();
		double newPrice2 = end.getNewPrice2();
		boolean successful = end.getSuccessful();
		System.out.println("***End of Negotiation: " + successful + " ***\n");
		System.out.println("NewPrice1: " + newPrice1 + " NewPrice2: " + newPrice2);
		Negotiation currentNegotiation = negotiatingOffers.get(negotiation);
		Offer[] offers = currentNegotiation.getOffers();
		String date = DateTime.ToString(offers[0].getDate());

		if (successful) {
			// Lege zusammengefuehrte Angebote und Preise in der Historie ab
			MatchedOffers matched = new MatchedOffers(newPrice1, newPrice2, offers[0], offers[1]);
			ArrayList<MatchedOffers> array = matchedOffers.get(date);
			if (array == null) {
				array = new ArrayList<MatchedOffers>();
			}
			array.add(matched);
			matchedOffers.put(date, array);

			confirmOffer(offers[0], newPrice1, ConfirmedOffer.Type.MATCHED);
			confirmOffer(offers[1], newPrice2, ConfirmedOffer.Type.MATCHED);
		} else {
			// Wenn der Slot der Angebote schon vollständig gematched wurde,
			// werden die Angebote zum Einheitspreis zusammengefuehrt
			if (offers[0].getDate().before(nextSlot)) {
				// Hole die Einheitspreise zur Startzeit der Angebote
				double[] unitPrices = unitPricesWithPenalty.get(DateTime.ToString(offers[0].getDate()));

				// Prüfe, ob Einheitspreis für diese Zeit vorliegt
				if (unitPrices != null) {
					// Berechne, ob die Angebote jeweils Supply oder Demand sind
					double[] values0 = offers[0].getAggLoadprofile().getValues();
					double[] values1 = offers[1].getAggLoadprofile().getValues();
					double sumValues0 = 0;
					double sumValues1 = 0;
					for (int i = 0; i < numSlots; i++) {
						sumValues0 += values0[i];
						sumValues1 += values1[i];
					}

					// Lege den Einheitspreis der Angebote fest, je nachdem, ob
					// sie
					// Supply oder Demand sind
					double price0, price1;
					if (sumValues0 < 0) {
						price0 = unitPrices[0];
					} else {
						price0 = unitPrices[1];
					}
					if (sumValues1 < 0) {
						price1 = unitPrices[0];
					} else {
						price1 = unitPrices[1];
					}

					// Bestätige Angebote zum geltenden Einheitspreis
					confirmOffer(offers[0], price0, ConfirmedOffer.Type.UNITPRICE);
					confirmOffer(offers[1], price1, ConfirmedOffer.Type.UNITPRICE);
				}
				// Wenn kein Einheitspreis vorliegt, berechne neuen
				// Einheitspreis für diese beiden Angebote
				else {
					confirmAllRemainingOffersWithOnePrice(offers[0].getDate(), 4, false);
				}
			}
			// Wurde der Slot noch nicht gematched, so kommen die Angebote
			// wieder auf den Marktplatz
			else {
				// Setze Kombination der beiden Angebote auf die Black List
				PossibleMatch possibleMatch = new PossibleMatch(offers[0], offers[1]);
				ArrayList<PossibleMatch> possibleMatchesBlackList = blackListPossibleMatches.get(date);
				if (possibleMatchesBlackList == null) {
					possibleMatchesBlackList = new ArrayList<PossibleMatch>();
				}
				possibleMatchesBlackList.add(possibleMatch);
				blackListPossibleMatches.put(date, possibleMatchesBlackList);

				// Setze beide Angebote wieder neu auf den Marktplatz
				receiveOffer(offers[0]);
				receiveOffer(offers[1]);
			}
			negotiatingOffers.remove(negotiation);
		}
	}

	/**
	 * Prueft, ob auf dem Marktplatz ein passendes Angebot verfuegbar ist. Wenn
	 * ein passendes Angebot verfuegbar ist, werden die Angebote
	 * zusammengefuehert und bestaetigt.
	 * 
	 * @param offer
	 *            Angebot, fuer das ein anderes Angebot auf dem Marktplatz
	 *            gesucht wird
	 * @param offerIsSupplyOffer
	 *            Information, ob das Angebot ein Erzeugerangebot ist
	 * @return false, wenn kein passendes Angebot gefunden wurde. true, wenn ein
	 *         passendes Angebot gefunden wurde
	 */
	public boolean findFittingOffer(Offer offer, boolean offerIsSupplyOffer) {
		System.out.println("***Find fitting Offer***");
		ArrayList<Offer> offers;
		// Immer Gegenangebot, wegen Preis
		if (offerIsSupplyOffer) {
			offers = demand.get(DateTime.ToString(offer.getDate()));
		} else {
			offers = supply.get(DateTime.ToString(offer.getDate()));
		}
		if (offers == null) {
			return false;
		}
		int numSlots = 4;

		Offer offerMostImprovement = offer;
		double[] valuesOffer = offer.getAggLoadprofile().getValues();
		double minPriceOffer = offer.getMinPrice();
		double maxPriceOffer = offer.getMaxPrice();

		// Hole alle aktuellen Werte fuer Vorhersage
		double[] predictionCurrent = prediction.get(DateTime.ToString(offer.getDate()));
		double[] deviationCurrentPrediction = chargeDeviationConfirmed(offer.getDate());
		double sumDeviationCurrentPrediction = 0;
		double[] sumLoadprofilesCurrent = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(offer.getDate()));
		if (sumLoadprofilesCurrent == null) {
			sumLoadprofilesCurrent = new double[numSlots];
			for (int i = 0; i < numSlots; i++) {
				sumLoadprofilesCurrent[i] = 0;
			}
		}

		// Gibt die Abweichung von der Prognose fuer alle bestaetigten
		// Lastprofile
		// und das aktuelle Angebot an
		double[] deviationOfferPrediction = new double[numSlots];
		double sumDeviationOfferPrediction = 0;

		for (int i = 0; i < numSlots; i++) {
			deviationOfferPrediction[i] = predictionCurrent[i] - sumLoadprofilesCurrent[i] - valuesOffer[i];
			sumDeviationOfferPrediction += Math.abs(deviationOfferPrediction[i]);
			sumDeviationCurrentPrediction += Math.abs(deviationCurrentPrediction[i]);
		}

		// Gibt die Abweichung von der Prognose fuer
		// deviationOffer-mostImprovement an
		double sumDeviationMostImprovementPrediction = sumDeviationOfferPrediction;

		// Hole den Array aller bereits in listPossibleMatches hinterlegten
		// possibleMatches
		// fuer die Startzeit dateOffer
		ArrayList<PossibleMatch> possibleMatchesOfDateOffer = listPossibleMatches
				.get(DateTime.ToString(offer.getDate()));
		if (possibleMatchesOfDateOffer == null) {
			possibleMatchesOfDateOffer = new ArrayList<PossibleMatch>();
		}
		ArrayList<PossibleMatch> blackListPossibleMatchesOfDateOffer = blackListPossibleMatches
				.get(DateTime.ToString(offer.getDate()));

		for (Offer compareOffer : offers) {
			// Pruefe, ob Kombination der Angebote auf Blacklist
			// Wenn ja, ueberspringe dieses Angebot
			PossibleMatch possibleMatch = new PossibleMatch(offer, compareOffer);
			if (blackListPossibleMatchesOfDateOffer != null) {
				if (blackListPossibleMatchesOfDateOffer.contains(possibleMatch)) {
					continue;
				}
			}

			// Pruefe, ob die Preisgrenzen der Angebote vereinbar sind
			// Wenn nein, ueberspringe dieses Angebot
			double minPriceCompare = compareOffer.getMinPrice();
			double maxPriceCompare = compareOffer.getMaxPrice();
			if (maxPriceCompare < minPriceOffer || maxPriceOffer < minPriceCompare) {
				continue;
			}

			double[] valuesCompareOffer = compareOffer.getAggLoadprofile().getValues();
			double[] deviationPrediction = new double[numSlots];
			double sumDeviationPrediction = 0;
			for (int j = 0; j < numSlots; j++) {
				deviationPrediction[j] = deviationOfferPrediction[j] - valuesCompareOffer[j];
				sumDeviationPrediction += Math.abs(deviationPrediction[j]);
			}

			if (sumDeviationPrediction < sumDeviationMostImprovementPrediction) {
				offerMostImprovement = compareOffer;
				sumDeviationMostImprovementPrediction = sumDeviationPrediction;
			}

			// Fuege Komination der Angebote fuer listPossibleMatches zum Array
			// hinzu
			possibleMatchesOfDateOffer.add(possibleMatch);
		}

		// Pruefe, ob hinzufuegen der beiden Angebote mit geringster Abweichung
		// Annaeherung an Prognose verbessert oder um weniger als 5
		// verschlechtert
		System.out.println("Maximale Abweichung: " + maxDeviation);
		System.out.println("Tatsaechliche Abweichung von Prognose: " + sumDeviationMostImprovementPrediction);
		if (sumDeviationMostImprovementPrediction < sumDeviationCurrentPrediction
				|| sumDeviationMostImprovementPrediction < maxDeviation) {
			if (offer.equals(offerMostImprovement)) {
				// confirmOffer(offer, offer.getPrice());
			} else {
				matchFittingOffers(offer, offerMostImprovement);
				return true;
			}
		}

		// Fuege Array mit allen neuen Kombinationen zu listPossibleMatches
		// hinzu
		if (possibleMatchesOfDateOffer.size() != 0) {
			listPossibleMatches.put(DateTime.ToString(offer.getDate()), possibleMatchesOfDateOffer);
		}
		return false;
	}

	/**
	 * Gibt ein Angebot zurück
	 * 
	 * @param uuid
	 *            Angebots-ID
	 * @return Angebot
	 */
	public Offer getOfferDemand(UUID uuid) {
		Set<String> demands = demand.keySet();
		for (String current : demands) {
			ArrayList<Offer> demandOffers = demand.get(current);
			for (Offer offer : demandOffers) {
				if (offer.getUUID() == uuid) {
					return offer;
				} else {
					System.out.println("Übergeben: " + uuid);
					System.out.println("Aktuell:   " + offer.getUUID());
				}
			}
		}
		return null;
	}

	/**
	 * Liefert den aktuellen EEX-Preis
	 * 
	 * @return Aktuellen EEX-Preis
	 */
	public static double getEEXPrice() {
		return Marketplace.eexPrice;
	}

	public ArrayList<Negotiation> getNegotiations() {
		ArrayList<Negotiation> negotiations = new ArrayList<Negotiation>();
		Set<UUID> uuids = negotiatingOffers.keySet();
		for (UUID uuid : uuids) {
			Negotiation negotiation = negotiatingOffers.get(uuid);
			negotiations.add(negotiation);
		}
		return negotiations;
	}

	public HashMap<UUID, Negotiation> getNegotiationsMap() {
		return negotiatingOffers;
	}

	public ArrayList<PossibleMatch> getPossibleMatches(GregorianCalendar date) {
		return listPossibleMatches.get(DateTime.ToString(date));
	}

	/**
	 * Liefert die Summe aller auf dem Marktplatz vorhandenen Lastprofile eines
	 * Zeitraums
	 * 
	 * @param date
	 *            Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller auf dem Marktplatz vorhandenen
	 *         Lastprofile des Zeitraums
	 */
	public double[] getSumAllOffers(GregorianCalendar date) {
		return sumLoadprofilesAllOffers.get(DateTime.ToString(date));
	}

	/**
	 * Liefert die Summe aller bestaetigten Lastprofile eines Zeitraums
	 * 
	 * @param date
	 *            Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller bestaetigten Lastprofile des Zeitraums
	 */
	public double[] getSumConfirmedOffers(GregorianCalendar date) {
		return sumLoadprofilesConfirmedOffers.get(DateTime.ToString(date));
	}

	public Offer getOfferSupply(UUID uuid) {
		Set<String> supplies = supply.keySet();
		for (String current : supplies) {
			ArrayList<Offer> supplyOffers = supply.get(current);
			for (Offer offer : supplyOffers) {
				if (offer.getUUID() == uuid) {
					return offer;
				}
			}
		}
		return null;
	}

	/**
	 * Gibt zurueck, ob fuer die Hoehe der Abweichung von der Prognose eine
	 * Anpassung erfragt oder der reBAP bezahlt werden soll. Hierfür sollen an
	 * dieser Stelle die nach der Seminararbeit "Make or Buy im Bilanzkreis"
	 * errechneten Grenzen für den Fremd- bzw. Eigenausgleich eingefügt werden.
	 * Anhand von diesen kann dann entscheiden werden, ob ein Ausgleich durch
	 * die intelligenten Geräte stattfinden soll oder nicht.
	 * 
	 * @param sumDeviationAll
	 *            Hoehe der Abweichung von der Prognose
	 * @return true, wenn eine Anpassung erfragt werden soll. false, wenn der
	 *         reBAP bezahlt werden soll.
	 */
	public boolean[] make(double[] deviation) {
		/*
		 * Berechnung nach Seminararbeit kann hier noch eingefügt werden. Die
		 * Grenzen werden auf 0 gesetzt, sodass immer für einen Ausgleich durch
		 * die intelligenten Geräte entschieden wird.
		 */
		double borderNegativeComp = 0;
		double borderPositiveComp = 0;

		boolean[] make = new boolean[numSlots];
		for (int i = 0; i < numSlots; i++) {
			double currentValue = deviation[i];
			make[i] = (currentValue > 0 && currentValue > borderNegativeComp
					|| currentValue < 0 && currentValue < borderPositiveComp);
		}

		return make;
	}

	/**
	 * Bestaetigt alle Angebote des naechsten Slots. In Abhaengigkeit von der
	 * Gesamtabweichung der Angebote von der Prognose werden Strafen verhaengt
	 * oder eine Anpassung erfragt.
	 */
	private void matchNextSlot() {
		System.out.println("MatchNextSlot");
		
		if ((demand.get(DateTime.ToString(nextSlot)) == null) && (supply.get(DateTime.ToString(nextSlot)) == null)) {
			System.out.println("Es liegen keine Angebote für nextSlot vor.");
			if (DateTime.now().after(nextSlot)) {
				nextSlot.add(Calendar.HOUR_OF_DAY, 1);
				System.out.println("Next Slot wurde hochgezählt.");
			}
			return;
		}
		
		double[] deviationAll = chargeDeviationAll(nextSlot);
		double sumDeviationAll = 0;
		for (int i = 0; i < numSlots; i++) {
			sumDeviationAll += Math.abs(deviationAll[i]);
		}

		System.out.println("Abweichung ohne Anpassungen: ");
		System.out.println(
				"[" + deviationAll[0] + "][" + deviationAll[1] + "][" + deviationAll[2] + "][" + deviationAll[3] + "]");
		System.out.println("Summe: " + sumDeviationAll);
		/*
		 * if (deviationAll.equals(currentPrediction)) { // Bestaetige alle noch
		 * uebrigen Angebote zum selben Preis // Eine Strafe von 10% muss
		 * gezahlt werden. confirmAllRemainingOffersWithOnePrice(nextSlot, 4); }
		 */

		// Fuehre zunächst alle Angebote, welche die Abweichung von der Prognose
		// nicht signifikant erhöhen, zusammen
		// matchAllGoodOffers(nextSlot);

		// Prüfe, ob eine Anpassung der intelligenten Geräte gefragt und nötigt
		// ist. Wenn ja, führe sie durch
		boolean anyMake = false;
		boolean[] make = make(deviationAll);
		ArrayList<Offer> remainingOffers = new ArrayList<Offer>();
		for (int i = 0; i < numSlots; i++) {
			anyMake = anyMake || make[i];
		}
		if (anyMake && sumDeviationAll != 0) {
			// matchAllGoodOffers(nextSlot);

			remainingOffers = (ArrayList<Offer>) demand.get(DateTime.ToString(nextSlot)).clone();
			ArrayList<Offer> supplies = (ArrayList<Offer>) supply.get(DateTime.ToString(nextSlot)).clone();

			if (remainingOffers.size() != 0) {
				if (supplies != null) {
					for (Offer supply : supplies) {
						remainingOffers.add(supply);
					}
				}
			} else {
				if (supplies != null) {
					remainingOffers = supplies;
				} else {
					return;
				}
			}

			// Sortiere verbleibende Angebote absteigend nach Betrag des
			// Volumens
			Collections.sort(remainingOffers);

			// Frage verbliebenen Angebote der Reihe nach
			// nach Ausgleichsmoeglichkeiten
			System.out.println("Frage insgesamt: " +remainingOffers.size());
			for (Offer currentOffer : remainingOffers) {
				// Berechne die Änderung, die angefragt werden soll
				double[] changeRequested = new double[numSlots];
				for (int i = 0; i < numSlots; i++) {
					if (make[i]) {
						changeRequested[i] = -deviationAll[i];
					} else {
						changeRequested[i] = 0;
					}
				}
				System.out.println("Erfragte Änderungen: " +valuesToString(changeRequested));

				ChangeRequestLoadprofile cr = new ChangeRequestLoadprofile(currentOffer.getUUID(), changeRequested,
						currentOffer.getDate());
				UUID author = currentOffer.getAuthor();

				// Versende Anfrage an Author
				API<ChangeRequestLoadprofile, Void> api = new API<ChangeRequestLoadprofile, Void>(Void.class);
				api.consumers(author).offers(currentOffer.getUUID()).changeRequestMarketplace();
				api.call(this, HttpMethod.POST, cr);

				// Warte, bis eine Antwort vom Consumer eingetroffen ist
				UUID uuid = UUID.randomUUID();
				double[] change = { 0, 1, 2, 3 };
				GregorianCalendar time = DateTime.now();
				ChangeRequestLoadprofile testCR = new ChangeRequestLoadprofile(uuid, change, time);

				if (currentAnswer == null) {
					currentAnswer = testCR;
				}

				System.out.println("Gleich: " + currentAnswer.equals(testCR));
				synchronized (currentAnswer) {
					if (currentAnswer.equals(testCR)) {
						System.out.println("Noch keine Antwort erhalten");
					}
					while (currentAnswer.equals(testCR)) {
						try {
							currentAnswer.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				System.out.println("Antwort erhalten");
				double[] receivedChanges = currentAnswer.getChange();
				System.out.println("Werte Antwort" + valuesToString(receivedChanges));

				// Berechne die neue Abweichung, wenn die uebergebene
				// Aenderung beachtet wird.
				double[] possibleChange = currentAnswer.getChange();
				double sumPossibleChange = 0;
				double[] possibleChangeDeviation = deviationAll.clone();
				for (int i = 0; i < numSlots; i++) {
					possibleChangeDeviation[i] += possibleChange[i];
					sumPossibleChange += Math.abs(possibleChange[i]);
				}
				System.out.println("Alte Abweichung" + valuesToString(deviationAll));
				System.out.println("Neue Abweichung" + valuesToString(possibleChangeDeviation));

				// Pruefe, für die Viertelstundenwerte, ob eine Änderung
				// erwünscht war und eine Verbesserung eintritt oder keine
				// Änderung gewünscht war und auch keine vorgenommen wird
				boolean goodChanges = sumPossibleChange != 0;
				for (int i = 0; i < numSlots; i++) {
					if (make[i]) {
						goodChanges = goodChanges && Math.abs(possibleChangeDeviation[i]) <= Math.abs(deviationAll[i]);
					} else {
						goodChanges = goodChanges && possibleChange[i] == 0;
					}
				}
				System.out.println("goodChanges: " +goodChanges);

				if (goodChanges) {
					// Zusage fuer ChangeRequest an Consumer
					API<ChangeRequestLoadprofile, Void> api1 = new API<ChangeRequestLoadprofile, Void>(Void.class);
					api1.consumers(author).offers(currentOffer.getUUID()).changeRequest().confirm();
					api1.call(this, HttpMethod.GET, cr);
					
					// Füge Änderungen zum Angebot hinzu
					currentOffer.setChanges(possibleChange);

					// Bestaetige das Angebot zum uebergebenen Preis
					confirmOffer(currentOffer, currentAnswer.getPrice(), ConfirmedOffer.Type.CHANGED);

					// Aktualisiere die Gesamtabweichung aller bestaetigter
					// Angebote
					// und deren Summe
					deviationAll = chargeDeviationAll(nextSlot);
					sumDeviationAll = 0;
					for (int i = 0; i < numSlots; i++) {
						sumDeviationAll += Math.abs(deviationAll[i]);
					}
					// Wenn die Summe = 0 gibt es keine Abweichung mehr und die
					// aenderungsnachfragen koennen beendet werden
					if (sumDeviationAll == 0) {
						break;
					}
				} else {
					// Absage fuer ChangeRequest an Consumer
					System.out.println("Sende Absage für ChangeRequest an Consumer");
					API<ChangeRequestLoadprofile, Void> api1 = new API<ChangeRequestLoadprofile, Void>(Void.class);
					api1.consumers(author).offers(currentOffer.getUUID()).changeRequest().decline();
					api1.call(this, HttpMethod.GET, cr);
				}
			}
			System.out.println("Abweichung nach Anpassungen: ");
			System.out.println("[" + deviationAll[0] + "][" + deviationAll[1] + "][" + deviationAll[2] + "]["
					+ deviationAll[3] + "]");
			System.out.println("Summe: " + sumDeviationAll);
		}

		// Bestaetige alle noch uebrigen Angebote zum selben Preis
		if (remainingOffers.size() != 0) {
			System.out.println("Confirm all remaining Offers with one Price");
			confirmAllRemainingOffersWithOnePrice(nextSlot, 4, true);
		}
		
		
		// Gib alle bestätigten Angebote aus
		ArrayList<ConfirmedOffer> justConfirmed = confirmedOffers.get(DateTime.ToString(nextSlot));
		confirmedOffersToString(justConfirmed, nextSlot);

		// Zaehle Variable nextSlot um eins hoch
		nextSlot.add(Calendar.HOUR_OF_DAY, 1);
		System.out.println("NextSlot: " + DateTime.ToString(nextSlot));
	}

	private String valuesToString(double[] values) {
		String s = ": ";
		for (int i = 0; i < values.length; i++) {
			s = s + "[" + values[i] + "]";
		}
		return s;
	}

	/**
	 * Fuehrt alle Angebote eines Zeitraums zusammen, die die Abweichung von der
	 * Prognose verbessern.
	 * 
	 * @param slot
	 *            Start des Zeitraums, fuer den die Angebote zusammengefuehrt
	 *            werden sollen
	 */
	private void matchAllGoodOffers(GregorianCalendar date) {
		// Hole die Liste aller moeglichen Matches
		ArrayList<PossibleMatch> possibleMatches = listPossibleMatches.get(DateTime.ToString(date));
		// Erstelle eine neue Liste, in welcher alle gerade zusammengefuegten
		// Angebote gespeichert werden
		ArrayList<Offer> offersJustMatched = new ArrayList<Offer>();

		// Gehe die Liste aller moeglichen Matches durch und fuehre die guten
		// zusammen
		for (PossibleMatch possibleMatch : possibleMatches) {
			Offer[] offers = possibleMatch.getOffers();
			// Wenn eines der Angebote in offersJustMatched ist, wurde es
			// bereits
			// bei einem vorherigen Schleifendurchlauf zusammengefuehrt und
			// bestaetigt und steht nicht mehr zur Verfuegung
			if (offersJustMatched.contains(offers[0]) || offersJustMatched.contains(offers[1])) {
				continue;
			}
			double[] loadprofile = possibleMatch.getValuesAggLoadprofile();
			double[] predictionCurrent = prediction.get(DateTime.ToString(possibleMatch.getDate()));
			double[] sumLPCurrent = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(possibleMatch.getDate()));
			double[] deviationOld = chargeDeviationConfirmed(possibleMatch.getDate());
			double[] deviationNew = new double[numSlots];
			double sumDeviationNew = 0;
			double sumDeviationOld = 0;
			// double volumePrediction = 0;
			for (int i = 0; i < numSlots; i++) {
				deviationNew[i] = predictionCurrent[i] - sumLPCurrent[i] - loadprofile[i];
				sumDeviationNew += Math.abs(deviationNew[i]);
				sumDeviationOld += Math.abs(deviationOld[i]);
				// volumePrediction += Math.abs(predictionCurrent[i]);
			}
			if (sumDeviationNew < sumDeviationOld /*
													 * || sumDeviationNew <
													 * volumePrediction
													 */) {
				boolean matched = matchFittingOffers(offers[0], offers[1]);
				if (matched) {
					offersJustMatched.add(offers[0]);
					offersJustMatched.add(offers[1]);
				}
			}
		}
	}

	/**
	 * Fuehrt Angebote zusammen, die die Annaeherung an die Prognose verbessern
	 * bzw. nicht sehr viel verschlechtern. Die Angebote werden vom Marktplatz
	 * entfernt. Passt der Preis, so werden die Angebote direkt bestaetigt.
	 * Passt der Preis nicht, wird eine Verhandlung mit den beiden Angeboten
	 * gestartet.
	 * 
	 * @param offer1
	 *            Erstes Angebot, das zusammengefuehrt werden soll
	 * @param offer2
	 *            Zweites Angebot, das zusammengefuehrt werden soll
	 */
	private boolean matchFittingOffers(Offer offer1, Offer offer2) {
		// Prüfe, dass Zusammenführen der Angebote auch möglich, da sie die
		// gleiche Startzeit haben und sich die Preisgrenzen überschneiden
		if (!offer1.getDate().equals(offer2.getDate()) || offer1.getMaxPrice() < offer2.getMinPrice()
				|| offer2.getMaxPrice() < offer1.getMinPrice()) {
			return false;
		}

		double[] valuesOffer1 = offer1.getAggLoadprofile().getValues();
		double[] valuesOffer2 = offer2.getAggLoadprofile().getValues();
		double sumOffer1 = 0;
		double sumOffer2 = 0;

		for (int i = 0; i < 4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}

		// Pruefe, ob Preise schon zusammenpassen bzw. Anpassung von Marktplatz
		// moeglich ist, da es nur zu Verbesserungen fuer die Consumer fuehrt
		boolean pricesFit = (sumOffer1 * offer1.getPriceSugg() + sumOffer2 * offer2.getPriceSugg()) == 0;

		// Bestätige bestehende Preise, da Preise bereits zusammenpassen
		if (pricesFit) {
			// Lege zusammengefuehrte Angebote und Preise in der Historie ab
			MatchedOffers matched = new MatchedOffers(offer1.getPriceSugg(), offer2.getPriceSugg(), offer1, offer2);
			ArrayList<MatchedOffers> array = matchedOffers.get(DateTime.ToString(offer1.getDate()));
			if (array == null) {
				array = new ArrayList<MatchedOffers>();
			}
			array.add(matched);
			matchedOffers.put(DateTime.ToString(offer1.getDate()), array);

			// Schicke Bestaetigung beider Angebote an Consumer
			confirmOffer(offer1, offer1.getPriceSugg(), ConfirmedOffer.Type.MATCHED);
			confirmOffer(offer2, offer2.getPriceSugg(), ConfirmedOffer.Type.MATCHED);
		}

		// Erstelle Angebot, falls Preise nicht passen, aber Preisfindung
		// generell möglich ist
		if (!pricesFit) {
			// Entferne Angebote von Marktplatz
			System.out.println("matchFittingOffers entfernt Angebote");
			removeOffer(offer1.getUUID(), false);
			removeOffer(offer2.getUUID(), false);

			// Erstelle neue Verhandlung und speichere Verhandlung unter
			// negotiatingOffers ab
			Negotiation negotiation = new Negotiation(offer1, offer2, sumOffer1, sumOffer2);
			negotiation.negotiationToString();
			negotiatingOffers.put(negotiation.getUUID(), negotiation);
		}
		return true;
	}

	/**
	 * Enthaelt neues Angebot. Gibt es auf dem Marktplatz ein bereits passendes
	 * Angebot, werden die Angebote direkt zusammengefuehrt und bestaetigt. Gibt
	 * es auf dem Marktplatz kein passendes Angebot, wird das neue Angebot als
	 * Erzeuger- oder Verbraucherangebot in der jeweiligen Map abgelegt.
	 * 
	 * @param offer
	 *            Neues Angebot, das am Markt teilnehmen will
	 */
	public void receiveOffer(Offer offer) {
		System.out.println("Angebot trifft auf Marktplatz ein.");
		GregorianCalendar dateGreg = offer.getDate();
		String date = DateTime.ToString(dateGreg);

		// Berechne die Summe des Lastprofils
		double[] valuesLoadprofile = offer.getAggLoadprofile().getValues();
		double sumLoadprofile = 0;
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
		}

		// currentDate gibt an, für welche Stunde noch Angebot entgegengenommen
		// werden.
		// Wenn die Minuten der aktuellen Stunde <= minuteOfSecondPhase sind,
		// werden noch Angebote für die aktuelle Stunde angenommen, ansonsten
		// erst wieder für die nächste Stunde und später
		GregorianCalendar currentDate = DateTime.now();
		if (currentDate.get(Calendar.HOUR_OF_DAY) == dateGreg.get(Calendar.HOUR_OF_DAY)) {
			int minutes = currentDate.get(Calendar.MINUTE);
			int slot = (int) Math.floor(minutes / 15);
			double sumUntilSlot = 0;

			for (int i = 0; i <= slot; i++) {
				sumUntilSlot += Math.abs(valuesLoadprofile[i]);
			}

			if (sumUntilSlot != 0) {
				Log.e(uuid, "Angebot kommt zu spät und wird zum Einheitspreis bestätigt");
				double[] price = unitPricesWithPenalty.get(DateTime.ToString(dateGreg));
				double confirmPrice = 0;
				if (sumLoadprofile >= 0) {
					if (price == null || price[1] == 0) {
						confirmPrice = -sumUntilSlot * offer.getPriceSugg() * 0.1;
					}
					confirmPrice = price[1];
				} else {
					if (price == null || price[0] == 0) {
						confirmPrice = eexPrice * 1.1;
					}
					confirmPrice = price[0];
				}
				confirmOffer(offer, confirmPrice, ConfirmedOffer.Type.UNITPRICE);
				return;
			}
		}

		// Addiere die Summe des Lastprofils zu der Summe aller
		// Lastprofile
		double[] sumAllOffers = sumLoadprofilesAllOffers.get(date);
		if (sumAllOffers == null) {
			sumAllOffers = new double[numSlots];
		}
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
			sumAllOffers[i] += valuesLoadprofile[i];
		}
		sumLoadprofilesAllOffers.put(date, sumAllOffers);

		if (sumLoadprofile >= 0) {
			if (!findFittingOffer(offer, true)) {
				ArrayList<Offer> offers = supply.get(date);
				if (offers == null) {
					offers = new ArrayList<Offer>();
				}
				offers.add(offer);
				Collections.sort(offers, new sortOfferPriceSupplyLowToHigh());
				supply.put(date, offers);
			}
		} else {
			if (!findFittingOffer(offer, false)) {
				ArrayList<Offer> offers = demand.get(date);
				if (offers == null) {
					offers = new ArrayList<Offer>();
				}
				offers.add(offer);
				Collections.sort(offers, new sortOfferPriceDemandHighToLow());
				demand.put(date, offers);
			}
		}
		marketplaceToString();
	}

	public void receiveAnswerChangeRequestLoadprofile(ChangeRequestLoadprofile cr) {
		currentAnswer = cr;
		//currentAnswer.notify();
	}

	/**
	 * Entfernt das uebergebene Angebot vom Marktplatz
	 * 
	 * @param offer
	 *            Angebot, das entfernt werden soll
	 * @param confirmed
	 *            Entferne Summe des Lastprofiles von der Gesamtsumme aller //
	 *            Lastprofile
	 */
	public void removeOffer(UUID offer, boolean confirmed) {
		System.out.println("Angebot wird entfernt: " + offer);

		Offer removeOffer = getOfferDemand(offer);
		boolean isSupply;
		if (removeOffer == null) {
			removeOffer = getOfferSupply(offer);
			isSupply = true;
		} else {
			isSupply = false;
		}
		if (removeOffer == null) {
			Set<String> allDemands = demand.keySet();
			if (allDemands != null) {
				System.out.println("Demands:");
				for (String s : allDemands) {
					System.out.println(s);
					ArrayList<Offer> list = demand.get(s);
					for (Offer o : list) {
						System.out.println(o.getUUID());
					}
				}
			} else {
				System.out.println("Kein Demand");
			}

			Set<String> allSupplies = supply.keySet();
			if (allSupplies != null) {
				System.out.println("Supplies:");
				for (String s : allSupplies) {
					System.out.println(s);
					ArrayList<Offer> list = supply.get(s);
					for (Offer o : list) {
						System.out.println(o.getUUID());
					}
				}
			} else {
				System.out.println("Kein Supply");
			}

			System.out.println("RemoveOffer ist null");
			return;
		}
		System.out.println("Supply: " + isSupply);

		double[] valuesO = removeOffer.getAggLoadprofile().getValues();
		System.out.println("Werte: [" + valuesO[0] + "][" + valuesO[1] + "][" + valuesO[2] + "][" + valuesO[3] + "]");

		String date = DateTime.ToString(removeOffer.getDate());

		if (!confirmed) {
			// Entferne Summe des Lastprofils von der Gesamtsumme aller
			// Lastprofile
			double[] values = removeOffer.getAggLoadprofile().getValues();
			double[] valuesAllLoadprofiles = sumLoadprofilesAllOffers.get(date);
			System.out.println("Summe Lastprofile zuvor: [" + valuesAllLoadprofiles[0] + "][" + valuesAllLoadprofiles[1]
					+ "][" + valuesAllLoadprofiles[2] + "][" + valuesAllLoadprofiles[3] + "]");
			for (int i = 0; i < numSlots; i++) {
				valuesAllLoadprofiles[i] -= values[i];
			}
			System.out
					.println("Summe Lastprofile danach: [" + valuesAllLoadprofiles[0] + "][" + valuesAllLoadprofiles[1]
							+ "][" + valuesAllLoadprofiles[2] + "][" + valuesAllLoadprofiles[3] + "]");
			sumLoadprofilesAllOffers.put(date, valuesAllLoadprofiles);
		}

		if (isSupply) {
			ArrayList<Offer> supplies = supply.get(date);
			System.out.println("Size Supplies vor Entfernen: " + supplies.size());
			supplies.remove(removeOffer);
			System.out.println("Size Supplies nach Entfernen: " + supplies.size());
			supply.put(date, supplies);
		} else {
			ArrayList<Offer> demands = demand.get(date);
			System.out.println("Size Demands vor Entfernen: " + demands.size());
			demands.remove(removeOffer);
			System.out.println("Size Demands nach Entfernen: " + demands.size());
			demand.put(date, demands);
		}

		// Entferne Angebote aus listPossibleMatches
		removeFromPossibleMatches(removeOffer);

		// Entferne Angebot aus blackListPossibleMatches
		removeFromBlackListPossibleMatches(removeOffer);
	}

	/**
	 * Entfernt alle moeglichen Kombination von der Black List, an denen das
	 * uebergeben Angebot beteiligt ist.
	 * 
	 * @param offer
	 *            Angebot, dessen Kombinationen entfernt werden sollen
	 */
	private void removeFromBlackListPossibleMatches(Offer offer) {
		String key = DateTime.ToString(offer.getDate());
		ArrayList<PossibleMatch> oldBlackList = blackListPossibleMatches.get(key);
		if (oldBlackList == null) {
			return;
		}
		ArrayList<PossibleMatch> newBlackList = new ArrayList<PossibleMatch>();
		for (PossibleMatch current : oldBlackList) {
			Offer[] offers = current.getOffers();
			if (!(offers[0].equals(offer) || offers[1].equals(offer))) {
				newBlackList.add(current);
			}
		}
		if (newBlackList.size() == 0) {
			blackListPossibleMatches.remove(key);
		} else {
			blackListPossibleMatches.put(key, newBlackList);
		}
	}

	/**
	 * Entfernt alle moeglichen Kombination aus possibleMatches, an denen das
	 * uebergeben Angebot beteiligt ist.
	 * 
	 * @param offer
	 *            Angebot, dessen Kombinationen entfernt werden sollen
	 */
	private void removeFromPossibleMatches(Offer offer) {
		String key = DateTime.ToString(offer.getDate());
		ArrayList<PossibleMatch> oldPossibleMatches = listPossibleMatches.get(key);
		if (oldPossibleMatches == null) {
			return;
		}
		System.out.println("Anzahl Possible Matches davor: " + oldPossibleMatches.size());

		ArrayList<PossibleMatch> newPossibleMatches = new ArrayList<PossibleMatch>();
		for (PossibleMatch current : oldPossibleMatches) {
			Offer[] offers = current.getOffers();
			if (!(offers[0].equals(offer) || offers[1].equals(offer))) {
				newPossibleMatches.add(current);
			}
		}
		if (newPossibleMatches.size() == 0) {
			listPossibleMatches.remove(key);
		} else {
			listPossibleMatches.put(key, newPossibleMatches);
		}
		System.out.println("Anzahl Possible Matches danach: " + oldPossibleMatches.size());
	}

	/**
	 * Liefert den Status des Marktplatzes
	 * 
	 * @return Map mit der Anzahl an Verbraucherangeboten, der Anzahl an
	 *         Erzeugerangeboten und Hoehe des EEX-Preises
	 */
	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("numberOfDemands", demand.size());
		map.put("numberOfSupplies", supply.size());
		map.put("numberOfNegotiations", negotiatingOffers.size());
		map.put("numberOfMatches", matchedOffers.size());
		map.put("eexPrice", getEEXPrice());
		map.put("allDeviation", sumLoadprofilesAllOffers);
		map.put("matchedDeviation", sumLoadprofilesConfirmedOffers);

		return map;
	}

	/**
	 * Ruft die Methode des BKV auf.
	 */
	public void ping() {
		System.out.println(DateTime.ToString(DateTime.now()));
		BKV();
	}

	public String toString() {
		String s = "\nMarketplace: \nnumberOfDemands: " + demand.size() + " numberOfSupplies: " + supply.size();
		s = s + " numberOfNegotiations: " + negotiatingOffers.size() + " numberOfMatches: " + matchedOffers.size();
		s = s + " numberOfPossibleMatches: " + listPossibleMatches.size() + " numberOfBlackList: "
				+ blackListPossibleMatches.size();
		return s;
	}

	public void marketplaceToString() {
		System.out.println("\nMarketplace: \nnumberOfDemands: " + demand.size() + " numberOfSupplies: " + supply.size()
				+ " numberOfNegotiations: " + negotiatingOffers.size() + " numberOfMatches: " + matchedOffers.size()
				+ " numberOfPossibleMatches: " + listPossibleMatches.size() + " numberOfBlackList: "
				+ blackListPossibleMatches.size());
		allOffersToString();
		possibleMatchesToString();
		blackListPossibleMatches.toString();
		matchedToString();
	}

	/**
	 * Gibt alle aktuell auf dem Marktplatz vorhandenen Angebote sortiert nach
	 * Demand und Supply auf der Console aus.
	 */
	public void allOffersToString() {
		Set<String> demandSet = demand.keySet();
		if (demandSet.size() == 0) {
			System.out.println("\nNo Demand");
		} else {
			System.out.println("\nDemand:");
			for (String date : demandSet) {
				System.out.println(date);
				ArrayList<Offer> offersAtDate = demand.get(date);
				for (Offer offer : offersAtDate) {
					double[] values = offer.getAggLoadprofile().getValues();
					System.out.println("	Price: " + offer.getPriceSugg() + " Values: " + valuesToString(values));
				}
			}
		}

		Set<String> supplySet = supply.keySet();
		if (supplySet.size() == 0) {
			System.out.println("No Supply");
		} else {
			System.out.println("Supply:");
			for (String date : supplySet) {
				System.out.println(date);
				ArrayList<Offer> offersAtDate = supply.get(date);
				for (Offer offer : offersAtDate) {
					double[] values = offer.getAggLoadprofile().getValues();
					System.out.println("	Price: " + offer.getPriceSugg() + " Values: " + valuesToString(values));
				}
			}
		}
	}

	/**
	 * Gibt alle Matches von der Black List auf der Console aus.
	 */
	public void blackListPossibleMatchesToString() {
		Set<String> date = blackListPossibleMatches.keySet();
		if (date.size() == 0) {
			System.out.println("\nNo Black List Possible Matches");
			return;
		}
		System.out.println("\nBlack List Possible Matches:");
		for (String currentDate : date) {
			System.out.println("Zeit: " + currentDate);
			ArrayList<PossibleMatch> possibleMatch = blackListPossibleMatches.get(currentDate);
			if (possibleMatch == null) {
				System.out.println("No Black List Possible Matches.");
			}
			for (int i = 0; i < possibleMatch.size(); i++) {
				System.out.println("	" + (i + 1) + ". : " + possibleMatch.get(i).toString());
			}
		}
	}
	
	
	/**
	 * Gibt die übergebene Liste von bestätigten Angeboten zur übergebenen Zeit aus.
	 * @param list	Liste der bestätigten Angebote, die ausgegeben werden soll
	 * @param time	Zeit, zu der alle Angebote der Liste beginnen
	 */
	public void confirmedOffersToString(ArrayList<ConfirmedOffer> list, GregorianCalendar time) {
		System.out.println("Confirmed Offers at " + DateTime.ToString(time) +  ":");
		double countMatched = 0, countChanged = 0, countUnitPrice = 0;
		for (ConfirmedOffer offer: list) {
			// Zähle die jeweilige Variable hoch
			if (offer.getType().equals(ConfirmedOffer.Type.MATCHED)) {
				countMatched++;
			}
			if (offer.getType().equals(ConfirmedOffer.Type.UNITPRICE)) {
				countUnitPrice++;
			}
			
			// Berechne den Gesamtpreis des Angebots
			double totalPrice = offer.getOffer().getSumAggLoadprofile()*offer.getPriceConfirmed();
			
					
			// Gebe das aktuelle Angebot aus
			String s = offer.getTypeString() + ", Total Price: " + totalPrice;
			double[] values = offer.getOffer().getAggLoadprofile().getValues();
			s = s + ", Values: " + valuesToString(values);
			if (offer.getType().equals(ConfirmedOffer.Type.CHANGED)) {
				countChanged++;
				s = s + ", Changes: " +valuesToString(offer.getOffer().getChanges());
			}
			s = s + ", Price per kWh: " +offer.getPriceConfirmed();
			System.out.println(s);
		}
		double sum = countMatched + countChanged + countUnitPrice;
		double percentageMatched = Math.round(1000.00 * (countMatched/sum)) / 10.0;
		double percentageChanged = Math.round(1000.00 * (countChanged/sum)) / 10.0;
		double percentageUnitPrice =  Math.round(1000.00 * (countUnitPrice/sum)) / 10.0;
		System.out.println("\nVon " +sum+ " bestätigten Angeboten sind " +countMatched+ ", also " +percentageMatched+ "% gematcht.");
		System.out.println("Von " +sum+ " bestätigten Angeboten sind " +countChanged+ ", also " +percentageChanged+ "% geändert.");
		System.out.println("Von " +sum+ " bestätigten Angeboten sind " +countUnitPrice+ ", also " +percentageUnitPrice+ "% zum Einheitspreis bestätigt.");
		System.out.println("Es wurden also " +percentageMatched+ "% der Angebote ohne Eingreifen des BKV zusammengeführt.");
	}

	/**
	 * Gibt alle zusammengefuehrten Angebote auf der Console aus.
	 */
	public void matchedToString() {
		Set<String> set = matchedOffers.keySet();
		if (set.size() == 0) {
			System.out.println("\nNo Matched Offers");
			return;
		}
		System.out.println("\nMatched Offers:");
		for (String neu : set) {
			System.out.println(neu + ":");
			ArrayList<MatchedOffers> matched = matchedOffers.get(neu);
			System.out.println("Anzahl matched: " + matched.size());
			for (int i = 0; i < matched.size(); i++) {
				System.out.println("	" + (i + 1) + ": " + matched.get(i).matchedOffersToString());
			}
		}
	}

	/**
	 * Gibt alle moeglichen Zusammenfuehrungen auf der Console aus.
	 */
	public void possibleMatchesToString() {
		Set<String> date = listPossibleMatches.keySet();
		if (date.size() == 0) {
			System.out.println("\nNo Possible Matches");
			return;
		}
		System.out.println("\nPossible Matches:");
		for (String currentDate : date) {
			System.out.println("Zeit: " + currentDate);
			ArrayList<PossibleMatch> possibleMatch = listPossibleMatches.get(currentDate);
			if (possibleMatch == null) {
				System.out.println("No Possible Matches.");
			}
			for (int i = 0; i < possibleMatch.size(); i++) {
				System.out.println("	" + (i + 1) + ". : " + possibleMatch.get(i).toString());
			}
		}
	}

	public ResponseEntity<double[]> getPrediction() {
		String dateString = DateTime.ToString(DateTime.currentTimeSlot());

		return new ResponseBuilder<double[]>(this).body(prediction.get(dateString)).build();
	}

	public ResponseEntity<Offer[]> getOffers(int count) {
		if (count < 0) {
			// abfangen falscher negativer werte
			count = 0;
		}

		String dateString = DateTime.ToString(DateTime.currentTimeSlot());

		if (!supply.containsKey(dateString) || supply.get(dateString) == null) {
			return new ResponseBuilder<Offer[]>(this).body(new Offer[] { new Offer(uuid,
					new Loadprofile(prediction.get(dateString), DateTime.currentTimeSlot(), Loadprofile.Type.MIXED)) })
					.build();
		}

		count = Math.min(count, supply.get(dateString).size());

		Offer[] list = new Offer[count];

		for (int i = 0; i < count; i++) {
			list[i] = supply.get(dateString).get(i);
		}

		return new ResponseBuilder<Offer[]>(this).body(list).build();
	}

	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Gibt die Angebote von Verbrauchern fuer das uebergebene Datum zurueck,
	 * die bereit sind am meisten zu zahlen.
	 * 
	 * @param date
	 *            Datum, fuer welches die Angebote sein sollen
	 * @param number
	 *            Anzahl an Angeboten, die angefragt wird
	 * @return Liste aller teuersten Angebote
	 */
	// private ArrayList<Offer> getMostExpensiveDemandOffer(GregorianCalendar
	// date, int number) {
	// ArrayList<Offer> allDemandsAtDate = demand.get(date);
	// if (allDemandsAtDate.size() < number) {
	// number = allDemandsAtDate.size();
	// }
	// ArrayList<Offer> mostExpensive = new ArrayList<Offer>();
	// for (int i = 0; i < number; i++) {
	// mostExpensive.add(allDemandsAtDate.get(i));
	// }
	// return mostExpensive;
	// }

	/**
	 * Ermögicht die Suche nach Angeboten
	 * 
	 * @param params
	 *            Suchparameter
	 * @return Liste an eingeschlossenen Angeboten
	 */
	public ResponseEntity<Offer[]> search(SearchParams params) {
		String date = DateTime.ToString(params.getDate());

		ArrayList<Offer> result = new ArrayList<Offer>();

		for (Offer o : demand.getOrDefault(date, new ArrayList<Offer>())) {
			if (o.getMinPrice() < params.getMinPrice()) {
				continue;
			}

			if (o.getMaxPrice() > params.getMaxPrice()) {
				continue;
			}

			result.add(o);
		}

		for (Offer o : supply.getOrDefault(date, new ArrayList<Offer>())) {
			if (o.getMinPrice() <= params.getMinPrice()) {
				continue;
			}

			if (o.getMaxPrice() >= params.getMaxPrice()) {
				continue;
			}

			result.add(o);
		}

		return new ResponseBuilder<Offer[]>(this).body(result.toArray(new Offer[result.size()])).build();
	}

	/**
	 * Gibt die Angebote von Erzeugern fuer das uebergebene Datum zurueck, die
	 * am wenigsten fuer den erzeugten Strom verlangen.
	 * 
	 * @param date
	 *            Datum fuer welches die Angebote sein sollen
	 * @param number
	 *            Anzahl an Angeboten, die angefragt wird
	 * @return Liste aller guenstigsten Angebote
	 */
	// private ArrayList<Offer> getCheapestSupplyOffer(GregorianCalendar date,
	// int number) {
	// ArrayList<Offer> allSuppliesAtDate = supply.get(date);
	// if (allSuppliesAtDate.size() < number) {
	// number = allSuppliesAtDate.size();
	// }
	// ArrayList<Offer> cheapest = new ArrayList<Offer>();
	// for (int i = 0; i < number; i++) {
	// cheapest.add(allSuppliesAtDate.get(i));
	// }
	// return cheapest;
	// }

}
