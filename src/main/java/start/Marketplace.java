package start;

import java.util.HashMap;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import Entity.Offer;
import Packet.AnswerToOfferFromMarketplace;
import Packet.EndOfNegotiation;
import Util.MergedOffers;
import Util.DateTime;
import Util.PossibleMerge;
import Util.Negotiation;

/**
 * Marktplatz, auf welchem alle Angebote eintreffen und zusammengefuehrt werden
 *
 */
public class Marketplace {
	private static Marketplace instance = null;

	/**
	 * Maps, die alle noch nicht zusammengeführten Angebote des Marktplatzes
	 * beinhaltet
	 */
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();

	/**
	 * Map, die alle bisher zusammengeführten Angebote nach Zeitslot beinhaltet
	 * 
	 */
	private Map<String, ArrayList<MergedOffers>> mergedOffers = new TreeMap<String, ArrayList<MergedOffers>>();

	/**
	 * Map, die alle Angebote beinhaltet, über deren Preis gerade mit den
	 * Consumern verhandelt wird
	 */
	private Map<UUID, Negotiation> negotiatingOffers = new TreeMap<UUID, Negotiation>();

	/**
	 * Map, die die Summe der Abweichungen aller zusammengeführter Angebote nach
	 * Zeitslot beinhaltet
	 */
	private Map<String, double[]> sumLoadprofilesConfirmedOffers = new TreeMap<String, double[]>();

	/**
	 * Map, die die Summe der Abweichungen aller auf dem Marktplatz verfügbaren,
	 * aller gerade verhandelten und aller vom Marktplatz zusammengeführten
	 * Angebote nach Zeitslot beinhaltet
	 */
	private Map<String, double[]> sumLoadprofilesAllOffers = new TreeMap<String, double[]>();

	/**
	 * Map, die die Prognose für den jeweiligen Zeitslot beinhaltet
	 */
	private Map<String, double[]> prediction = new TreeMap<String, double[]>();

	/**
	 * Map, die nach Startzeit alle möglichen Angebotskombinationen enthält
	 */
	private Map<String, ArrayList<PossibleMerge>> listPossibleMerges = new TreeMap<String, ArrayList<PossibleMerge>>();
	/**
	 * Map, die nach Startzeit alle schon erfolglos verhandelten
	 * Angebotskombinationen enthält
	 */

	private Map<String, ArrayList<PossibleMerge>> blackListPossibleMerges = new TreeMap<String, ArrayList<PossibleMerge>>();

	/**
	 * Aktueller eexPreis
	 */
	private static final double eexPrice = 20;

	/**
	 * Aktuell geduldete Abweichung. (5% von erwartetem Gesamtvolumen: 100)
	 */
	private double maxDeviation = 0.05 * 100;

	/**
	 * Anzahl an 15-Minuten-Slots der Angebote
	 */
	private int numSlots = 4;

	/**
	 * Startzeit des nächsten Slots, der geplant werden muss
	 */
	private GregorianCalendar nextSlot;

	/**
	 * Erstellt einen neuen Marktplatz mit Prognose = 0 für die nächsten 24h
	 */
	private Marketplace() {
		// Setzt die Vorhersage für die nächsten 24h = 0
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

	private void confirmAllRemainingOffersWithOnePrice(GregorianCalendar date, boolean reBAPpenalty) {
		String dateString = DateTime.ToString(date);
		Set<UUID> demands = demand.keySet();
		Set<UUID> supplies = supply.keySet();
		ArrayList<Offer> allDemandsAtDate = new ArrayList<Offer>();
		ArrayList<Offer> allSuppliesAtDate = new ArrayList<Offer>();
		double volumeDemand = 0;
		double volumeSupply = 0;
		double sumPricesDemand = 0;
		double sumPricesSupply = 0;
		
		for (UUID uuid: demands) {
			Offer currentOffer = demand.get(uuid);
			if (DateTime.ToString(currentOffer.getDate()).equals(dateString)) {
				allDemandsAtDate.add(currentOffer);
				volumeDemand += currentOffer.getSumAggLoadprofile();
				sumPricesDemand += currentOffer.getSumAggLoadprofile()*currentOffer.getPrice();
			}
		}
		System.out.println("\nSumPricesDemand: "+sumPricesDemand);
		System.out.println("VolumeDemand: " +volumeDemand);
		
		for (UUID uuid: supplies) {
			Offer currentOffer = supply.get(uuid);
			if (DateTime.ToString(currentOffer.getDate()).equals(dateString)) {
				allSuppliesAtDate.add(currentOffer);
				volumeSupply += currentOffer.getSumAggLoadprofile();
				sumPricesSupply += currentOffer.getSumAggLoadprofile()*currentOffer.getPrice();
			}
		}
		System.out.println("\nSumPricesSupply: "+sumPricesSupply);
		System.out.println("VolumeSupply: " +volumeSupply);
		
		double middle = (Math.abs(sumPricesDemand)+Math.abs(sumPricesSupply))/2;
		System.out.println("\nMittelpreis: " +middle);
		// Berechne Strafe
		double penalty;
		if (reBAPpenalty) {
			// TODO Strafe für reBAP
			penalty = middle*0.1;
		}
		else {
			penalty = middle*0.1;
		}
		System.out.println("Strafe: " +penalty);
		double priceDemand = Math.abs((middle+penalty)/volumeDemand);
		double priceSupply = (middle-penalty)/volumeSupply;
		System.out.println("priceDemand: " +priceDemand);
		System.out.println("priceSupply: " +priceSupply);
		System.out.println("Gesamt: " + (priceDemand*volumeDemand + priceSupply*volumeSupply));
		
		// Bestätige alle Angebote des Zeitrausm mit den errechneten Preisen
		for (Offer demand: allDemandsAtDate) {
			//confirmOffer(demand, priceDemand);
		}
		for (Offer supply: allSuppliesAtDate) {
			//confirmOffer(supply, priceSupply);
		}
	}
	
	// TODO Noch notwendig????
	private double[] chargeWorseningWithLoadprofile(GregorianCalendar date, double[] loadprofile) {
		double[] currentDeviation = chargeDeviationConfirmed(date);
		double[] newDeviation = new double[numSlots];
		double[] worsening = new double[numSlots];
		for (int i = 0; i < numSlots; i++) {
			newDeviation[i] = -(currentDeviation[i] - loadprofile[i]);
			if (Math.abs(newDeviation[i]) > Math.abs(currentDeviation[i])) {
				worsening[i] = Math.abs(newDeviation[i]) - Math.abs(currentDeviation[i]);
				if (newDeviation[i] < 0) {
					worsening[i] = -worsening[i];
				}
			} else {
				worsening[i] = 0;
			}
		}
		return worsening;
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
		// Nehme Lastprofil von offer in die Summe der Lastprofile aller
		// bestätigten Angebote auf
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

			String url = "http://localhost:8080/consumers/" + consumer + "/offers/" + offer.getUUID()
					+ "/confirmByMarketplace";

			try {
				ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Wird von Negotiation aufgerufen, wenn eine Verhandlung beendet ist. Ist
	 * die Verhandlung erfolgreich beendet worden, so werden beide Angebote zu
	 * den neuen Preisen bestätigt. Ist die Verhandlung nicht erfolgreich
	 * beendet worden, so werden beide Angebote wieder auf den Marktplatz
	 * gestellt. Die Negotiation wird aus der Liste negotiatingOffers entfernt.
	 * 
	 * @param negotiation
	 *            Verhandlung, die beendet wurde
	 * @param newPrice1
	 *            Neu verhandelter Preis für Angebot1
	 * @param newPrice2
	 *            Neu verhandelter Preis für Angebot2
	 * @param successfull
	 *            Gibt an, ob die Verhandlung erfolgreich beendet wurde
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
			// Lege zusammengeführte Angebote und Preise in der Historie ab
			MergedOffers merged = new MergedOffers(newPrice1, newPrice2, offers[0], offers[1]);
			ArrayList<MergedOffers> array = mergedOffers.get(date);
			if (array == null) {
				array = new ArrayList<MergedOffers>();
			}
			array.add(merged);
			mergedOffers.put(date, array);

			confirmOffer(offers[0], newPrice1);
			confirmOffer(offers[1], newPrice2);
		} else {
			// Setze Kombination der beiden Angebote auf die Black List
			PossibleMerge possibleMerge = new PossibleMerge(offers[0], offers[1]);
			ArrayList<PossibleMerge> possibleMerges = blackListPossibleMerges.get(date);
			if (possibleMerges == null) {
				possibleMerges = new ArrayList<PossibleMerge>();
			}
			possibleMerges.add(possibleMerge);
			blackListPossibleMerges.put(date, possibleMerges);

			// Setze beide Angebote wieder neu auf den Marktplatz
			putOffer(offers[0]);
			putOffer(offers[1]);
			// TODO Sollen verhandelte Preise gespeichert werden???
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
		Set<UUID> set;
		if (offerIsSupplyOffer) {
			set = demand.keySet();
		} else {
			set = supply.keySet();
		}
		if (set == null) {
			return false;
		}
		int numSlots = 4;

		Offer offerMostImprovement = offer;
		double[] valuesOffer = offer.getAggLoadprofile().getValues();

		// Hole alle aktuellen Werte für Vorhersage
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

		// Gibt die Abweichung von der Prognose für alle bestätigten Lastprofile
		// und das aktuelle Angebot an
		double[] deviationOfferPrediction = new double[numSlots];
		double sumDeviationOfferPrediction = 0;

		for (int i = 0; i < numSlots; i++) {
			deviationOfferPrediction[i] = predictionCurrent[i] - sumLoadprofilesCurrent[i] - valuesOffer[i];
			sumDeviationOfferPrediction += Math.abs(deviationOfferPrediction[i]);
			sumDeviationCurrentPrediction += Math.abs(deviationCurrentPrediction[i]);
		}

		// Gibt die Abweichung von der Prognose für
		// deviationOffer-mostImprovement an
		double[] deviationMostImprovementPrediction = deviationOfferPrediction;
		double sumDeviationMostImprovementPrediction = sumDeviationOfferPrediction;

		// Hole den Array aller bereits in listPossibleMerges hinterlegten
		// possibleMerges
		// für die Startzeit dateOffer
		ArrayList<PossibleMerge> possibleMergesOfDateOffer = listPossibleMerges.get(DateTime.ToString(offer.getDate()));
		if (possibleMergesOfDateOffer == null) {
			possibleMergesOfDateOffer = new ArrayList<PossibleMerge>();
		}
		ArrayList<PossibleMerge> blackListPossibleMergesOfDateOffer = blackListPossibleMerges
				.get(DateTime.ToString(offer.getDate()));

		for (UUID uuid : set) {
			Offer compareOffer;

			if (offerIsSupplyOffer) {
				compareOffer = demand.get(uuid);
			} else {
				compareOffer = supply.get(uuid);
			}

			// Prüfe, ob Kombination der Angebote auf Blacklist
			// Wenn ja, überspringe dieses Angebot
			PossibleMerge possibleMerge = new PossibleMerge(offer, compareOffer);
			if (blackListPossibleMergesOfDateOffer != null) {
				if (blackListPossibleMergesOfDateOffer.contains(possibleMerge)) {
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
				deviationMostImprovementPrediction = deviationPrediction;
			}

			// Füge Komination der Angebote für listPossibleMerges zum Array
			// hinzu
			possibleMergesOfDateOffer.add(possibleMerge);
		}

		// Prüfe, ob hinzufügen der beiden Angebote mit geringster Abweichung
		// Annäherung an Prognose verbessert oder um weniger als 5
		// verschlechtert
		System.out.println("Maximale Abweichung: " + maxDeviation);
		System.out.println("Tatsächliche Abweichung von Prognose: " + sumDeviationMostImprovementPrediction);
		if (sumDeviationMostImprovementPrediction < sumDeviationCurrentPrediction
				|| sumDeviationMostImprovementPrediction < maxDeviation) {
			if (offer.equals(offerMostImprovement)) {
				// confirmOffer(offer, offer.getPrice());
			} else {
				mergeFittingOffers(offer, offerMostImprovement);
				return true;
			}
		}

		// Füge Array mit allen neuen Kombinationen zu listPossibleMerges hinzu
		if (possibleMergesOfDateOffer.size() != 0) {
			listPossibleMerges.put(DateTime.ToString(offer.getDate()), possibleMergesOfDateOffer);
		}
		return false;
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

	public ArrayList<PossibleMerge> getPossibleMerges(GregorianCalendar date) {
		return listPossibleMerges.get(DateTime.ToString(date));
	}

	/**
	 * Liefert die Summe aller auf dem Marktplatz vorhandenen Lastprofile eines
	 * Zeitraums
	 * 
	 * @param time
	 *         Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller auf dem Marktplatz vorhandenen
	 *         Lastprofile des Zeitraums
	 */
	public double[] getSumAllOffers(GregorianCalendar date) {
		return sumLoadprofilesAllOffers.get(DateTime.ToString(date));
	}

	/**
	 * Liefert die Summe aller bestätigten Lastprofile eines Zeitraums
	 * 
	 * @param time
	 *            Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller bestätigten Lastprofile des Zeitraums
	 */
	public double[] getSumConfirmedOffers(GregorianCalendar date) {
		return sumLoadprofilesConfirmedOffers.get(DateTime.ToString(date));
	}
	
	/**
	 * Gibt zurück, ob für die Höhe der Abweichung von der Prognose
	 * eine Anpassung erfragt oder der reBAP bezahlt werden soll.
	 * @param sumDeviationAll Höhe der Abweichung von der Prognose
	 * @return true, wenn eine Anpassung erfragt werden soll. false,
	 * wenn der reBAP bezahlt werden soll.
	 */
	public boolean make(double sumDeviationAll) {
		return true;
	}

	public String marketplaceToString() {
		String s = "\nMarketplace: \nnumberOfDemands: " + demand.size() + " numberOfSupplies: " + supply.size();
		s = s + " numberOfNegotiations: " + negotiatingOffers.size() + " numberOfMerges: " + mergedOffers.size();
		s = s + " numberOfPossibleMerges: " + listPossibleMerges.size() + " numberOfBlackList: "
				+ blackListPossibleMerges.size();
		return s;
	}

	/**
	 * Bestätigt alle Angebote des nächsten Slots.
	 * In Abhängigkeit von der Gesamtabweichung der Angebote von der Prognose
	 * werden Strafen verhängt oder eine Anpassung erfragt.
	 */
	private void matchNextSlot() {
		double[] deviationAll = chargeDeviationAll(nextSlot);
		double[] currentPrediction = prediction.get(nextSlot);
		double sumDeviationAll = 0;
		for (int i = 0; i < numSlots; i++) {
			sumDeviationAll += Math.abs(deviationAll[i]);
		}
		if (deviationAll.equals(currentPrediction)) {
			// Bestätige alle noch uebrigen Angebote zum selben Preis
			// Eine Strafe von 10% muss gezahlt werden.
			confirmAllRemainingOffersWithOnePrice(nextSlot, false);
		}
		if (make(sumDeviationAll)) {
			// TODO Alle Schritte für Make
			// TODO Wie soll sich hier die Preisgestaltung abspielen? -
			// Nachfrage nach Änderungen dauert zu lang?
			mergeAllGoodOffers(nextSlot);
			
			ArrayList<Offer> remainingOffers = new ArrayList<Offer>();
			Set<UUID> demands = demand.keySet();
			Set<UUID> supplies = supply.keySet();
			
			for (UUID current: demands) {
				Offer currentOffer = demand.get(current);
				if (currentOffer.getDate().equals(nextSlot)) {
					remainingOffers.add(currentOffer);
				}
			}
			for (UUID current: supplies) {
				Offer currentOffer = supply.get(current);
				if (currentOffer.getDate().equals(nextSlot)) {
					remainingOffers.add(currentOffer);
				}
			}
			
			// Sortiere verbleibende Angebote absteigend nach Betrag des Volumens
			Collections.sort(remainingOffers);
			
			double[] currentDeviation = chargeDeviationAll(nextSlot);
			
			for (Offer currentOffer: remainingOffers) {
				askOfferForChange(currentOffer, currentDeviation);
				
				currentDeviation = chargeDeviationAll(nextSlot);
			}
			
		} else {
			// Bestätige alle noch uebrigen Angebote zum selben Preis
			// Die Strafe beinhaltet hier einen Ausgleich für den zu
			// zahlenden reBAP
			confirmAllRemainingOffersWithOnePrice(nextSlot, true);
		}
	}
	
	private boolean askOfferForChange(Offer offer, double[] change) {
		// TODO Sende Anfrage für Änderung
		
		// TODO Bekomme Antwort auf Anfrage
		
		return false;
	}
	
	/**
	 * Führt alle Angebote eines Zeitraums zusammen, die die Abweichung von der Prognose
	 * verbessern bzw. mit welchen die Abweichung von der Prognose < 5 ist.
	 * @param slot Start des Zeitraums, für den die Angebote zusammengeführt werden sollen
	 */
	private void mergeAllGoodOffers(GregorianCalendar date) {
		// Hole die Liste aller möglichen Merges
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(DateTime.ToString(date));
		// Erstelle eine neue Liste, in welcher alle gerade zusammengefügten
		// Angebote gespeichert werden
		ArrayList<Offer> offersJustMerged = new ArrayList<Offer>();

		// Gehe die Liste aller möglichen Merges durch und führe die guten
		// zusammen
		for (PossibleMerge possibleMerge : possibleMerges) {
			Offer[] offers = possibleMerge.getOffers();
			// Wenn eines der Angebote in offersJustMerged ist, wurde es bereits
			// bei einem vorherigen Schleifendurchlauf zusammengeführt und
			// bestätigt und steht nicht mehr zur Verfügung
			if (offersJustMerged.contains(offers[0]) || offersJustMerged.contains(offers[1])) {
				continue;
			}
			double[] loadprofile = possibleMerge.getValuesAggLoadprofile();
			double[] predictionCurrent = prediction.get(DateTime.ToString(possibleMerge.getDate()));
			double[] sumLPCurrent = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(possibleMerge.getDate()));
			double[] deviationOld = chargeDeviationConfirmed(possibleMerge.getDate());
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
				offersJustMerged.add(offers[0]);
				offersJustMerged.add(offers[1]);
				mergeFittingOffers(offers[0], offers[1]);
			}
		}
	}
	
	// TODO Methode löschen
	private void mergeAllRemainingOffers(GregorianCalendar date) {
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(DateTime.ToString(nextSlot));

		// Sortiere possibleMerges nach Preisunterschied
		Collections.sort(possibleMerges);

		// Erstelle Array, in welchem alle bereits bestätigten Angebote
		// gesammelt werden
		ArrayList<Offer> confirmedOffers = new ArrayList<Offer>();

		// Bestätige der Reihe nach alle possibleMerges, wenn die Angebote
		// nicht schon bestätigt wurden
		for (PossibleMerge possibleMerge : possibleMerges) {
			Offer[] offers = possibleMerge.getOffers();
			if (confirmedOffers.contains(offers[0]) || confirmedOffers.contains(offers[1])) {
				continue;
			} else {
				confirmedOffers.add(offers[0]);
				confirmedOffers.add(offers[1]);
				// Berechne Verschlechterung, die durch das Lastprofil von
				// possibleMerge entsteht
				double[] worsening = chargeWorseningWithLoadprofile(possibleMerge.getDate(),
						possibleMerge.getValuesAggLoadprofile());

				mergeOffers(offers[0], offers[1], worsening);
			}
		}
		for (Offer current : confirmedOffers) {
			removeFromPossibleMerges(current);
		}
		Set<UUID> demandUUIDs = demand.keySet();
		Set<UUID> supplyUUIDs = supply.keySet();
		boolean remainingDemand = demandUUIDs.size() > 0;
		boolean remainingSupply = supplyUUIDs.size() > 0;
		if (remainingDemand && remainingSupply) {
			System.out.println("Das darf nicht sein");
		} else if (remainingDemand) {
			for (UUID demandUUID : demandUUIDs) {
				// TODO wie wird Preis hier festgelegt???
				double price = eexPrice;
				confirmOffer(demand.get(demandUUID), price);
			}
		} else if (remainingSupply) {
			for (UUID supplyUUID : supplyUUIDs) {
				// TODO wie wird Preis hier festgelegt???
				double price = eexPrice;
				confirmOffer(supply.get(supplyUUID), price);
			}
		}
	}
	
	/**
	 * Führt Angebote zusammen, die die Annäherung an die Prognose verbessern
	 * bzw. nicht sehr viel verschlechtern. Die Angebote werden vom Marktplatz
	 * entfernt. Passt der Preis, so werden die Angebote direkt bestätigt. Passt
	 * der Preis nicht, wird eine Verhandlung mit den beiden Angeboten
	 * gestartet.
	 * 
	 * @param offer1
	 *            Erstes Angebot, das zusammengeführt werden soll
	 * @param offer2
	 *            Zweites Angebot, das zusammengeführt werden soll
	 */
	private void mergeFittingOffers(Offer offer1, Offer offer2) {
		System.out.println("Merge Fitting Offers");
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

		// Prüfe, ob die beiden Angebote demand oder supply sind
		if (sumOffer1 < 0) {
			offer1Supply = false;
		}
		if (sumOffer2 < 0) {
			offer2Supply = false;
		}

		// Nehme Angebote vom Marktplatz
		this.removeOffer(offer1.getUUID(), true);
		this.removeOffer(offer2.getUUID(), true);

		double price1, price2;

		// Prüfe, ob Preise schon zusammenpassen bzw. Anpassung von Marktplatz
		// möglich ist, da es nur zu Verbesserungen für die Consumer führt
		boolean pricesFit = (sumOffer1 * offer1.getPrice() + sumOffer2 * offer2.getPrice()) >= 0;

		// Berechne neue Preise, falls Preise schon so passen, dass
		// Anpassung vom Marktplatz möglich ist, da es nur zu Verbesserungen
		// für die Consumer führt
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

			// Lege zusammengeführte Angebote und Preise in der Historie ab
			MergedOffers merged = new MergedOffers(price1, price2, offer1, offer2);
			System.out.println(merged.mergedOffersToString());
			ArrayList<MergedOffers> array = mergedOffers.get(DateTime.ToString(offer1.getDate()));
			if (array == null) {
				array = new ArrayList<MergedOffers>();
			}
			array.add(merged);
			mergedOffers.put(DateTime.ToString(offer1.getDate()), array);

			// Schicke Bestätigung beider Angebote an Consumer
			confirmOffer(offer1, price1);
			confirmOffer(offer2, price2);
		}

		// Berechne Mittelwert, falls Preise nicht passen
		if (!pricesFit) {
			// Lege zu erreichende Preise für beide Angebote fest
			if (sumOffer1 + sumOffer2 == 0) {
				price1 = (offer1.getPrice() + offer2.getPrice()) / 2;
				price2 = price1;
			} else {
				// Ermittle Gesamtpreis für beide Angebote
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

				// Berechne Preise pro kWh für Angebote
				price1 = price1 / sumOffer1;
				price2 = price2 / sumOffer2;
				System.out.println("Zu erreichende Preise: " + price1 + ", " + price2);
			}

			// Erstelle neue Verhandlung und speichere Verhandlung unter
			// negotiatingOffers ab
			Negotiation negotiation = new Negotiation(offer1, offer2, price1, price2, sumOffer1, sumOffer2);
			negotiation.negotiationToString();
			negotiatingOffers.put(negotiation.getUUID(), negotiation);
		}
	}

	/**
	 * Fuehrt zwei Angebote zusammen und bestaetigt allen beteiligten Consumern
	 * das Angebot
	 * 
	 * @param offer1
	 *            Erstes Angebot der Zusammenfuehrung
	 * @param offer2
	 *            Zweites Angebot der Zusammenfuehrung
	 * @param deviation
	 *            Viertelstuendliche Abweichung der Summe der beiden Angebote
	 */
	// TODO Prüfe, ob Methode noch notwendig
	private void mergeOffers(Offer offer1, Offer offer2, double[] worsening) {
		System.out.println("Merge Offers");
		String date = DateTime.ToString(offer1.getDate());
		if (!offer1.getDate().equals(offer2.getDate())) {
			// TODO Fehlermeldung, dass Angebote wegen unterschiedlichem
			// Zeitraum
			// nicht zusammengeführt werden können
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

		// Prüfe, ob die beiden Angebote demand oder supply sind
		if (sumOffer1 < 0) {
			offer1Supply = false;
		}
		if (sumOffer2 < 0) {
			offer2Supply = false;
		}

		// Nehme Angebote vom Marktplatz
		this.removeOffer(offer1.getUUID(), true);
		this.removeOffer(offer2.getUUID(), true);

		double price1, price2;

		// Lege Preis für beide Angebote ohne "Strafe" fest
		if (sumOffer1 + sumOffer2 == 0) {
			price1 = (offer1.getPrice() + offer2.getPrice()) / 2;
			price2 = price1;
		} else {
			// Ermittle Gesamtpreis für beide Angebote
			price1 = sumOffer1 * offer1.getPrice();
			price2 = sumOffer2 * offer2.getPrice();
			System.out.println("Einzelpreis: " + offer1.getPrice() + ", " + offer2.getPrice());
			System.out.println("Gesamtpreis: " + price1 + ", " + price2);
			// Ermittle Mittelwert von Betrag des Gesamtpreises beider Angebote
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

			// Berechne Preise pro kWh für Angebote
			price1 = price1 / sumOffer1;
			price2 = price2 / sumOffer2;
		}
		System.out.println("Neuer Preis: " + price1 + ", " + price2);

		boolean worse = false;
		for (int i = 0; i < numSlots; i++) {
			if (worsening[i] != 0) {
				worse = true;
			}
		}

		if (worse) {
			// Lege bei Verschlechterung Gesamtstrafe für einzelne Angebote fest
			double sumDeviation1 = 0;
			double sumDeviation2 = 0;
			for (int i = 0; i < numSlots; i++) {
				if (worsening[i] != 0) {
					if (Math.abs(worsening[i]) == Math.abs(valuesOffer1[i]) + Math.abs(valuesOffer2[i])) {
						sumDeviation1 += Math.abs(valuesOffer1[i]);
						sumDeviation2 += Math.abs(valuesOffer2[i]);
					} else {
						if (Math.abs(valuesOffer1[i]) > Math.abs(valuesOffer2[i])) {
							sumDeviation1 += Math.abs(worsening[i]);
						} else {
							sumDeviation2 += Math.abs(worsening[i]);
						}
					}
				}
			}
			sumDeviation1 = sumDeviation1 * eexPrice;
			sumDeviation2 = sumDeviation2 * eexPrice;
			System.out.println("sumDeviation: " + sumDeviation1 + ", " + sumDeviation2);

			// Berechne Strafe pro kWh und füge sie zum Preis hinzu
			price1 = price1 - sumDeviation1 / sumOffer1;
			price2 = price2 - sumDeviation2 / sumOffer2;
		}

		// Lege zusammengeführte Angebote und Preise in der Historie ab
		MergedOffers merged = new MergedOffers(price1, price2, offer1, offer2);
		ArrayList<MergedOffers> array = mergedOffers.get(date);
		if (array == null) {
			array = new ArrayList<MergedOffers>();
		}
		array.add(merged);
		mergedOffers.put(date, array);

		// Schicke Bestätigung für beide Angebote
		// confirmOffer(offer1, price1);
		System.out.println("Preis Angebot 1: " + price1);
		// confirmOffer(offer2, price2);
		System.out.println("Preis Angebot 2: " + price2);
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
		double[] valuesLoadprofile = offer.getAggLoadprofile().getValues();
		double sumLoadprofile = 0;
		double[] sumAllOffers = sumLoadprofilesAllOffers.get(DateTime.ToString(offer.getDate()));
		if (sumAllOffers == null) {
			sumAllOffers = new double[numSlots];
		}
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
			sumAllOffers[i] += valuesLoadprofile[i];
		}
		sumLoadprofilesAllOffers.put(DateTime.ToString(offer.getDate()), sumAllOffers);

		if (sumLoadprofile >= 0) {
			if (!findFittingOffer(offer, true)) {
				supply.put(offer.getUUID(), offer);
			}
		} else {
			if (!findFittingOffer(offer, false)) {
				demand.put(offer.getUUID(), offer);
			}
		}
	}

	/**
	 * Entfernt das uebergebene Angebot vom Marktplatz
	 * 
	 * @param offer
	 *            Angebot, das entfernt werden soll
	 */
	public void removeOffer(UUID offer, boolean merged) {
		// Prüfe, ob Angebot auf Marktplatz
		Offer removeOffer = supply.get(offer);
		supply.remove(offer);
		if (removeOffer == null) {
			removeOffer = demand.get(offer);
			demand.remove(offer);
			if (removeOffer == null) {
				// TODO Fehlermeldung, dass Angebot nicht entfernt werden kann
				return;
			}
		}

		if (!merged) {
			// Entferne Summe des Lastprofiles von der Gesamtsumme aller
			// Lastprofile
			String date = DateTime.ToString(removeOffer.getDate());
			double[] values = removeOffer.getAggLoadprofile().getValues();
			double[] valuesDeviation = sumLoadprofilesAllOffers.get(date);
			for (int i = 0; i < numSlots; i++) {
				valuesDeviation[i] += values[i];
			}
			sumLoadprofilesAllOffers.put(date, valuesDeviation);
		}

		// Entferne Angebote aus listPossibleMerges
		removeFromPossibleMerges(removeOffer);
	}

	/**
	 * Entfernt alle möglichen Kombination aus possibleMerges, an denen das
	 * übergeben Angebot beteiligt ist.
	 * 
	 * @param offer
	 *            Angebot, dessen Kombinationen entfernt werden sollen
	 */
	private void removeFromPossibleMerges(Offer offer) {
		String key = DateTime.ToString(offer.getDate());
		ArrayList<PossibleMerge> oldPossibleMerges = listPossibleMerges.get(key);
		if (oldPossibleMerges == null) {
			return;
		}
		ArrayList<PossibleMerge> newPossibleMerges = new ArrayList<PossibleMerge>();
		for (PossibleMerge current : oldPossibleMerges) {
			Offer[] offers = current.getOffers();
			if (!(offers[0].equals(offer) || offers[1].equals(offer))) {
				newPossibleMerges.add(current);
			}
		}
		if (newPossibleMerges.size() == 0) {
			listPossibleMerges.remove(key);
		} else {
			listPossibleMerges.put(key, newPossibleMerges);
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
		map.put("numberOfMerges", mergedOffers.size());
		map.put("eexPrice", getEEXPrice());
		map.put("allDeviation", sumLoadprofilesAllOffers);
		map.put("mergedDeviation", sumLoadprofilesConfirmedOffers);

		return map;
	}

	public void BKV() {
		GregorianCalendar now = DateTime.now();
		if (now.get(Calendar.HOUR_OF_DAY) == nextSlot.get(Calendar.HOUR_OF_DAY) && now.get(Calendar.MINUTE) >= 45) {
			matchNextSlot();
			nextSlot.add(Calendar.HOUR_OF_DAY, 1);
		}

		// Prüfe, ob Angebot für aktuellen, schon verhandelten Slot vorliegen
		// TODO Was passiert mit diesen Angeboten??
		int currentSlot = (int) Math.floor(now.get(Calendar.MINUTE) / 15);
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		String start = DateTime.ToString(now);

		double[] currentDeviation = chargeDeviationConfirmed(now);
		double sumCurrentDeviation = 0;
		for (int i = 0; i < numSlots; i++) {
			sumCurrentDeviation += Math.abs(currentDeviation[i]);
		}
		double[] currentPrediction = prediction.get(start);
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(start);

		for (PossibleMerge possibleMerge : possibleMerges) {
			double[] values = possibleMerge.getValuesAggLoadprofile();
			double[] newDeviationFromPrediction = new double[numSlots];
			double sumNewDeviationFromPrediction = 0;
			for (int i = 0; i < numSlots; i++) {
				newDeviationFromPrediction[i] = currentPrediction[i] - values[i];
				sumNewDeviationFromPrediction += Math.abs(newDeviationFromPrediction[i]);
			}
			// Wenn die Abweichung kleiner 5, werden die Angebote
			// zusammengeführt
			if (sumNewDeviationFromPrediction < sumCurrentDeviation || sumNewDeviationFromPrediction < maxDeviation) {
				Offer[] offers = possibleMerge.getOffers();
				mergeFittingOffers(offers[0], offers[1]);
				// TODO aktualisiere perfectMatch und noch mehr???
			} else {
				// TODO
			}
		}
	}

	/**
	 * Bestaetigt ein zufaellig ausgewaehltes Verbraucherangebot des
	 * Marktplatzes.
	 */
	public void ping() {
		Set<UUID> set = demand.keySet();

		for (UUID uuidOffer : set) {
			Offer offer = demand.get(uuidOffer);
			confirmOffer(offer, offer.getAggLoadprofile().getMinPrice());
			demand.remove(offer.getUUID());
			break;
		}
	}

	/**
	 * Gibt alle aktuell auf dem Marktplatz vorhandenen Angebote sortiert nach
	 * Demand und Supply auf der Console aus.
	 */
	public void allOffersToString() {
		Set<UUID> demandSet = demand.keySet();
		if (demandSet.size() == 0) {
			System.out.println("\nNo Demand");
		} else {
			System.out.println("\nDemand:");
			for (UUID uuid : demandSet) {
				Offer offer = demand.get(uuid);
				double[] values = offer.getAggLoadprofile().getValues();
				System.out.println("	Price: " + offer.getPrice() + " Values: [" + values[0] + "][" + values[1] + "]["
						+ values[2] + "][" + values[3] + "]");
			}
		}

		Set<UUID> supplySet = supply.keySet();
		if (supplySet.size() == 0) {
			System.out.println("No Supply");
		} else {
			System.out.println("Supply:");
			for (UUID uuid : supplySet) {
				Offer offer = supply.get(uuid);
				double[] values = offer.getAggLoadprofile().getValues();
				System.out.println("	Price: " + offer.getPrice() + " Values: [" + values[0] + "][" + values[1] + "]["
						+ values[2] + "][" + values[3] + "]");
			}
		}
	}

	/**
	 * Gibt alle Merges von der Black List auf der Console aus.
	 */
	public void blackListPossibleMergesToString() {
		Set<String> date = blackListPossibleMerges.keySet();
		if (date.size() == 0) {
			System.out.println("\nNo Black List Possible Merges");
			return;
		}
		System.out.println("\nBlack List Possible Merges:");
		for (String currentDate : date) {
			System.out.println("Zeit: " + currentDate);
			ArrayList<PossibleMerge> possibleMerge = blackListPossibleMerges.get(currentDate);
			if (possibleMerge == null) {
				System.out.println("No Black List Possible Merges.");
			}
			for (int i = 0; i < possibleMerge.size(); i++) {
				System.out.println("	" + (i + 1) + ". : " + possibleMerge.get(i).toString());
			}
		}
	}

	/**
	 * Gibt alle zusammengeführten Angebote auf der Console aus.
	 */
	public void mergedToString() {
		Set<String> set = mergedOffers.keySet();
		if (set.size() == 0) {
			System.out.println("\nNo Merged Offers");
			return;
		}
		System.out.println("\nMerged Offers:");
		for (String neu : set) {
			System.out.println(neu + ":");
			ArrayList<MergedOffers> merged = mergedOffers.get(neu);
			for (int i = 0; i < merged.size(); i++) {
				System.out.println("	" + (i + 1) + ": " + merged.get(i).mergedOffersToString());
			}
		}
	}

	/**
	 * Gibt alle möglichen Zusammenführungen auf der Console aus.
	 */
	public void possibleMergesToString() {
		Set<String> date = listPossibleMerges.keySet();
		if (date.size() == 0) {
			System.out.println("\nNo Possible Merges");
			return;
		}
		System.out.println("\nPossible Merges:");
		for (String currentDate : date) {
			System.out.println("Zeit: " + currentDate);
			ArrayList<PossibleMerge> possibleMerge = listPossibleMerges.get(currentDate);
			if (possibleMerge == null) {
				System.out.println("No Possible Merges.");
			}
			for (int i = 0; i < possibleMerge.size(); i++) {
				System.out.println("	" + (i + 1) + ". : " + possibleMerge.get(i).toString());
			}
		}
	}
}
