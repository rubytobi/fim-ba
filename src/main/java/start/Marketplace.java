package start;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import Entity.Offer;
import Packet.ConfirmOffer;
import Util.MergedOffers;
import Util.DateTime;
import Util.Log;

public class Marketplace {
	private static Marketplace instance = null;
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();
	private Map<UUID, MergedOffers> mergedOffers = new HashMap<UUID, MergedOffers>();
	private static final double eexPrice = 20;
	private int numSlots = 4;

	private Marketplace() {

	}

	public static Marketplace instance() {
		if (instance == null) {
			instance = new Marketplace();
		}
		return instance;
	}

	public static double getEEXPrice() {
		return Marketplace.eexPrice;
	}

	public Offer getDemand(UUID uuid) {
		return demand.get(uuid);
	}

	public Offer getSupply(UUID uuid) {
		return supply.get(uuid);
	}

	public void putOffer(Offer offer) {
		double[] valuesLoadprofile = offer.getAggLoadprofile().getValues();
		double sumLoadprofile = 0;
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
		}
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

	public void removeOffer(UUID offer) {
		supply.remove(offer);
		demand.remove(offer);
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("numberOfDemands", demand.size());
		map.put("numberOfSupplies", supply.size());
		map.put("eexPrice", getEEXPrice());

		return map;
	}

	public boolean findFittingOffer(Offer offer, boolean offerIsSupplyOffer) {
		Set<UUID> set;
		if (offerIsSupplyOffer) {
			set = supply.keySet();
		} else {
			set = demand.keySet();
		}
		if (set == null) {
			return false;
		}
		int numSlots = 4;
		double sumLeastDeviation = 0;
		Offer offerLeastDeviation = offer;
		double[] offerLoadprofile = offer.getAggLoadprofile().getValues();

		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des
		// Angebots
		for (int i = 0; i < numSlots; i++) {
			sumLeastDeviation += Math.abs(offerLoadprofile[i]);
		}

		double[] currentDeviation;
		double[] leastDeviation = offerLoadprofile;
		double sumCurrentDeviation;

		// Suche aus allen Angeboten des Marktplatzes, das mit der geringsten
		// Abweichung
		for (UUID uuid : set) {
			Offer compareOffer;

			if (offerIsSupplyOffer) {
				compareOffer = supply.get(uuid);
			} else {
				compareOffer = demand.get(uuid);
			}

			// Prüfe, ob Angebote für gleichen Zeitraum sind, gehe sonst zum
			// nächsten Angebot
			if (DateTime.ToString(compareOffer.getAggLoadprofile().getDate()) == DateTime
					.ToString(offer.getAggLoadprofile().getDate())) {
				double[] compareLoadprofile = compareOffer.getAggLoadprofile().getValues();
				currentDeviation = new double[4];
				sumCurrentDeviation = 0;

				for (int i = 0; i < numSlots; i++) {
					currentDeviation[i] = offerLoadprofile[i] + compareLoadprofile[i];
					sumCurrentDeviation += Math.abs(currentDeviation[i]);
				}
				if (sumCurrentDeviation < sumLeastDeviation) {
					sumLeastDeviation = sumCurrentDeviation;
					leastDeviation = currentDeviation;
					offerLeastDeviation = compareOffer;
				}
			}
		}

		// Prüfe, ob geringste Abweichung klein genug, um Angebote
		// zusammenzufuehren
		if (Math.abs(sumLeastDeviation) < 5 && offerLeastDeviation.getUUID() != offer.getUUID()) {
			mergeOffers(offer, offerLeastDeviation, leastDeviation);
			return true;
		}

		return false;
	}

	private void mergeOffers(Offer offer1, Offer offer2, double[] deviation) {
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

		// Prüfe, ob die beiden Angebote demand oder supply sind und
		// Lösche beide aus der jeweiligen Hashmap
		if (sumOffer1 < 0) {
			offer1Supply = false;
			demand.remove(offer1.getUUID());
		} else {
			supply.remove(offer1.getUUID());
		}
		if (sumOffer2 < 0) {
			offer2Supply = false;
			demand.remove(offer2.getUUID());
		} else {
			supply.remove(offer2.getUUID());
		}

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

		// Lege nun Gesamtstrafe für einzelne Angebote fest
		double sumDeviation1 = 0;
		double sumDeviation2 = 0;
		for (int i = 0; i < numSlots; i++) {
			if (deviation[i] != 0) {
				if (Math.abs(deviation[i]) == Math.abs(valuesOffer1[i]) + Math.abs(valuesOffer2[i])) {
					sumDeviation1 += Math.abs(valuesOffer1[i]);
					sumDeviation2 += Math.abs(valuesOffer2[i]);
				} else {
					if (Math.abs(valuesOffer1[i]) > Math.abs(valuesOffer2[i])) {
						sumDeviation1 += Math.abs(deviation[i]);
					} else {
						sumDeviation2 += Math.abs(deviation[i]);
					}
				}
			}
		}
		sumDeviation1 = sumDeviation1 * eexPrice;
		sumDeviation2 = sumDeviation2 * eexPrice;

		// Berechne Strafe pro kWh und füge sie zum Preis hinzu
		price1 = price1 - sumDeviation1 / sumOffer1;
		price2 = price2 - sumDeviation2 / sumOffer2;

		// TODO Lege zusammengeführte Angebote und Preise in der Historie ab

		// Schicke Bestätigung für beide Angebote
		confirmOffer(offer1, price1);
		confirmOffer(offer2, price2);
	}

	private void confirmOffer(Offer offer, double newPrice) {
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
				Log.i(url);
				Log.e(e.getMessage());
			}
		}
	}

	public void ping() {
		Set<UUID> set = demand.keySet();

		if (set.size() == 0) {
			System.out.println("ping: @ marketplace " + DateTime.timestamp() + " no demand offers in Marketplace");
		}
		for (UUID uuidOffer : set) {
			Offer offer = demand.get(uuidOffer);
			System.out.println("ping: @ marketplace " + DateTime.timestamp() + " confirm demand offer " + uuidOffer);
			confirmOffer(offer, offer.getAggLoadprofile().getMinPrice());
			demand.remove(offer.getUUID());
			break;
		}
	}
}