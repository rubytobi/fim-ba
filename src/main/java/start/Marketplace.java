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
	
	public boolean findFittingOffer (Offer offer, boolean offerIsSupplyOffer) {
		Set<UUID> set;
		if (offerIsSupplyOffer) {
			set = supply.keySet();
		}
		else {
			set = demand.keySet();
		}
		if (set == null) {
			return false;
		}
		int numSlots = 4;
		double sumLeastDeviation = 0;
		Offer offerLeastDeviation = offer;
		double[] offerLoadprofile = offer.getAggLoadprofile().getValues();
		
		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des Verbrauchers
		for (int i=0; i<numSlots; i++) {
			sumLeastDeviation += Math.abs(offerLoadprofile[i]);
		}
		
		double[] currentDeviation;
		double[] leastDeviation = offerLoadprofile;
		
		// Suche aus allen Angeboten des Marktplatzes, das mit der geringsten Abweichung
		for (UUID uuid: set) {
			Offer compareOffer;
			if (offerIsSupplyOffer) {
				compareOffer = supply.get(uuid);
			}
			else {
				compareOffer = demand.get(uuid);
			}
			
			// Prüfe, ob Angebote für gleichen Zeitraum sind, gehe sonst zum nächsten Angebot
			if (DateTime.ToString(compareOffer.getAggLoadprofile().getDate()) == 
					DateTime.ToString(offer.getAggLoadprofile().getDate())) {
				double[] compareLoadprofile = compareOffer.getAggLoadprofile().getValues();
				currentDeviation = new double[4];
				double sumCurrentDeviation = 0;
			
				for (int i=0; i<numSlots; i++) {
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
		
		// Prüfe, ob geringste Abweichung klein genug, um Angebote zusammenzufuehren
		if (Math.abs(sumLeastDeviation) < 5 && offerLeastDeviation.getUUID() != offer.getUUID()) {
			mergeOffers(offer, offerLeastDeviation, leastDeviation, sumLeastDeviation);
			return true;
		}
		
		return false;
	}

	private void mergeOffers (Offer offer1, Offer offer2, double[] deviation, double sumDeviation) {		
		Offer offerDemand, offerSupply;
		if(offer1.getAggLoadprofile().getPrice() < 0) {
			offerDemand = offer1;
			offerSupply = offer2;
		}
		else {
			offerSupply = offer1;
			offerDemand = offer2;
		}

		double sumOfferDemand = 0;
		double sumOfferSupply = 0;
		double priceDemand, priceSupply;
		for (int i=0; i<numSlots; i++) {
			sumOfferDemand += offerDemand.getAggLoadprofile().getValues()[i];
			sumOfferSupply += offerSupply.getAggLoadprofile().getValues()[i];
		}
		if (sumOfferDemand + sumOfferSupply == 0) {
			priceDemand = (offerDemand.getPrice()+offerSupply.getPrice())/2;
			priceSupply = priceDemand;
		}
		else {
			priceDemand = sumOfferDemand*offerDemand.getPrice();
			priceSupply = sumOfferSupply*offerSupply.getPrice();
			
			double price = (Math.abs(priceDemand)+Math.abs(priceSupply))/2;
			if (priceDemand < 0) {
				priceDemand = -price;
			}
			else {
				priceDemand = price;
			}
			if (priceSupply < 0) {
				priceSupply = -price;
			}
			else {
				priceDemand = price;
			}
			
			priceDemand = priceDemand/sumOfferDemand;
			priceSupply = priceSupply/sumOfferSupply;
		}
		
		
	}
	
	private void confirmOffer (Offer offer, UUID consumer, double newPrice) {
		ConfirmOffer confirmOffer = new ConfirmOffer(offer.getUUID(), newPrice);
		
		// Sende confirmOffer an consumer
		RestTemplate rest = new RestTemplate();
		HttpEntity<ConfirmOffer> entity = new HttpEntity<ConfirmOffer>(confirmOffer, Application.getRestHeader());

		String url = "http://localhost:8080/consumers/" +consumer+ "/offers/" +offer.getUUID()+"/confirmByMarketplace";

		try {
			ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
		} catch (Exception e) {
			Log.i(url);
			Log.e(e.getMessage());
		}
	}
}
