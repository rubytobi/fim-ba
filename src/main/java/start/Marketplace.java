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

import Entity.Consumer;
import Entity.Offer;
import Packet.ConfirmOffer;
import Packet.OfferNotification;
import Util.MergedOffers;
import Util.DateTime;
import Util.Log;
import jersey.repackaged.com.google.common.collect.Lists;

public class Marketplace {
	private static Marketplace instance = null;
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();
	private Map<UUID, MergedOffers> mergedOffers = new HashMap<UUID, MergedOffers>();
	private static final double eexPrice = 20;

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

	public void ping() {
	}

	public void putDemand(Offer demandOffer) {
		if (!findFittingOffer(demandOffer, false)) {
			demand.put(demandOffer.getUUID(), demandOffer);
		}
	}

	public void putSupply(Offer supplyOffer) {
		if (!findFittingOffer(supplyOffer, true)) {
			supply.put(supplyOffer.getUUID(), supplyOffer);
		}
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
		double leastDeviation = 0;
		Offer offerLeastDeviation = offer;
		double[] offerLoadprofile = offer.getAggLoadprofile().getValues();

		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des
		// Verbrauchers
		for (int i = 0; i < numSlots; i++) {
			leastDeviation = leastDeviation + offerLoadprofile[i];
		}

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
				double[] supplyLoadprofile = compareOffer.getAggLoadprofile().getValues();
				double currentDeviation = 0;

				for (int i = 0; i < numSlots; i++) {
					currentDeviation = supplyLoadprofile[i] - offerLoadprofile[i];
				}
				if (currentDeviation < leastDeviation) {
					leastDeviation = currentDeviation;
					offerLeastDeviation = compareOffer;
				}
			}
		}

		// Prüfe, ob geringste Abweichung klein genug, um Angebote
		// zusammenzufuehren
		if (leastDeviation < 5 && offerLeastDeviation.getUUID() != offer.getUUID()) {
			mergeOffers(offer, offerLeastDeviation);
			return true;
		}

		return false;
	}

	private void mergeOffers(Offer offer1, Offer offer2) {
		// Entferne beide Angebote vom Marktplatz
		demand.remove(offer1.getUUID());
		demand.remove(offer2.getUUID());
		supply.remove(offer1.getUUID());
		supply.remove(offer2.getUUID());

		// Berechne neuen Preis und jeweils prozentuale Abweichung vom alten
		// Preis
		double price1 = Math.abs(offer1.getPrice());
		double price2 = Math.abs(offer2.getPrice());
		double mergedPrice = (price1 + price2) / 2;
		double deviationPrice1 = (mergedPrice - price1) / price1;
		double deviationPrice2 = (mergedPrice - price2) / price2;

		Set<UUID> consumers1 = offer1.getAllLoadprofiles().keySet();
		Set<UUID> consumers2 = offer2.getAllLoadprofiles().keySet();

		// Berechne anteilige Einzelpreise für alle Consumer von offer1 und
		// benachrichtige sie
		for (UUID uuid : consumers1) {
			Loadprofile loadprofile1 = offer1.getAllLoadprofiles().get(uuid);
			double newPrice = loadprofile1.getMinPrice() * deviationPrice1;
			confirmOffer(offer1, uuid, newPrice);
		}

		// Berechne anteilige Einzelpreise für alle Consumer von offer2 und
		// benachrichtige sie
		for (UUID uuid : consumers2) {
			Loadprofile supplyLoadprofile = offer2.getAllLoadprofiles().get(uuid);
			double newPrice = supplyLoadprofile.getMinPrice() * deviationPrice2;
			confirmOffer(offer2, uuid, newPrice);
		}

		// Lege Zusammenführung der beiden Angebote ab
		MergedOffers mergedOffer = new MergedOffers(mergedPrice, offer1, offer2);
		mergedOffers.put(mergedOffer.getUUID(), mergedOffer);
	}

	private void confirmOffer(Offer offer, UUID consumer, double newPrice) {
		ConfirmOffer confirmOffer = new ConfirmOffer(offer.getUUID(), newPrice);

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
