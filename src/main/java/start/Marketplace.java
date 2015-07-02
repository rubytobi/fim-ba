package start;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import Entity.Offer;
import jersey.repackaged.com.google.common.collect.Lists;

public class Marketplace {
	private static Marketplace instance = null;
	private Map<UUID, Offer> demand = new HashMap<UUID, Offer>();
	private Map<UUID, Offer> supply = new HashMap<UUID, Offer>();

	private class IncreasingComperator implements Comparator<Offer> {

		@Override
		public int compare(Offer a, Offer b) {
			return Double.compare(a.getPrice(), b.getPrice());
		}

	}

	private class DecreasingComperator implements Comparator<Offer> {

		@Override
		public int compare(Offer a, Offer b) {
			return -1 * Double.compare(a.getPrice(), b.getPrice());
		}

	}

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

	public void putDemand(Offer offer) {
		demand.put(offer.getUUID(), offer);
	}

	public void putSupply(Offer offer) {
		supply.put(offer.getUUID(), offer);
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("numberOfDemands", demand.size());
		map.put("numberOfSupplies", supply.size());
		map.put("eexPrice", getEEXPrice());

		return map;
	}

}
