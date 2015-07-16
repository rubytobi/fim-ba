package start;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.Set;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import Entity.Offer;
import Packet.ConfirmOffer;
import Util.MergedOffers;
import Util.DateTime;
import Util.Log;
import Util.PossibleMerge;

/**
 * Marktplatz, auf welchem alle Angebote eintreffen und zusammengefuehrt werden
 *
 */
public class Marketplace {
	private static Marketplace instance = null;
	
	// Maps, die alle noch nicht zusammengeführten Angebote des Marktplatzes beinhaltet
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();
	
	// Map, die alle bisher zusammengeführten Angebote nach Zeitslot beinhaltet
	private Map<String, ArrayList<MergedOffers>> mergedOffers = new TreeMap<String, ArrayList<MergedOffers>>();
	
	// Map, die die Summe der Abweichungen aller zusammengeführter Angebote nach Zeitslot beinhaltet
	private Map<String, double[]> sumLoadprofilesConfirmedOffers = new TreeMap<String, double[]>();
	
	// Map, die die Summe der Abweichungen aller auf dem Marktplatz verfügbaren bzw. vom Marktplatz zusammengeführten Angebote nach Zeitslot beinhaltet
	private Map<String, double[]> sumLoadprofilesAllOffers = new TreeMap<String, double[]>();
	
	// Map, die die Prognose für den jeweiligen Zeitslot beinhaltet
	private Map<String, double[]> prediction = new TreeMap<String, double[]>();
	
	// Map, die nach Startzeit alle möglichen Angebotskombinationen enthält
	private Map<String, ArrayList<PossibleMerge>> listPossibleMerges = new TreeMap<String, ArrayList<PossibleMerge>>();
	
	// Aktueller eexPreis
	private static final double eexPrice = 20;
	
	// Anzahl an 15-Minuten-Slots der Angebote
	private int numSlots = 4;
	
	// Aktuelle externe Netzauslastung (eigene Netzauslastung + externe Netzauslastung soll immer = 0 sein)
	private double externalVoltage = 0;

	private Marketplace() {
		// Setzt die Vorhersage für die nächsten 24h = 0
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		double[] zeroValues = {0, 0, 0, 0};
		for (int i=0; i<24; i++) {
			prediction.put(DateTime.ToString(now), zeroValues);
			now.add(Calendar.HOUR_OF_DAY, 1);
		}
	}

	public static Marketplace instance() {
		if (instance == null) {
			instance = new Marketplace();
		}
		return instance;
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
	 * Liefert die Summe aller auf dem Marktplatz vorhandenen Lastprofile eines Zeitraums
	 * @param time Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller auf dem Marktplatz vorhandenen Lastprofile des Zeitraums
	 */
	public double[] getSumAllOffers (GregorianCalendar time) {
		return sumLoadprofilesAllOffers.get(DateTime.ToString(time));
	}
	
	/**
	 * Liefert die Summe aller bestätigten Lastprofile eines Zeitraums
	 * @param time Gibt den Anfang des Zeitraums an
	 * @return Array mit der Summe aller bestätigten Lastprofile des Zeitraums
	 */
	public double[] getSumConfirmedOffers (GregorianCalendar time) {
		return sumLoadprofilesConfirmedOffers.get(DateTime.ToString(time));
	}

	/**
	 * Liefert
	 * 
	 * @param uuid
	 * @return
	 */
	public Offer getDemand(UUID uuid) {
		return demand.get(uuid);
	}

	public Offer getSupply(UUID uuid) {
		return supply.get(uuid);
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
		double[] sumDeviation = sumLoadprofilesAllOffers.get(DateTime.ToString(offer.getDate()));
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
			sumDeviation[i] += valuesLoadprofile[i];
		}
		sumLoadprofilesAllOffers.put(DateTime.ToString(offer.getDate()), sumDeviation);
		
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
		if (removeOffer == null) {
			removeOffer = demand.get(offer);
			if (removeOffer == null) {
				// TODO Fehlermeldung, dass Angebot nicht entfernt werden kann
				return;
			}
		}
		
		if (!merged) {
			// Entferne Summe des Lastprofiles von der Gesamtsumme aller Lastprofile
			String date = DateTime.ToString(removeOffer.getDate());
			double[] values = removeOffer.getAggLoadprofile().getValues();
			double[] valuesDeviation = sumLoadprofilesAllOffers.get(date);
			for (int i=0; i<numSlots; i++) {
				valuesDeviation[i] += values[i];
			}
			sumLoadprofilesAllOffers.put(date, valuesDeviation);
		}
		
		// Entferne Angebote aus listOffers
		String key = DateTime.ToString(removeOffer.getDate());
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(key);
		for (PossibleMerge current: possibleMerges) {
			Offer[] offers = current.getOffers();
			if (offers[0] == removeOffer || offers[1] == removeOffer) {
				possibleMerges.remove(current);
			}
		}
		if (possibleMerges.size() == 0) {
			listPossibleMerges.remove(key);
		}
		else {
			listPossibleMerges.put(key, possibleMerges);
		}
		
		// Entferne Angebot vom Marktplatz
		supply.remove(offer);
		demand.remove(offer);
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
		map.put("eexPrice", getEEXPrice());
		map.put("allDeviation", sumLoadprofilesAllOffers);
		map.put("mergedDeviation", sumLoadprofilesConfirmedOffers);

		return map;
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
		double[] currentDeviationConfirmed = chargeDeviationConfirmed(offer.getDate());
		double sumMostImprovement = 0;
		double sumCurrentDeviationConfirmed = 0;
		
		for (int i=0; i<numSlots; i++) {
			sumCurrentDeviationConfirmed += Math.abs(currentDeviationConfirmed[i]);
			sumMostImprovement += Math.abs(currentDeviationConfirmed[i] + valuesOffer[i]);
		}
		
		// Hole den Array aller bereits in listPossibleMerges hinterlegten possibleMerges 
		// für die Startzeit dateOffer
		ArrayList<PossibleMerge> possibleMergesOfDateOffer = listPossibleMerges.get(DateTime.ToString(offer.getDate()));
		if (possibleMergesOfDateOffer == null) {
			possibleMergesOfDateOffer = new ArrayList<PossibleMerge>();
		}
		
		for (UUID uuid: set) {
			Offer compareOffer;

			if (offerIsSupplyOffer) {
				compareOffer = supply.get(uuid);
			} else {
				compareOffer = demand.get(uuid);
			}
			
			double[] valuesCompareOffer = compareOffer.getAggLoadprofile().getValues();
			double[] newDeviation = new double[numSlots];
			double sumNewDeviation = 0;
			for (int i=0; i<numSlots; i++) {
				newDeviation[i] = currentDeviationConfirmed[i] + valuesOffer[i] + valuesCompareOffer[i];
				sumNewDeviation += Math.abs(newDeviation[i]);
			}
			
			if (sumNewDeviation < sumMostImprovement) {
				offerMostImprovement = compareOffer;
				sumMostImprovement = sumNewDeviation;
			}
			
			// Füge Kombination aller Angebote mit Offer für listPossibleMerges zum Array hinzu
			PossibleMerge possibleMerge = new PossibleMerge(offer, compareOffer);
			possibleMergesOfDateOffer.add(possibleMerge);
		}
		
		// Prüfe, ob hinzufügen der beiden Angebote mit geringster Abweichung
		// Annäherung an Prognose verbessert
		if (sumMostImprovement < sumCurrentDeviationConfirmed) {
			if (offer.equals(offerMostImprovement)) {
				confirmOffer(offer, offer.getPrice());
			}
			double[] noWorsening = {0, 0, 0, 0};
			mergeOffers(offer, offerMostImprovement, noWorsening);
			return true;
		}
		
		// Füge Array mit allen neuen Kombinationen zu listPossibleMerges hinzu
		listPossibleMerges.put(DateTime.ToString(offer.getDate()), possibleMergesOfDateOffer);
		return false;
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
	private void mergeOffers(Offer offer1, Offer offer2, double[] worsening) {
		String date = DateTime.ToString(offer1.getDate());
		if (! date.equals(offer2.getDate())) {
			// TODO Fehlermeldung, dass Angebote wegen unterschiedlichem Zeitraum 
			// nicht zusammengeführt werden können
			return;
		}
		
		double[] valuesOffer1 = offer1.getAggLoadprofile().getValues();
		double[] valuesOffer2 = offer1.getAggLoadprofile().getValues();
		double sumOffer1 = 0;
		double sumOffer2 = 0;
		boolean offer1Supply = true;
		boolean offer2Supply = true;

		for (int i = 0; i < 4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}

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

			// Berechne Preise pro kWh für Angebote
			price1 = price1 / sumOffer1;
			price2 = price2 / sumOffer2;
		}
		
		boolean worse = false;
		for (int i=0; i<numSlots; i++) {
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

			// Berechne Strafe pro kWh und füge sie zum Preis hinzu
			price1 = price1 - sumDeviation1 / sumOffer1;
			price2 = price2 - sumDeviation2 / sumOffer2;
		}

		// Lege zusammengeführte Angebote und Preise in der Historie ab
		MergedOffers merged = new MergedOffers(price1, price2, offer1, offer2);
		ArrayList<MergedOffers> array = mergedOffers.get(date);
		array.add(merged);
		mergedOffers.put(date, array);

		// Schicke Bestätigung für beide Angebote
		confirmOffer(offer1, price1);
		confirmOffer(offer2, price2);
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
		// Nehme Lastprofil von offer in die Summe der Lastprofile aller bestätigten Angebote auf
		double[] values = offer.getAggLoadprofile().getValues();
		double[] oldValues = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(offer.getDate()));
		for (int i=0; i<numSlots; i++) {
			values[i] += oldValues[i];
		}
		sumLoadprofilesConfirmedOffers.put(DateTime.ToString(offer.getDate()), values);
		
		ConfirmOffer confirmOffer = new ConfirmOffer(offer.getUUID(), newPrice);
		Set<UUID> set = offer.getAllLoadprofiles().keySet();

		for (UUID consumer : set) {
			// Sende confirmOffer an consumer
			RestTemplate rest = new RestTemplate();
			HttpEntity<ConfirmOffer> entity = new HttpEntity<ConfirmOffer>(confirmOffer, Application.getRestHeader());

			String url = "http://localhost:8080/consumers/" + consumer + "/offers/" + offer.getUUID()
					+ "/confirmByMarketplace";

			try {
				ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
			} catch (Exception e) {
			}
		}
	}
	
	public void BKV() {
		GregorianCalendar now = DateTime.now();
		int currentSlot = (int) Math.floor(now.get(Calendar.MINUTE)/15);		
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		String start = DateTime.ToString(now);
		
		double[] deviationConfirmed = chargeDeviationConfirmed(now);
		
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(start);
		PossibleMerge bestMerge;
		double[] improvementBestMerge;
		
		for (PossibleMerge possibleMerge: possibleMerges) {
			double[] values = possibleMerge.getValuesAggLoadprofile();
			double[] deviationIfConfirmed = new double[numSlots];
			boolean isBetter = false;
		}
	}
	
	/**
	 * Berechnet die Abweichung von sumLoadprofilesConfirmedOffers von der Prognose
	 * @param start Start der Stunde, fuer die die Abweichung berechnet wird
	 * @return Array mit der Abweichung von sumLoadprofilesConfirmed von der Prognose in 15-Minuten-Einheiten
	 */
	private double[] chargeDeviationConfirmed (GregorianCalendar start) {
		double[] deviationConfirmed = new double[numSlots];
		double[] currentPrediction = prediction.get(start);
		double[] currentConfirmed = sumLoadprofilesConfirmedOffers.get(start);
		for (int i=0; i<numSlots; i++) {
			deviationConfirmed[i] = currentPrediction[i] + currentConfirmed[i];
		}
		return deviationConfirmed;
	}
	
	
	/**
	 * Berechnet die Abweichung von sumLoadprofilesAllOffers von der Prognose
	 * @param start Start der Stunde, fuer die die Abweichung berechnet wird
	 * @return Array mit der Abwecihung von sumLoadprofilesAllOffers von der Prognose in 15-Minuten-Einheiten
	 */
	private double[] chargeDeviationAll (GregorianCalendar start) {
		double[] deviationAll = new double[numSlots];
		double[] currentPrediction = prediction.get(start);
		double[] currentConfirmed = sumLoadprofilesAllOffers.get(start);
		for (int i=0; i<numSlots; i++) {
			deviationAll[i] = currentPrediction[i] + currentConfirmed[i];
		}
		return deviationAll;
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
}
