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
import Packet.ConfirmOffer;
import Util.MergedOffers;
import Util.DateTime;
import Util.Log;
import Util.PossibleMerge;
import Util.Negotiation;

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
	
	// Map, die alle Angebote beinhaltet, über deren Preis gerade mit den Consumern verhandelt wird
	private Map<UUID, Negotiation> negotiatingOffers = new TreeMap<UUID, Negotiation>();
	
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
	
	// Aktuell geduldete Abweichung in Prozent
	private double maxDeviation = 5;
	
	// Anzahl an 15-Minuten-Slots der Angebote
	private int numSlots = 4;
	
	// Startzeit des nächsten Slots, der geplant werden muss
	private GregorianCalendar nextSlot;

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
		this.nextSlot = now;
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
		double[] sumAllOffers = sumLoadprofilesAllOffers.get(DateTime.ToString(offer.getDate()));
		if (sumAllOffers == null) {
			sumAllOffers = new double[numSlots];
		}
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
			sumAllOffers[i] += valuesLoadprofile[i];
		}
		sumLoadprofilesAllOffers.put(DateTime.ToString(offer.getDate()), sumAllOffers);
		System.out.println("Summe Lastprofile: " +DateTime.ToString(offer.getDate()) + ": "+sumLoadprofilesAllOffers.get(DateTime.ToString(offer.getDate()))[0]);
		
		if (sumLoadprofile >= 0) {
			if (!findFittingOffer(offer, true)) {
				supply.put(offer.getUUID(), offer);
				System.out.println("Zu Supply hinzugefügt.");
			}
		} else {
			if (!findFittingOffer(offer, false)) {
				demand.put(offer.getUUID(), offer);
				System.out.println("Zu Demand hinzugefügt.");
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
		
		// Entferne Angebote aus listPossibleMerges
		removeFromPossibleMerges(removeOffer);
	}
	
	private void removeFromPossibleMerges (Offer offer) {
		String key = DateTime.ToString(offer.getDate());
		ArrayList<PossibleMerge> oldPossibleMerges = listPossibleMerges.get(key);
		ArrayList<PossibleMerge> newPossibleMerges = new ArrayList<PossibleMerge>();
		for (PossibleMerge current: oldPossibleMerges) {
			Offer[] offers = current.getOffers();
			if (! (offers[0].equals(offer) || offers[1].equals(offer))) {
				newPossibleMerges.add(current);
			}
		}
		if (newPossibleMerges.size() == 0) {
			listPossibleMerges.remove(key);
		}
		else {
			listPossibleMerges.put(key,  newPossibleMerges);
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
		System.out.println("Find fitting Offer");
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
		double[] perfectMatchConfirmed = chargeCurrentDeviationFromPrediction(offer.getDate());
		
		// Hole alle aktuellen Werte für Vorhersage
		double[] predictionCurrent = prediction.get(DateTime.ToString(offer.getDate()));
		double volumePredictionCurrent = 0;
		double[] deviationCurrentPrediction = chargeCurrentDeviationFromPrediction(offer.getDate());
		double sumDeviationCurrentPrediction = 0;
		double[] sumLoadprofilesCurrent = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(offer.getDate()));
		
		// Gibt die Abweichung von der Prognose für alle bestätigten Lastprofile und das aktuelle Angebot an
		double[] deviationOfferPrediction = new double[numSlots];
		double sumDeviationOfferPrediction = 0;
				
		for (int i=0; i<numSlots; i++) {
			volumePredictionCurrent += Math.abs(predictionCurrent[i]);
			deviationOfferPrediction[i] = predictionCurrent[i] - sumLoadprofilesCurrent[i] - valuesOffer[i];
			sumDeviationOfferPrediction += Math.abs(deviationOfferPrediction[i]);
			sumDeviationCurrentPrediction += Math.abs(deviationCurrentPrediction[i]);
		}
		
		// Gibt die Abweichung von der Prognose für deviationOffer-mostImprovement an
		double[] deviationMostImprovementPrediction = deviationOfferPrediction;
		double sumDeviationMostImprovementPrediction = sumDeviationOfferPrediction;
		
		
		// Hole den Array aller bereits in listPossibleMerges hinterlegten possibleMerges 
		// für die Startzeit dateOffer
		ArrayList<PossibleMerge> possibleMergesOfDateOffer = listPossibleMerges.get(DateTime.ToString(offer.getDate()));
		if (possibleMergesOfDateOffer == null) {
			possibleMergesOfDateOffer = new ArrayList<PossibleMerge>();
		}
		
		for (UUID uuid: set) {
			Offer compareOffer;

			if (offerIsSupplyOffer) {
				compareOffer = demand.get(uuid);
			} else {
				compareOffer = supply.get(uuid);
			}
			
			double[] valuesCompareOffer = compareOffer.getAggLoadprofile().getValues();
			double[] deviationPrediction = new double[numSlots];
			double sumDeviationPrediction = 0;
			for (int j=0; j<numSlots; j++) {
				deviationPrediction[j] = deviationOfferPrediction[j] - valuesCompareOffer[j];
				sumDeviationPrediction += Math.abs(deviationPrediction[j]);
			}
			
			if (sumDeviationPrediction < sumDeviationMostImprovementPrediction) {
				offerMostImprovement = compareOffer;
				sumDeviationMostImprovementPrediction = sumDeviationPrediction;
				deviationMostImprovementPrediction = deviationPrediction;
			}
			
			// Füge Kombination aller Angebote mit Offer für listPossibleMerges zum Array hinzu
			PossibleMerge possibleMerge = new PossibleMerge(offer, compareOffer);
			possibleMergesOfDateOffer.add(possibleMerge);
		}
		
		// Prüfe, ob hinzufügen der beiden Angebote mit geringster Abweichung
		// Annäherung an Prognose verbessert oder um weniger als 5 verschlechtert
		System.out.println("Maximale Abweichung: " +maxDeviation*0.01*volumePredictionCurrent);
		System.out.println("Tatsächliche Abweichung von Prognose: " +sumDeviationMostImprovementPrediction);
		if (sumDeviationMostImprovementPrediction < sumDeviationCurrentPrediction 
				|| sumDeviationMostImprovementPrediction < maxDeviation*volumePredictionCurrent) {
			if (offer.equals(offerMostImprovement)) {
				confirmOffer(offer, offer.getPrice());
				return true;
			}
			else {
				mergeFittingOffers(offer,offerMostImprovement);
				return true;
			}
		}
		
		// Füge Array mit allen neuen Kombinationen zu listPossibleMerges hinzu
		listPossibleMerges.put(DateTime.ToString(offer.getDate()), possibleMergesOfDateOffer);
		return false;
	}
	
	/**
	 * Führt Angebote zusammen, die die Annäherung an die Prognose verbessern bzw.
	 * nicht sehr viel verschlechtern.
	 * Die Angebote werden vom Marktplatz entfernt.
	 * Passt der Preis, so werden die Angebote direkt bestätigt. Passt der Preis nicht,
	 * wird eine Verhandlung mit den beiden Angeboten gestartet.
	 * @param offer1 Erstes Angebot, das zusammengeführt werden soll
	 * @param offer2 Zweites Angebot, das zusammengeführt werden soll
	 */
	private void mergeFittingOffers (Offer offer1, Offer offer2) {
		System.out.println("Merge Fitting Offers");
		if (! offer1.getDate().equals(offer2.getDate())) {
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
		System.out.println("Summen: " +sumOffer1+ ", " +sumOffer2);

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
		boolean pricesFit = (sumOffer1*offer1.getPrice() + sumOffer2*offer2.getPrice()) >= 0;		
		
		// Berechne neue Preise, falls Preise schon so passen, dass
		// Anpassung vom Marktplatz möglich ist, da es nur zu Verbesserungen
		// für die Consumer führt
		if (pricesFit) {
			price1 = sumOffer1*offer1.getPrice();
			price2 = sumOffer2*offer2.getPrice();
			
			if (! (Math.abs(price1) == Math.abs(price2))) {
				if (Math.abs(price1) > Math.abs(price2) && price1 > 0
						|| Math.abs(price1) < Math.abs(price2) && price1 < 0) {
					price1 = Math.abs(price2/sumOffer1);
					price2 = Math.abs(price2/sumOffer2);
				}
				else {
					price2 = Math.abs(price1/sumOffer2);
					price1 = Math.abs(price1/sumOffer1);
				}
			}
			
			// Schicke Bestätigung beider Angebote an Consumer
			confirmOffer(offer1, price1);
			confirmOffer(offer2, price2);
		}
		
		// Berechne Mittelwert, falls Preise nicht passen
		if (! pricesFit) {
			// Lege zu erreichende Preise für beide Angebote fest
			if (sumOffer1 + sumOffer2 == 0) {
				price1 = (offer1.getPrice() + offer2.getPrice()) / 2;
				price2 = price1;
			} else {
				// Ermittle Gesamtpreis für beide Angebote
				price1 = sumOffer1 * offer1.getPrice();
				price2 = sumOffer2 * offer2.getPrice();
				System.out.println("Einzelpreis: " +offer1.getPrice()+ ", " +offer2.getPrice());
				System.out.println("Gesamtpreis: " +price1+ ", " +price2);
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
				System.out.println("Mittelwert: " +price1+ ", " +price2);

				// Berechne Preise pro kWh für Angebote
				price1 = price1 / sumOffer1;
				price2 = price2 / sumOffer2;
			}
			
			// Erstelle neue Verhandlung und speichere Verhandlung unter negotiatingOffers ab
			Negotiation negotiation = new Negotiation(offer1, offer2, price1, price2, sumOffer1, sumOffer2);
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
	private void mergeOffers(Offer offer1, Offer offer2, double[] worsening) {
		System.out.println("Merge Offers");
		String date = DateTime.ToString(offer1.getDate());
		if (! offer1.getDate().equals(offer2.getDate())) {
			// TODO Fehlermeldung, dass Angebote wegen unterschiedlichem Zeitraum 
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
		System.out.println("Summen: " +sumOffer1+ ", " +sumOffer2);

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
			System.out.println("Einzelpreis: " +offer1.getPrice()+ ", " +offer2.getPrice());
			System.out.println("Gesamtpreis: " +price1+ ", " +price2);
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
			System.out.println("Mittelwert: " +price1+ ", " +price2);

			// Berechne Preise pro kWh für Angebote
			price1 = price1 / sumOffer1;
			price2 = price2 / sumOffer2;
		}
		System.out.println("Neuer Preis: " +price1+ ", " +price2);
		
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
			System.out.println("sumDeviation: " +sumDeviation1+ ", " +sumDeviation2);

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
		//confirmOffer(offer1, price1);
		System.out.println("Preis Angebot 1: " +price1);
		//confirmOffer(offer2, price2);
		System.out.println("Preis Angebot 2: " +price2);
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
		if (oldValues == null) {
			oldValues = new double[numSlots];
			for (int i=0; i<numSlots; i++) {
				oldValues[i] = 0;
			}
		}
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
	
	private void mergeAllGoodOffers (GregorianCalendar slot) {
		// Hole die Liste aller möglichen Merges
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(DateTime.ToString(slot));
		// Erstelle eine neue Liste, in welcher alle gerade zusammengefügten Angebote gespeichert werden
		ArrayList<Offer> offersJustMerged = new ArrayList<Offer>();
		
		// Gehe die Liste aller möglichen Merges durch und führe die guten zusammen 
		for (PossibleMerge possibleMerge: possibleMerges) {
			Offer[] offers = possibleMerge.getOffers();
			// Wenn eines der Angebote in offersJustMerged ist, wurde es bereits bei einem vor-
			// herigen Schleifendurchlauf zusammengeführt und bestätigt und steht nicht mehr
			// zur Verfügung
			if (offersJustMerged.contains(offers[0]) || offersJustMerged.contains(offers[1])) {
				continue;
			}
			double[] loadprofile = possibleMerge.getValuesAggLoadprofile();
			double[] predictionCurrent = prediction.get(DateTime.ToString(possibleMerge.getDate()));
			double[] sumLPCurrent = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(possibleMerge.getDate()));
			double[] deviationOld = chargeCurrentDeviationFromPrediction(possibleMerge.getDate());
			double[] deviationNew = new double[numSlots];
			double sumDeviationNew = 0;
			double sumDeviationOld = 0;
			double volumePrediction = 0;
			for (int i=0; i<numSlots; i++) {
				deviationNew[i] = predictionCurrent[i] - sumLPCurrent[i] - loadprofile[i];
				sumDeviationNew += Math.abs(deviationNew[i]);
				sumDeviationOld += Math.abs(deviationOld[i]);
				volumePrediction += Math.abs(predictionCurrent[i]);
			}
			if (sumDeviationNew < sumDeviationOld
					|| sumDeviationNew < volumePrediction) {
				offersJustMerged.add(offers[0]);
				offersJustMerged.add(offers[1]);
				mergeFittingOffers(offers[0], offers[1]);
			}
		}
	}
	
	private void matchNextSlot() {
		double[] deviationAll = chargeDeviationAll(nextSlot);
		double sumDeviationAll = 0;
		for (int i=0; i<numSlots; i++) {
			sumDeviationAll += Math.abs(deviationAll[i]);
		}
		if (make(sumDeviationAll)) {
			// TODO Alle Schritte für Make
			// TODO Wie soll sich hier die Preisgestaltung abspielen? - Nachfrage nach Änderungen dauert zu lang?
			mergeAllGoodOffers(nextSlot);
			
			ArrayList<PossibleMerge> remainingMerges = listPossibleMerges.get(DateTime.ToString(nextSlot));
			
		}
		else {
			ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(DateTime.ToString(nextSlot));

			// Sortiere possibleMerges nach Preisunterschied (zuerst mit Gewinn, dann mit Verlust für BKV)
			Collections.sort(possibleMerges);
			
			// Erstelle Array, in welchem alle bereits bestätigten Angebote gesammelt werden
			ArrayList<Offer> confirmedOffers = new ArrayList<Offer>();
			
			// Bestätige der Reihe nach alle possibleMerges, wenn die Angebote nicht schon bestätigt wurden
			for (PossibleMerge possibleMerge: possibleMerges) {
				Offer[] offers = possibleMerge.getOffers();
				if (confirmedOffers.contains(offers[0]) || confirmedOffers.contains(offers[1])) {
					continue;
				}
				else {
					confirmedOffers.add(offers[0]);
					confirmedOffers.add(offers[1]);
					// Berechne Verschlechterung, die durch das Lastprofil von possibleMerge entsteht
					double[] worsening = chargeWorseningWithLoadprofile(possibleMerge.getDate(), possibleMerge.getValuesAggLoadprofile());
					
					mergeOffers(offers[0], offers[1], worsening);
				}
			}
			for (Offer current: confirmedOffers) {
				removeFromPossibleMerges(current);
			}
			Set<UUID> demandUUIDs = demand.keySet();
			Set<UUID> supplyUUIDs = supply.keySet();
			boolean remainingDemand = demandUUIDs.size() > 0;
			boolean remainingSupply = supplyUUIDs.size() > 0;
			if (remainingDemand && remainingSupply) {
				System.out.println("Das darf nicht sein");
			}
			else if (remainingDemand) {
				for (UUID demandUUID: demandUUIDs) {
					// TODO wie wird Preis hier festgelegt???
					double price = eexPrice;
					confirmOffer(demand.get(demandUUID), price);
				}
			}
			else if (remainingSupply) {
				for (UUID supplyUUID: supplyUUIDs) {
					// TODO wie wird Preis hier festgelegt???
					double price = eexPrice;
					confirmOffer(supply.get(supplyUUID), price);
				}
			}
		}		
	}
	
	private double[] chargeWorseningWithLoadprofile (GregorianCalendar date, double[] loadprofile) {
		double[] currentDeviation = chargeCurrentDeviationFromPrediction(date);
		double[] newDeviation = new double[numSlots];
		double[] worsening = new double[numSlots];
		for (int i=0; i<numSlots; i++) {
			newDeviation[i] = -(currentDeviation[i] - loadprofile[i]);
			if (Math.abs(newDeviation[i]) > Math.abs(currentDeviation[i])) {
				worsening[i] = Math.abs(newDeviation[i]) - Math.abs(currentDeviation[i]);
				if (newDeviation[i] < 0) {
					worsening[i] = -worsening[i];
				}
			}
			else {
				worsening[i] = 0;
			}
		}
		return worsening;
	}
	
	public boolean make(double sumDeviationAll) {
		return true;
	}
	
	private double[] chargeDeviationLoadprofilePerfectMatch1(double[] loadprofile, GregorianCalendar start) {
		double[] deviationLoadprofile = new double[numSlots];
		double[] perfectMatch = chargeCurrentDeviationFromPrediction(start);
		for (int i=0; i<numSlots; i++) {
			deviationLoadprofile[i] = loadprofile[i] - perfectMatch[i] ;
		}
		return deviationLoadprofile;
	}
	
	public void BKV() {
		GregorianCalendar now = DateTime.now();
		if (now.get(Calendar.HOUR_OF_DAY) == nextSlot.get(Calendar.HOUR_OF_DAY) && now.get(Calendar.MINUTE) >= 45) {
			matchNextSlot();
			nextSlot.add(Calendar.HOUR_OF_DAY, 1);
		}
		
		// Prüfe, ob Angebot für aktuellen, schon verhandelten Slot vorliegen
		// TODO Was passiert mit diesen Angeboten??
		int currentSlot = (int) Math.floor(now.get(Calendar.MINUTE)/15);
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		String start = DateTime.ToString(now);
				
		double[] perfectMatch = chargeCurrentDeviationFromPrediction(now);
		
		ArrayList<PossibleMerge> possibleMerges = listPossibleMerges.get(start);
		PossibleMerge bestMerge;
		double[] deviationBestMergeFromPerfectMatch;
		double sumDeviationBestMergeFromPerfectMatch;
		
		for (PossibleMerge possibleMerge: possibleMerges) {
			double[] values = possibleMerge.getValuesAggLoadprofile();
			double[] deviationFromPerfectMatch = new double[numSlots];
			double sumDeviationFromPerfectMatch = 0;
			for (int i=0; i<numSlots; i++) {
				deviationFromPerfectMatch[i] = values[i] - perfectMatch[i];
				sumDeviationFromPerfectMatch += Math.abs(deviationFromPerfectMatch[i]);
			}
			// Wenn die Abweichung kleiner 5, werden die Angebote zusammengeführt
			if (sumDeviationFromPerfectMatch < 5) {
				Offer[] offers = possibleMerge.getOffers();
				mergeOffers(offers[0], offers[1], deviationFromPerfectMatch);
				// TODO aktualisiere perfectMatch und noch mehr???
			}
			else {
				// TODO 
			}
		}
	}
	
	/**
	 * Berechnet die Abweichung von sumLoadprofilesConfirmedOffers von der Prognose.
	 * @param start Start der Stunde, fuer die die Abweichung berechnet wird
	 * @return Array mit der Abweichung von sumLoadprofilesConfirmed von der Prognose in 15-Minuten-Einheiten
	 */
	private double[] chargeCurrentDeviationFromPrediction (GregorianCalendar start) {
		double[] perfectMatch = new double[numSlots];
		double[] currentPrediction = prediction.get(DateTime.ToString(start));
		double[] currentConfirmed = sumLoadprofilesConfirmedOffers.get(DateTime.ToString(start));
		if (currentConfirmed == null) {
			currentConfirmed = new double[numSlots];
			for (int i=0; i<numSlots; i++) {
				currentConfirmed[i] = 0;
			}
		}
		for (int i=0; i<numSlots; i++) {
			perfectMatch[i] = currentPrediction[i] - currentConfirmed[i];
			System.out.println("PerfektMatch: " +perfectMatch[i]);
		}
		return perfectMatch;
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
			deviationAll[i] = currentPrediction[i] - currentConfirmed[i];
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
	
	/**
	 * Wird von Negotiation aufgerufen, wenn eine Verhandlung beendet ist.
	 * Ist die Verhandlung erfolgreich beendet worden, so werden beide Angebote zu den neuen Preisen bestätigt.
	 * Ist die Verhandlung nicht erfolgreich beendet worden, so werden beide Angebote wieder auf den Marktplatz gestellt.
	 * Die Negotiation wird aus der Liste negotiatingOffers entfernt.
	 * @param negotiation Verhandlung, die beendet wurde
	 * @param newPrice1 Neu verhandelter Preis für Angebot1
	 * @param newPrice2 Neu verhandelter Preis für Angebot2
	 * @param successfull Gibt an, ob die Verhandlung erfolgreich beendet wurde
	 */
	public void endOfNegotiation(UUID negotiation, double newPrice1, double newPrice2, boolean successfull) {
		Negotiation currentNegotiation = negotiatingOffers.get(negotiation);
		Offer[] offers = currentNegotiation.getOffers();

		if (successfull) {
			confirmOffer(offers[0], newPrice1);
			confirmOffer(offers[1], newPrice2);
		}
		else {
			putOffer(offers[0]);
			putOffer(offers[1]);
			// TODO Sollen verhandelte Preise gespeichert werden???
		}
		negotiatingOffers.remove(negotiation);
	}
}
