package Entity;

import Entity.Offer;
import java.util.SortedMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.UUID;

public class Marketplace {
	private double eex;
	private UUID uuid;
	private Offer cheapestConsumer, cheapestProducer;
	
	// TODO alle Angebote, sortiert nach Preis, mit Information ob Angebot Erzeuger oder Verbraucher über Preis:
	// negativer Preis Verbraucher, positiver Preis Erzeuger
	// PREIS ALS KEY GEHT NICHT!!!!!
	private ArrayList<Offer> offersConsumer = new ArrayList<Offer>();
	private TreeMap<UUID, Offer> offersProducer = new TreeMap<UUID, Offer>();
	private TreeMap<String, Double> testProducer = new TreeMap<String, Double>();
	private TreeMap<Double, String> testConsumer = new TreeMap<Double, String>();
	
	public Marketplace (double eex) {
		this.eex = eex;
		testProducer.put("Producer A", 0.0);
		testProducer.put("Producer D", 0.0);
		testProducer.put("Producer C", 0.0);
		testProducer.put("Producer", 0.0);
		
		testConsumer.put(-0.1, "Consumer A");
		testConsumer.put(-4.0, "Consumer C");
		testConsumer.put(-2.0, "Consumer B");
	}
	
	public double getEex() {
		return eex;
	}
	
//	public TreeMap<Double, Offer> getOffersConsumer() {
//		return offersConsumer;
//	}
//	
//	public TreeMap<Double, Offer> getOffersProducer() {
//		return offersProducer;
//	}
	
	public String getCheapestOfferConsumer() {
		double key = testConsumer.lastKey();
		return testConsumer.get(key);
	}
	
	public double getCheapestOfferProducer() {
		String key = testProducer.firstKey();
		System.out.println(key);
		return testProducer.get(key);
	}
}
