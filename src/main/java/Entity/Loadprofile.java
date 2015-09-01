package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import Packet.ChangeRequestSchedule;
import Util.DateTime;

/**
 * Klasse fuer Lastprofile
 *
 */
public class Loadprofile {
	public enum Type {
		UNDEFINED, INITIAL, DELTA, CHANGE_REQUEST, MIXED
	};

	private UUID uuid;

	// Verbrauch pro 15 Minuten
	private double[] values;

	// Zeitpunkt, ab wann das Lastprofil gelten soll
	private GregorianCalendar date;

	// Preis, den das Device für das Lastprofil vorschlägt
	@JsonProperty("priceSugg")
	private double priceSugg;

	// Maximaler Preis, den das Device für dieses Lastprofil zulässt
	@JsonProperty("maxPrice")
	private double maxPrice;

	// Minimaler Preis, den das Device für dieses Lastprofil zulässt
	@JsonProperty("minPrice")
	private double minPrice;

	// Gibt, an, ob das Lastprofil Preise festsetzt
	private Type type;

	private double priceEex = 20;

	private Loadprofile() {
		type = Type.UNDEFINED;
		uuid = UUID.randomUUID();
	}

	/**
	 * Erstellt neues Lastprofil aus uebergebenen Werten mit Minimum und Maximum
	 * 
	 * @param values
	 *            Werte fuer das neue Lastprofil
	 * @param date
	 *            Startzeitpunkt des neuen Lastprofils
	 * @param priceSugg
	 *            Preis pro kWh, den das Device für dieses Lastprofil vorschlägt
	 * @param minPrice
	 *            Minimaler Preis pro kWh, den das Device zulässt
	 * @param maxPrice
	 *            Maximaler Preis pro kWh, den das Device zulässt
	 * @param isDelta
	 *            Gibt an, ob das Lastprofil ein Deltalastprofil ist
	 */
	public Loadprofile(double[] values, GregorianCalendar date, double priceSugg, double minPrice, double maxPrice,
			Type isDelta) {
		this();

		this.values = values;
		this.date = date;
		this.type = isDelta;

		if (minPrice <= priceSugg && priceSugg <= maxPrice) {
			this.priceSugg = priceSugg;
			this.maxPrice = maxPrice;
			this.minPrice = minPrice;
		}
	}

	public Type getType() {
		return type;
	}

	/**
	 * Erstellt ein Deltalastprofil aus den übergebenen Werten. Die Preisgrenzen
	 * werden beim Deltalastprofil auf unendlich gesetzt und der Preisvorschlag
	 * auf den aktuellen EEX-Preis.
	 * 
	 * @param values
	 *            Werte des Lastprofils
	 * @param date
	 *            Startzeit des Lastprofils
	 * @param state
	 *            Lastprofilestatus
	 */
	public Loadprofile(double[] values, GregorianCalendar date, Type state) {
		this();

		this.values = values;
		this.date = date;
		this.type = state;
		this.minPrice = Double.NEGATIVE_INFINITY;
		this.maxPrice = Double.POSITIVE_INFINITY;
		this.priceSugg = priceEex;

	}

	/**
	 * Erstellt aggregiertes Lastprofil aus uebergebenen Lastprofilen
	 * 
	 * @param lp1
	 *            Erstes Lastprofil fuer Aggregierung
	 * @param lp2
	 *            Zweites Lastprofil fuer Aggregierung
	 */
	public Loadprofile(Loadprofile lp1, Loadprofile lp2) {
		this();

		if (!DateTime.ToString(lp1.getDate()).equals(DateTime.ToString(lp2.getDate()))) {
			throw new IllegalArgumentException();
		}

		if (lp1.getValues() == null || lp2.getValues() == null) {
			throw new IllegalArgumentException();
		}

		date = lp1.getDate();
		values = new double[4];

		for (int i = 0; i < 4; i++) {
			values[i] = lp1.getValues()[i] + lp2.getValues()[i];
		}

		this.maxPrice = Math.min(lp1.getMaxPrice(), lp2.getMaxPrice());
		this.minPrice = Math.max(lp1.getMinPrice(), lp2.getMinPrice());

		this.priceSugg = (lp1.getPriceSugg() * lp1.getLoad() + lp2.getPriceSugg() * lp2.getLoad())
				/ (lp1.getLoad() + lp2.getLoad());

		type = Type.MIXED;
	}

	public Loadprofile(ChangeRequestSchedule cr) {
		this(cr.getChangesLoadprofile(), cr.getStartLoadprofile(), Type.MIXED);
		this.uuid = cr.getUUID();
	}

	/**
	 * Liefert den vorgeschlagenen Preis des Lastprofils
	 * 
	 * @return Preis pro kWh, den das Device fuer dieses Lastprofil vorschlägt
	 */
	public double getPriceSugg() {
		return priceSugg;
	}

	/**
	 * Liefert den maximalen Preis des Lastprofils
	 * 
	 * @return Maximaler Preis pro kWh, den das Device für dieses Lastprofil
	 *         maximal akzeptieren will
	 */
	public double getMaxPrice() {
		return maxPrice;
	}

	/**
	 * Liefert den minimalen Preis des Lastprofils
	 * 
	 * @return Minimaler Preis pro kWh, den das Device für dieses Lastprofil
	 *         minimal akzeptieren will
	 */
	public double getMinPrice() {
		return minPrice;
	}

	/**
	 * Liefert die Werte des Lastprofils
	 * 
	 * @return Array mit Werten des Lastprofils
	 */
	public double[] getValues() {
		return values;
	}

	@JsonIgnore
	public double getLoad() {
		double load = 0;

		for (double d : values) {
			load += d;
		}

		return load;
	}

	/**
	 * Liefert den Startzeitpunkt des Lastprofils
	 * 
	 * @return Startzeitpunkt des Lastprofils als GregorianCalendar
	 */
	public GregorianCalendar getDate() {
		return date;
	}

	/**
	 * Setzt den Preis des Lastprofils
	 * 
	 * @param newPrice
	 *            Preis pro kWh, den der COnsumer fuer dieses Lastprofil
	 *            mindestens einnehmen bzw. maximal zahlen will
	 */
	public void setPriceSugg(double newPrice) {
		priceSugg = newPrice;
	}

	/**
	 * Liefert die wichtigsten Informationen des Lastprofils als String
	 * 
	 * @return String, der die aktuellen Werte, das Startdatum und den Preis des
	 *         Lastprofils enthaelt
	 */
	public String toString() {
		return "Loadprofile [values=" + Arrays.toString(values) + ",date=" + DateTime.ToString(date) + ",priceSugg="
				+ priceSugg + ",minPrice=" + minPrice + ",maxPrice=" + maxPrice + ",isDelta=" + type + "]";
	}

	/**
	 * Berechnet die Abweichung des Lastprofils von seinem Mittelwert
	 * 
	 * @return Die Summe aller Abweichungen vom Mittelwert
	 */
	public double chargeDeviationAverage() {
		double average;
		double deviationAverage = 0;

		average = (values[0] + values[1] + values[2] + values[3]) / 4;
		deviationAverage = deviationAverage + Math.abs((values[0] - average));
		deviationAverage = deviationAverage + Math.abs((values[1] - average));
		deviationAverage = deviationAverage + Math.abs((values[2] - average));
		deviationAverage = deviationAverage + Math.abs((values[3] - average));

		return deviationAverage;
	}

	/**
	 * Berechnet die absolute Abweichung des Lastprofils von einem anderen
	 * Lastprofil
	 * 
	 * @param otherProfile
	 *            Anderes Lastprofil, von welchem die Abweichung berechnet
	 *            werden soll
	 * @return Die Summe aller Abweichungen vom Mittelwert
	 */
	public double chargeDeviationOtherProfile(Loadprofile otherProfile) {
		double deviationOtherProfile = 0;

		deviationOtherProfile = deviationOtherProfile + Math.abs((values[0] - otherProfile.getValues()[0]));
		deviationOtherProfile = deviationOtherProfile + Math.abs((values[1] - otherProfile.getValues()[1]));
		deviationOtherProfile = deviationOtherProfile + Math.abs((values[2] - otherProfile.getValues()[2]));
		deviationOtherProfile = deviationOtherProfile + Math.abs((values[3] - otherProfile.getValues()[3]));

		return deviationOtherProfile;
	}

	@JsonIgnore
	public boolean isDelta() {
		return type.equals(Type.DELTA);
	}

	public UUID getUUID() {
		return uuid;
	}

	public Offer toOffer(UUID author) {
		return new Offer(author, this);
	}
}
