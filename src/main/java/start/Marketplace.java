package start;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Set;

import Entity.Offer;
import Entity.Consumer;
import jersey.repackaged.com.google.common.collect.Lists;

public class Marketplace {
	private static Marketplace instance = null;
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();
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
		if (!findFittingSupplyOffer(demandOffer)) {
			demand.put(demandOffer.getUUID(), demandOffer);
		}
	}

	public void putSupply(Offer supplyOffer) {
		supply.put(supplyOffer.getUUID(), supplyOffer);
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("numberOfDemands", demand.size());
		map.put("numberOfSupplies", supply.size());
		map.put("eexPrice", getEEXPrice());

		return map;
	}
	
	public boolean findFittingSupplyOffer (Offer demandOffer) {
		Set<UUID> set = supply.keySet();
		if (set == null) {
			return false;
		}
		int numSlots = 4;
		double leastDeviation = 0;
		Offer offerLeastDeviation = demandOffer;
		double[] demandLoadprofile = demandOffer.getAggLoadprofile().getValues();
		
		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des Verbrauchers
		for (int i=0; i<numSlots; i++) {
			leastDeviation = leastDeviation + demandLoadprofile[i];
		}
		
		// Suche aus allen Angeboten des Marktplatzes, das mit der geringsten Abweichung
		for (UUID uuid: set) {
			Offer supplyOffer = supply.get(uuid);
			double[] supplyLoadprofile = supplyOffer.getAggLoadprofile().getValues();
			double currentDeviation = 0;
			
			for (int i=0; i<numSlots; i++) {
				currentDeviation = supplyLoadprofile[i] - demandLoadprofile[i];
			}
			if (currentDeviation < leastDeviation) {
				leastDeviation = currentDeviation;
				offerLeastDeviation = supplyOffer;
			}
		}
		
		// Prüfe, ob geringste Abweichung klein genug, um Angebote zusammenzufuehren
		if (leastDeviation < 5 && offerLeastDeviation.getUUID() != demandOffer.getUUID()) {
			mergeOffers(demandOffer, offerLeastDeviation);
			return true;
		}
		return false;
	}
	
	public boolean findFittingDemandOffer (Offer supplyOffer) {
		Set<UUID> set = demand.keySet();
		if (set == null) {
			return false;
		}
		int numSlots = 4;
		double leastDeviation = 0;
		Offer offerLeastDeviation = supplyOffer;
		double[] supplyLoadprofile = supplyOffer.getAggLoadprofile().getValues();
		
		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des Verbrauchers
		for (int i=0; i<numSlots; i++) {
			leastDeviation = leastDeviation + supplyLoadprofile[i];
		}
		
		// Suche aus allen Angeboten des Marktplatzes, das mit der geringsten Abweichung
		for (UUID uuid: set) {
			Offer demandOffer = demand.get(uuid);
			double[] demandLoadprofile = demandOffer.getAggLoadprofile().getValues();
			double currentDeviation = 0;
			
			for (int i=0; i<numSlots; i++) {
				currentDeviation = supplyLoadprofile[i] - demandLoadprofile[i];
			}
			if (currentDeviation < leastDeviation) {
				leastDeviation = currentDeviation;
				offerLeastDeviation = demandOffer;
			}
		}
		
		// Prüfe, ob geringste Abweichung klein genug, um Angebote zusammenzufuehren
		if (leastDeviation < 5 && offerLeastDeviation.getUUID() != supplyOffer.getUUID()) {
			mergeOffers(offerLeastDeviation, supplyOffer);
			return true;
		}
		return false;
	}
	
	private void mergeOffers (Offer offer1, Offer offer2) {		
		// Entferne beide Angebote vom Marktplatz
		demand.remove(offer1.getUUID());
		demand.remove(offer2.getUUID());
		supply.remove(offer1.getUUID());
		supply.remove(offer2.getUUID());
		
		// Berechne neuen Preis und jeweils prozentuale Abweichung vom alten Preis		
		double price1 = Math.abs(offer1.getPrice());
		double price2 = Math.abs(offer2.getPrice());
		double mergedPrice = (price1 + price2)/2;
		double deviationPrice1 = (mergedPrice - price1)/price1;
		double deviationPrice2 = (mergedPrice - price2)/price2;
		
		Set<UUID> consumers1 = offer1.getAllLoadprofiles().keySet();
		Set<UUID> consumers2 = offer2.getAllLoadprofiles().keySet();
		
		// Berechne anteilige Einzelpreise für alle Consumer von offer1 und benachrichtige sie
		for (UUID uuid : consumers1) {
			Loadprofile loadprofile1 = offer1.getAllLoadprofiles().get(uuid);
			double newPrice = loadprofile1.getPrice()*deviationPrice1;
			confirmOffer(offer1, uuid, newPrice);
		}
		
		// Berechne anteilige Einzelpreise für alle Consumer von offer2 und benachrichtige sie
		for (UUID uuid : consumers2) {
			Loadprofile supplyLoadprofile = offer2.getAllLoadprofiles().get(uuid);
			double newPrice = supplyLoadprofile.getPrice()*deviationPrice2;
			confirmOffer(offer2, uuid, newPrice);
		}
		
		// Lege Zusammenführung der beiden Angebote in 
	}
	
	private void confirmOffer (Offer offer, UUID consumer, double newPrice) {
		// TODO Sende Bestätigung für offer an consumer mit newPrice
	}
	
	private static void mapToString(HashMap<String, double[]> map) {
		Set<String> set = map.keySet();

		for (String s : set) {
			System.out.println("##" + s);
			System.out.println(Arrays.toString(map.get(s)));
		}
	}

}
