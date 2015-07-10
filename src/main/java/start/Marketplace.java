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
	
	public void putOffer (Offer offer) {
		double[] valuesLoadprofile = offer.getAggLoadprofile().getValues();
		double sumLoadprofile = 0;
		for (int i=0; i<numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
		}
		if (sumLoadprofile > 0) {
			if (! findFittingOffer(offer, true)) {
				supply.put(offer.getUUID(), offer);
			}
		}
		else {
			if (! findFittingOffer(offer, false)) {
				demand.put(offer.getUUID(), offer);
			}
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
		double sumLeastDeviation = 0;
		Offer offerLeastDeviation = offer;
		double[] offerLoadprofile = offer.getAggLoadprofile().getValues();
		
		// Setzte leastDeviation initial auf aufsummiertes Lastprofil des Angebots
		for (int i=0; i<numSlots; i++) {
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
			}
			else {
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
		
		// Prüfe, ob geringste Abweichung klein genug, um Angebote zusammenzufuehren
		if (Math.abs(sumLeastDeviation) < 5 && offerLeastDeviation.getUUID() != offer.getUUID()) {
			mergeOffers(offer, offerLeastDeviation, leastDeviation);
			return true;
		}

		return false;
	}

	private void mergeOffers (Offer offer1, Offer offer2, double[] deviation) {		
		Offer offerDemand, offerSupply;
		
		double[] valuesOffer1 = offer1.getAggLoadprofile().getValues();
		double[] valuesOffer2 = offer1.getAggLoadprofile().getValues();
		double sumOffer1 = 0;
		double sumOffer2 = 0;
		double sumOfferDemand, sumOfferSupply;
		double[] valuesOfferDemand, valuesOfferSupply;
		for (int i=0; i<4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}
		if (sumOffer1<0 && sumOffer2>0) {
			offerDemand = offer1;
			sumOfferDemand = sumOffer1;
			valuesOfferDemand = valuesOffer1;
			
			offerSupply = offer2;
			sumOfferSupply = sumOffer2;
			valuesOfferSupply = valuesOffer2;
		}
		else if (sumOffer1 > 0 && sumOffer2 < 0) {
			offerDemand = offer2;
			sumOfferDemand = sumOffer2;
			valuesOfferDemand = valuesOffer2;
			
			offerSupply = offer1;
			sumOfferSupply = sumOffer1;
			valuesOfferSupply = valuesOffer1;
		}
		else {
			// TODO Throw exception
			return;
		}
		
		double priceDemand, priceSupply;
		
		// Lege Preis für beide Angebote ohne "Strafe" fest
		if (sumOfferDemand + sumOfferSupply == 0) {
			priceDemand = (offerDemand.getPrice()+offerSupply.getPrice())/2;
			priceSupply = priceDemand;
		}
		else {
			// Ermittle Gesamtpreis für beide Angebote
			priceDemand = sumOfferDemand*offerDemand.getPrice();
			priceSupply = sumOfferSupply*offerSupply.getPrice();
			// Ermittle Mittelwert von Betrag des Gesamtpreises beider Angebote
			double price = (Math.abs(priceDemand)+Math.abs(priceSupply))/2;
			// Weise den Gesamtpreisen den jeweiligen Mittelwert zu
			priceDemand = -price;
			priceSupply = price;
			// Berechne Preise pro kWh für Angebote
			priceDemand = priceDemand/sumOfferDemand;
			priceSupply = priceSupply/sumOfferSupply;
		}
		
		// Lege nun Gesamtstrafe für einzelne Angebote fest
		double sumDeviationDemand = 0;
		double sumDeviationSupply = 0;
		for (int i=0; i<numSlots; i++) {
			if (deviation[i] != 0) {
				if (Math.abs(deviation[i]) == Math.abs(valuesOfferDemand[i])
						+ Math.abs(valuesOfferSupply[i])) {
					sumDeviationDemand += Math.abs(valuesOfferDemand[i]);
					sumDeviationSupply += Math.abs(valuesOfferSupply[i]);
				}				
				else {
					if (Math.abs(valuesOfferDemand[i]) > Math.abs(valuesOfferSupply[i])) {
						sumDeviationDemand += Math.abs(valuesOfferDemand[i]);
					}
					else {
						sumDeviationSupply += Math.abs(valuesOfferSupply[i]);
					}
				}		
			}
		}
		sumDeviationDemand = sumDeviationDemand*eexPrice;
		sumDeviationSupply = sumDeviationSupply*eexPrice;
		
		// Berechne Strafe pro kWh und füge sie zum Preis hinzu
		priceDemand = priceDemand - sumDeviationDemand/sumOfferDemand;
		priceSupply = priceSupply - sumDeviationSupply/sumOfferSupply;
		
		// Schicke Bestätigung für beide Angebote
		confirmOffer(offerDemand, priceDemand);
		confirmOffer(offerSupply, priceSupply);
	}

	private void confirmOffer(Offer offer, double newPrice) {
		ConfirmOffer confirmOffer = new ConfirmOffer(offer.getUUID(), newPrice);
		Set<UUID> set = offer.getAllLoadprofiles().keySet();
		
		for (UUID consumer: set) {
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
}
