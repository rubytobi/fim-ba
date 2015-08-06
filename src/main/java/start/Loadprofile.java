package start;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import Entity.Offer;
import Packet.ChangeRequestSchedule;
import Util.DateTime;

/**
 * Klasse fuer Lastprofile
 *
 */
public class Loadprofile {
	private UUID uuid;

	// Verbrauch pro 15 Minuten
	private double[] values;

	// Zeitpunkt, ab wann das Lastprofil gelten soll
	private GregorianCalendar date;

	// Preis, den der Consumer mindestens einnehmen bzw. maximal zahlen will
	@JsonProperty("minPrice")
	private double minPrice;

	// Gibt, an, ob das Lastprofil ein Delta-Lastprofil ist
	@JsonProperty("isDelta")
	private boolean isDelta;

	// Setzt minPrice auf 0
	private Loadprofile() {
		minPrice = 0.0;
		isDelta = false;
		uuid = UUID.randomUUID();
	}

	/**
	 * Erstellt neues Lastprofil aus uebergebenen Werten
	 * 
	 * @param values
	 *            Werte fuer das neue Lastprofil
	 * @param date
	 *            Startzeitpunkt des neuen Lastprofils
	 * @param minPrice
	 *            Preis pro kWh, den der Consumer für dieses Lastprofil
	 *            mindestens einnehmen bzw. maximal zahlen
	 */
	public Loadprofile(double[] values, GregorianCalendar date, double minPrice) {
		this();

		this.values = values;
		this.date = date;
		this.minPrice = minPrice;
	}

	/**
	 * Erstellt neues Lastprofil aus uebergebenen Werten
	 * 
	 * @param values
	 *            Werte fuer das neue Lastprofil
	 * @param date
	 *            Startzeitpunkt des neuen Lastprofils
	 * @param minPrice
	 *            Preis pro kWh, den der Consumer für dieses Lastprofil
	 *            mindestens einnehmen bzw. maximal zahlen
	 * @param isDelta
	 *            Gibt an, ob das Lastprofil ein Deltalastprofil ist
	 */
	public Loadprofile(double[] values, GregorianCalendar date, double minPrice, boolean isDelta) {
		this(values, date, minPrice);

		this.isDelta = isDelta;
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

		// TODO: annahme: nehme stets den billigeren preis
		this.minPrice = Math.min(lp1.getMinPrice(), lp2.getMinPrice());
	}

	/**
	 * Erzeugt ein Lastprofil mit einer fixen UUID
	 * 
	 * @param uuid
	 * @param changesLoadprofile
	 * @param startLoadprofile
	 * @param d
	 */
	public Loadprofile(UUID uuid, double[] changesLoadprofile, GregorianCalendar startLoadprofile, double d) {
		this(changesLoadprofile, startLoadprofile, d);
		this.uuid = uuid;
	}

	public Loadprofile(ChangeRequestSchedule cr) {
		this(cr.getChangesLoadprofile(), cr.getStartLoadprofile(), 0.0);
		this.uuid = cr.getUUID();
	}

	/**
	 * Liefert den Preis des Lastprofils
	 * 
	 * @return Preis pro kWh, den der Consumer fuer dieses Lastprofil mindestens
	 *         einnehmen bzw. maximal zahlen will
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
	public void setMinPrice(double newPrice) {
		minPrice = newPrice;
	}

	/**
	 * Liefert die wichtigsten Informationen des Lastprofils als String
	 * 
	 * @return String, der die aktuellen Werte, das Startdatum und den Preis des
	 *         Lastprofils enthaelt
	 */
	public String toString() {
		return "Loadprofile [values=" + Arrays.toString(values) + ",date=" + DateTime.ToString(date) + ",minPrice="
				+ minPrice + ",isDelta=" + isDelta + "]";
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
	 * Berechnet die Abweichung des Lastprofils von einem anderen Lastprofil
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
		return isDelta;
	}

	public UUID getUUID() {
		return uuid;
	}

	public Offer toOffer(UUID author) {
		return new Offer(author, this);
	}
}
