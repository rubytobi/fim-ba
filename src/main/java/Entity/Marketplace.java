package Entity;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import Packet.AnswerToOfferFromMarketplace;
import Packet.EndOfNegotiation;
import Packet.ChangeRequestLoadprofile;
import Util.MatchedOffers;
import Util.API;
import Util.DateTime;
import Util.Log;
import Util.PossibleMatch;
import Util.Negotiation;
import Util.sortOfferPriceSupplyLowToHigh;
import start.Application;
import Util.sortOfferPriceDemandHighToLow;

/**
 * Marktplatz, auf welchem alle Angebote eintreffen und zusammengefuehrt werden
 */
public class Marketplace {
	/**
	 * Map, die nach Startzeit alle schon erfolglos verhandelten
	 * Angebotskombinationen enthaelt
	 */
	private Map<String, ArrayList<PossibleMatch>> blackListPossibleMatches = new TreeMap<String, ArrayList<PossibleMatch>>();

	private ChangeRequestLoadprofile currentAnswer;

	/**
	 * Maps, die alle noch nicht zusammengefuehrten Angebote des Marktplatzes
	 * beinhaltet
	 */
	private Map<String, ArrayList<Offer>> demand = new TreeMap<String, ArrayList<Offer>>();
	/**
	 * Aktueller eexPreis
	 */
	private static final double eexPrice = 20;

	private static Marketplace instance = null;

	/**
	 * Map, die nach Startzeit alle moeglichen Angebotskombinationen enthaelt
	 */
	private Map<String, ArrayList<PossibleMatch>> listPossibleMatches = new TreeMap<String, ArrayList<PossibleMatch>>();

	/**
	 * Aktuell geduldete Abweichung. (5% von erwartetem Gesamtvolumen: 100)
	 */
	private double maxDeviation = 0.05 * 100;

	/**
	 * Map, die alle bisher zusammengefuehrten Angebote nach Zeitslot beinhaltet
	 */
	private Map<String, ArrayList<MatchedOffers>> matchedOffers = new TreeMap<String, ArrayList<MatchedOffers>>();

	/**
	 * Map, die alle Angebote beinhaltet, ueber deren Preis gerade mit den
	 * Consumern verhandelt wird
	 */
	private Map<UUID, Negotiation> negotiatingOffers = new TreeMap<UUID, Negotiation>();

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
	private Map<String, double[]> prediction = new TreeMap<String, double[]>();

	/**
	 * Map, die die Summe der Abweichungen aller zusammengefuehrter Angebote
	 * nach Zeitslot beinhaltet
	 */
	private Map<String, double[]> sumLoadprofilesConfirmedOffers = new TreeMap<String, double[]>();

	/**
	 * Map, die die Summe der Abweichungen aller auf dem Marktplatz
	 * verfuegbaren, aller gerade verhandelten und aller vom Marktplatz
	 * zusammengefuehrten Angebote nach Zeitslot beinhaltet
	 */
	private Map<String, double[]> sumLoadprofilesAllOffers = new TreeMap<String, double[]>();

	private Map<String, ArrayList<Offer>> supply = new TreeMap<String, ArrayList<Offer>>();

	private UUID uuid = UUID.randomUUID();

	/**
	 * Erstellt einen neuen Marktplatz mit Prognose = 0 fuer die naechsten 24h
	 */
	private Marketplace() {
		// Setzt die Vorhersage fuer die naechsten 24h = 0
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		double[] zeroValues = { 0, 0, 0, 0 };
		for (int i = 0; i < 24; i++) {
			prediction.put(DateTime.ToString(now), zeroValues);
			now.add(Calendar.HOUR_OF_DAY, 1);
		}
		this.nextSlot = now;
	}

	public static Marketplace instance() {
		if (instance == null) {
			instance = new Marketplace();
		}
		return instance;
	}

	/**
	 * ueberprueft, ob schon Minute 55 oder groeßer erreicht ist und macht ggf.
	 * den naechsten Slot.
	 * 
	 */
	public void BKV() {
		GregorianCalendar now = DateTime.now();
		if (now.get(Calendar.HOUR_OF_DAY) == nextSlot.get(Calendar.HOUR_OF_DAY) && now.get(Calendar.MINUTE) >= 55) {
			matchNextSlot();
		}

		// Berechne den Startzeitpunkt der als letztes gemachten Stunde
		GregorianCalendar slotLastMatched = (GregorianCalendar) nextSlot.clone();
		slotLastMatched.add(Calendar.HOUR_OF_DAY, -1);

		// Berechne aktuellen Slot
		int slot;
		if (now.get(Calendar.HOUR_OF_DAY) == slotLastMatched.get(Calendar.HOUR_OF_DAY)) {
			double minute = now.get(Calendar.MINUTE);
			slot = (int) Math.floor(minute / 15);
		} else {
			slot = 0;
		}

		// Fuehre zuerst alle guten Angebote zusammen und bestaetige dann alle
		// verbliebenen Angebote zu einem Einheitspreis
		matchAllGoodOffers(slotLastMatched);
		confirmAllRemainingOffersWithOnePrice(slotLastMatched, slot);
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
		double[] deviationAll = new double[numSlots];
		double[] currentPrediction = prediction.get(start);
		double[] currentConfirmed = sumLoadprofilesAllOffers.get(start);
		for (int i = 0; i < numSlots; i++) {
			deviationAll[i] = currentPrediction[i] - currentConfirmed[i];
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
	private void confirmAllRemainingOffersWithOnePrice(GregorianCalendar date, int slot) {
		String dateString = DateTime.ToString(date);
		// Hole alle Angebote zu dem uebergebenen Datum
		ArrayList<Offer> allDemandsAtDate = demand.get(dateString);
		ArrayList<Offer> allSuppliesAtDate = supply.get(dateString);
		ArrayList<Offer> toRemoveFromDemands = new ArrayList<Offer>();
		ArrayList<Offer> toRemoveFromSupplies = new ArrayList<Offer>();
		double volumeDemand = 0;
		double volumeSupply = 0;
		double sumPricesDemand = 0;
		double sumPricesSupply = 0;

		for (Offer currentOffer : allDemandsAtDate) {
			double sumUntilCurrentSlot = 0;
			// Berechne die Summe des Lastprofils einschließlich des aktuellen
			// Slots
			for (int i = 0; i == slot; i++) {
				sumUntilCurrentSlot += Math.abs(currentOffer.getAggLoadprofile().getValues()[i]);
			}

			// Nur, wenn die oben berechnete Summe des Lastprofils ungleich 0
			// ist,
			// muss das Angebot zum Einheitspreis mit Strafe bestaetigt werden.
			// Andernfalls nicht, da es noch Zeit hat einen Partner zu finden.
			if (sumUntilCurrentSlot != 0) {
				volumeDemand += currentOffer.getSumAggLoadprofile();
				sumPricesDemand += currentOffer.getSumAggLoadprofile() * currentOffer.getPrice();
			} else {
				toRemoveFromDemands.add(currentOffer);
			}
		}
		// Entferne Angebote mit sumUntilCurrentSlot = 0
		for (Offer removeOffer : toRemoveFromDemands) {
			allDemandsAtDate.remove(removeOffer);
		}
		System.out.println("\nSumPricesDemand: " + sumPricesDemand);
		System.out.println("VolumeDemand: " + volumeDemand);

		for (Offer currentOffer : allSuppliesAtDate) {
			double sumUntilCurrentSlot = 0;
			// Berechne die Summe des Lastprofils einschließlich des aktuellen
			// Slots
			for (int i = 0; i == slot; i++) {
				sumUntilCurrentSlot += Math.abs(currentOffer.getAggLoadprofile().getValues()[i]);
			}

			// Nur, wenn die oben berechnete Summe des Lastprofils ungleich 0
			// ist,
			// muss das Angebot zum Einheitspreis mit Strafe bestaetigt werden.
			// Andernfalls nicht, da es noch Zeit hat einen Partner zu finden.
			if (sumUntilCurrentSlot != 0) {
				volumeSupply += currentOffer.getSumAggLoadprofile();
				sumPricesSupply += currentOffer.getSumAggLoadprofile() * currentOffer.getPrice();
			} else {
				toRemoveFromSupplies.add(currentOffer);
			}
		}
		// Entferne Angebote mit sumUntilCurrentSlot = 0
		for (Offer removeOffer : toRemoveFromSupplies) {
			allSuppliesAtDate.remove(removeOffer);
		}
		System.out.println("\nSumPricesSupply: " + sumPricesSupply);
		System.out.println("VolumeSupply: " + volumeSupply);

		double middle = (Math.abs(sumPricesDemand) + Math.abs(sumPricesSupply)) / 2;
		System.out.println("\nMittelpreis: " + middle);

		// Berechne Strafe
		double penalty = middle * 0.1;
		System.out.println("Strafe: " + penalty);
		double priceDemand = Math.abs((middle + penalty) / volumeDemand);
		double priceSupply = (middle - penalty) / volumeSupply;
		System.out.println("priceDemand: " + priceDemand);
		System.out.println("priceSupply: " + priceSupply);
		System.out.println("Gesamt: " + (priceDemand * volumeDemand + priceSupply * volumeSupply));

		// Bestaetige alle Angebote des Zeitrausm mit den errechneten Preisen
		for (Offer demand : allDemandsAtDate) {
			confirmOffer(demand, priceDemand);
		}
		for (Offer supply : allSuppliesAtDate) {
			confirmOffer(supply, priceSupply);
		}
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
	private void confirmOffer(Offer offer, double newPrice) {
		// Entferne offer von Marktplatz
		removeOffer(offer.getUUID(), true);

		// Nehme Lastprofil von offer in die Summe der Lastprofile aller
		// bestaetigten Angebote auf
		double[] values = offer.getAggLoadprofile().getValues().clone();

		double[] oldValues = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(offer.getDate()));
		if (oldValues == null) {
			oldValues = new double[numSlots];
			for (int i = 0; i < numSlots; i++) {
				oldValues[i] = 0;
			}
		}

		for (int i = 0; i < numSlots; i++) {
			values[i] += oldValues[i];
		}
		sumLoadprofilesConfirmedOffers.put(DateTime.ToString(offer.getDate()), values);

		AnswerToOfferFromMarketplace answerOffer = new AnswerToOfferFromMarketplace(offer.getUUID(), newPrice);
		Set<UUID> set = offer.getAllLoadprofiles().keySet();

		for (UUID consumer : set) {
			// Sende confirmOffer an consumer
			RestTemplate rest = new RestTemplate();
			HttpEntity<AnswerToOfferFromMarketplace> entity = new HttpEntity<AnswerToOfferFromMarketplace>(answerOffer,
					Application.getRestHeader());

			String url = new API().consumers(consumer).offers(offer.getUUID()).confirmByMarketplace().toString();

			Log.d(uuid, url);
			try {
				rest.exchange(url, HttpMethod.POST, entity, Void.class);
			} catch (Exception e) {
				Log.d(uuid, e.getMessage());
			}
		}
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

			confirmOffer(offers[0], newPrice1);
			confirmOffer(offers[1], newPrice2);
		} else {
			// Setze Kombination der beiden Angebote auf die Black List
			PossibleMatch possibleMatch = new PossibleMatch(offers[0], offers[1]);
			ArrayList<PossibleMatch> possibleMatches = blackListPossibleMatches.get(date);
			if (possibleMatches == null) {
				possibleMatches = new ArrayList<PossibleMatch>();
			}
			possibleMatches.add(possibleMatch);
			blackListPossibleMatches.put(date, possibleMatches);

			// Setze beide Angebote wieder neu auf den Marktplatz
			putOffer(offers[0]);
			putOffer(offers[1]);
		}
		negotiatingOffers.remove(negotiation);
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
	 * Gibt die Angebote von Erzeugern fuer das uebergebene Datum zurueck, die
	 * am wenigsten fuer den erzeugten Strom verlangen.
	 * 
	 * @param date
	 *            Datum fuer welches die Angebote sein sollen
	 * @param number
	 *            Anzahl an Angeboten, die angefragt wird
	 * @return Liste aller guenstigsten Angebote
	 */
	public ArrayList<Offer> getCheapestSupplyOffer(GregorianCalendar date, int number) {
		ArrayList<Offer> allSuppliesAtDate = supply.get(date);
		if (allSuppliesAtDate.size() < number) {
			number = allSuppliesAtDate.size();
		}
		ArrayList<Offer> cheapest = new ArrayList<Offer>();
		for (int i = 0; i < number; i++) {
			cheapest.add(allSuppliesAtDate.get(i));
		}
		return cheapest;
	}

	/**
	 * 
	 * @param uuid
	 * @return
	 */
	public Offer getDemand(UUID uuid) {
		// TODO Auto-generated method stub
		Set<String> demands = demand.keySet();
		for (String current : demands) {
			ArrayList<Offer> demandOffers = demand.get(current);
			for (Offer offer : demandOffers) {
				if (offer.getUUID() == uuid) {
					return offer;
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
	public ArrayList<Offer> getMostExpensiveDemandOffer(GregorianCalendar date, int number) {
		ArrayList<Offer> allDemandsAtDate = demand.get(date);
		if (allDemandsAtDate.size() < number) {
			number = allDemandsAtDate.size();
		}
		ArrayList<Offer> mostExpensive = new ArrayList<Offer>();
		for (int i = 0; i < number; i++) {
			mostExpensive.add(allDemandsAtDate.get(i));
		}
		return mostExpensive;
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

	public Offer getSupply(UUID uuid) {
		// TODO Auto-generated method stub
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
	 * Anpassung erfragt oder der reBAP bezahlt werden soll.
	 * 
	 * @param sumDeviationAll
	 *            Hoehe der Abweichung von der Prognose
	 * @return true, wenn eine Anpassung erfragt werden soll. false, wenn der
	 *         reBAP bezahlt werden soll.
	 */
	public boolean make(double sumDeviationAll) {
		return true;
	}

	/**
	 * Bestaetigt alle Angebote des naechsten Slots. In Abhaengigkeit von der
	 * Gesamtabweichung der Angebote von der Prognose werden Strafen verhaengt
	 * oder eine Anpassung erfragt.
	 */
	private void matchNextSlot() {
		double[] deviationAll = chargeDeviationAll(nextSlot);
		double[] currentPrediction = prediction.get(nextSlot);
		double sumDeviationAll = 0;
		for (int i = 0; i < numSlots; i++) {
			sumDeviationAll += Math.abs(deviationAll[i]);
		}
		if (deviationAll.equals(currentPrediction)) {
			// Bestaetige alle noch uebrigen Angebote zum selben Preis
			// Eine Strafe von 10% muss gezahlt werden.
			confirmAllRemainingOffersWithOnePrice(nextSlot, 4);
		}
		if (make(sumDeviationAll)) {
			matchAllGoodOffers(nextSlot);

			ArrayList<Offer> remainingOffers = demand.get(DateTime.ToString(nextSlot));
			ArrayList<Offer> supplies = supply.get(DateTime.ToString(nextSlot));

			for (Offer supply : supplies) {
				remainingOffers.add(supply);
			}

			// Sortiere verbleibende Angebote absteigend nach Betrag des
			// Volumens
			Collections.sort(remainingOffers);

			double[] currentDeviation = chargeDeviationAll(nextSlot);
			double sumCurrentDeviation = 0;
			for (int i = 0; i < numSlots; i++) {
				sumCurrentDeviation += Math.abs(currentDeviation[i]);
			}

			// Frage verbliebenen Angebote der Reihe nach
			// nach Ausgleichsmoeglichkeiten
			for (Offer currentOffer : remainingOffers) {
				ChangeRequestLoadprofile cr = new ChangeRequestLoadprofile(currentOffer.getUUID(), currentDeviation);
				UUID author = currentOffer.getAuthor();

				// Versende Anfrage an Author
				RestTemplate rest = new RestTemplate();
				HttpEntity<ChangeRequestLoadprofile> entity = new HttpEntity<ChangeRequestLoadprofile>(cr,
						Application.getRestHeader());

				String url = "http://localhost:8080/consumers/" + author + "/offers/" + currentOffer.getUUID()
						+ "/receiveChangeRequestLoadprofile";

				Log.d(uuid, url);

				try {
					rest.exchange(url, HttpMethod.POST, entity, Void.class);
				} catch (Exception e) {
				}

				// Warte, bis eine Antwort vom Consumer eingetroffen ist
				synchronized (currentAnswer) {
					while (currentAnswer == null) {
						try {
							currentAnswer.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

				// Berechne die neue Abweichung, wenn die uebergebene
				// aenderung beachtet wird.
				double[] possibleChange = currentAnswer.getChange();
				double[] possibleChangeDeviation = currentDeviation.clone();
				double sumPossibleChange = 0;
				double sumPossibleChangeDeviation = 0;
				for (int i = 0; i < numSlots; i++) {
					possibleChangeDeviation[i] += possibleChange[i];
					sumPossibleChange += Math.abs(possibleChange[i]);
					sumPossibleChangeDeviation += Math.abs(possibleChangeDeviation[i]);
				}

				// Pruefe, ob der Consumer eine aenderung vorgenommen hat und
				// wenn
				// ja,
				// ob diese aenderung zur Verbesserung beitraegt
				if (sumPossibleChange > 0 && sumPossibleChangeDeviation < sumCurrentDeviation) {
					// Bestaetige das Angebot zum uebergebenen Preis
					confirmOffer(currentOffer, currentOffer.getPrice());

					// Aktualisiere die Gesamtabweichung aller bestaetigter
					// Angebote
					// und deren Summe
					currentDeviation = chargeDeviationAll(nextSlot);
					sumCurrentDeviation = 0;
					for (int i = 0; i < numSlots; i++) {
						sumCurrentDeviation += Math.abs(currentDeviation[i]);
					}
					// Wenn die Summe = 0 gibt es keine Abweichung mehr und die
					// aenderungsnachfragen koennen beendet werden
					if (sumCurrentDeviation == 0) {
						break;
					}
				}
			}
		}

		// Bestaetige alle noch uebrigen Angebote zum selben Preis
		confirmAllRemainingOffersWithOnePrice(nextSlot, 4);

		// Zaehle Variable nextSlot um eins hoch
		nextSlot.add(Calendar.HOUR_OF_DAY, 1);
	}

	/**
	 * Fuehrt alle Angebote eines Zeitraums zusammen, die die Abweichung von der
	 * Prognose verbessern bzw. mit welchen die Abweichung von der Prognose < 5
	 * ist.
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
			double volumePrediction = 0;
			for (int i = 0; i < numSlots; i++) {
				deviationNew[i] = predictionCurrent[i] - sumLPCurrent[i] - loadprofile[i];
				sumDeviationNew += Math.abs(deviationNew[i]);
				sumDeviationOld += Math.abs(deviationOld[i]);
				volumePrediction += Math.abs(predictionCurrent[i]);
			}
			if (sumDeviationNew < sumDeviationOld || sumDeviationNew < volumePrediction) {
				offersJustMatched.add(offers[0]);
				offersJustMatched.add(offers[1]);
				matchFittingOffers(offers[0], offers[1]);
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
	private void matchFittingOffers(Offer offer1, Offer offer2) {
		System.out.println("Match Fitting Offers");
		if (!offer1.getDate().equals(offer2.getDate())) {
			return;
		}

		double[] valuesOffer1 = offer1.getAggLoadprofile().getValues();
		double[] valuesOffer2 = offer2.getAggLoadprofile().getValues();
		double sumOffer1 = 0;
		double sumOffer2 = 0;
		boolean offer1Supply = true;
		boolean offer2Supply = true;

		for (int i = 0; i < 4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}
		System.out.println("Summen: " + sumOffer1 + ", " + sumOffer2);

		// Pruefe, ob die beiden Angebote demand oder supply sind
		if (sumOffer1 < 0) {
			offer1Supply = false;
		}
		if (sumOffer2 < 0) {
			offer2Supply = false;
		}

		double price1, price2;

		// Pruefe, ob Preise schon zusammenpassen bzw. Anpassung von Marktplatz
		// moeglich ist, da es nur zu Verbesserungen fuer die Consumer fuehrt
		boolean pricesFit = (sumOffer1 * offer1.getPrice() + sumOffer2 * offer2.getPrice()) >= 0;

		// Berechne neue Preise, falls Preise schon so passen, dass
		// Anpassung vom Marktplatz moeglich ist, da es nur zu Verbesserungen
		// fuer die Consumer fuehrt
		if (pricesFit) {
			price1 = sumOffer1 * offer1.getPrice();
			price2 = sumOffer2 * offer2.getPrice();

			if (!(Math.abs(price1) == Math.abs(price2))) {
				if (Math.abs(price1) > Math.abs(price2) && price1 > 0
						|| Math.abs(price1) < Math.abs(price2) && price1 < 0) {
					price1 = Math.abs(price2 / sumOffer1);
					price2 = Math.abs(price2 / sumOffer2);
				} else {
					price2 = Math.abs(price1 / sumOffer2);
					price1 = Math.abs(price1 / sumOffer1);
				}
			}

			// Lege zusammengefuehrte Angebote und Preise in der Historie ab
			MatchedOffers matched = new MatchedOffers(price1, price2, offer1, offer2);
			System.out.println(matched.matchedOffersToString());
			ArrayList<MatchedOffers> array = matchedOffers.get(DateTime.ToString(offer1.getDate()));
			if (array == null) {
				array = new ArrayList<MatchedOffers>();
			}
			array.add(matched);
			matchedOffers.put(DateTime.ToString(offer1.getDate()), array);

			// Schicke Bestaetigung beider Angebote an Consumer
			confirmOffer(offer1, price1);
			confirmOffer(offer2, price2);
		}

		// Berechne Mittelwert, falls Preise nicht passen
		if (!pricesFit) {
			// Lege zu erreichende Preise fuer beide Angebote fest
			if (sumOffer1 + sumOffer2 == 0) {
				price1 = (offer1.getPrice() + offer2.getPrice()) / 2;
				price2 = price1;
			} else {
				// Ermittle Gesamtpreis fuer beide Angebote
				price1 = sumOffer1 * offer1.getPrice();
				price2 = sumOffer2 * offer2.getPrice();
				System.out.println("Einzelpreis: " + offer1.getPrice() + ", " + offer2.getPrice());
				System.out.println("Gesamtpreis: " + price1 + ", " + price2);
				// Ermittle Mittelwert von Betrag des Gesamtpreises beider
				// Angebote
				double price = (Math.abs(price1) + Math.abs(price2)) / 2;
				// Weise den Gesamtpreisen den jeweiligen Mittelwert zu
				if (offer1Supply) {
					price1 = price;
				} else {
					price1 = -price;
				}
				if (offer2Supply) {
					price2 = price;
				} else {
					price2 = -price;
				}
				System.out.println("Mittelwert: " + price1 + ", " + price2);

				// Berechne Preise pro kWh fuer Angebote
				price1 = price1 / sumOffer1;
				price2 = price2 / sumOffer2;
				System.out.println("Zu erreichende Preise: " + price1 + ", " + price2);
			}

			// Entferne Angebote von Marktplatz
			removeOffer(offer1.getUUID(), false);
			removeOffer(offer2.getUUID(), false);

			// Erstelle neue Verhandlung und speichere Verhandlung unter
			// negotiatingOffers ab
			Negotiation negotiation = new Negotiation(offer1, offer2, price1, price2, sumOffer1, sumOffer2);
			negotiation.negotiationToString();
			negotiatingOffers.put(negotiation.getUUID(), negotiation);
		}
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
	public void putOffer(Offer offer) {
		GregorianCalendar dateGreg = offer.getDate();
		String date = DateTime.ToString(dateGreg);
		GregorianCalendar currentDate = (GregorianCalendar) nextSlot.clone();
		currentDate.add(Calendar.HOUR_OF_DAY, -1);

		// Pruefe, dass Angebot nicht in Vergangenheit liegt
		if (dateGreg.before(currentDate)) {
			return;
		}

		double[] valuesLoadprofile = offer.getAggLoadprofile().getValues();
		double sumLoadprofile = 0;
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
	}

	public void receiveAnswerChangeRequestLoadprofile(ChangeRequestLoadprofile cr) {
		currentAnswer = cr;
		currentAnswer.notify();
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
		Offer removeOffer = getDemand(offer);
		if (removeOffer == null) {
			removeOffer = getSupply(offer);
		}
		if (removeOffer == null) {
			return;
		}

		String date = DateTime.ToString(removeOffer.getDate());

		if (!confirmed) {
			// Entferne Summe des Lastprofils von der Gesamtsumme aller
			// Lastprofile
			double[] values = removeOffer.getAggLoadprofile().getValues();
			double[] valuesDeviation = sumLoadprofilesAllOffers.get(date);
			for (int i = 0; i < numSlots; i++) {
				valuesDeviation[i] += values[i];
			}
			sumLoadprofilesAllOffers.put(date, valuesDeviation);
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
	 * Bestaetigt ein zufaellig ausgewaehltes Verbraucherangebot des
	 * Marktplatzes.
	 */
	public void ping() {
		Set<String> set = demand.keySet();

		for (String date : set) {
			Offer offer = demand.get(date).get(0);
			confirmOffer(offer, offer.getAggLoadprofile().getMinPrice());
			break;
		}
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
					System.out.println("	Price: " + offer.getPrice() + " Values: [" + values[0] + "][" + values[1]
							+ "][" + values[2] + "][" + values[3] + "]");
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
					System.out.println("	Price: " + offer.getPrice() + " Values: [" + values[0] + "][" + values[1]
							+ "][" + values[2] + "][" + values[3] + "]");
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
}
