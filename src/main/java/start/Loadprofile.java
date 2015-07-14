package start;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import Util.DateTime;

public class Loadprofile {
	// 4 Werte für 15 Minuten Slots
	private double[] values;

	// Zeitpunkt, ab wann das Lastprofil gelten soll
	private GregorianCalendar date;

	// Preis
	@JsonProperty("minPrice")
	private double minPrice;

	@JsonProperty("isDelta")
	private boolean isDelta;

	public Loadprofile() {
		minPrice = 0.0;
		isDelta = false;
	}

	// Erstellt ausübergebenem Array neues Lastprofil
	public Loadprofile(double[] values, GregorianCalendar date) {
		this();

		// Prüfe, dass der Consumer 4 15-Minuten-Slots will und dass das Profil
		// zur vollen Stunde startet
		if (values.length != 4 || date.get(Calendar.MINUTE) != 0) {
			throw new IllegalArgumentException();
		}

		this.values = values;
		this.date = date;
	}

	public Loadprofile(double[] values, GregorianCalendar date, double minPrice) {
		this(values, date);

		this.minPrice = minPrice;
	}

	public Loadprofile(double[] values, GregorianCalendar date, double minPrice, boolean isDelta) {
		this(values, date, minPrice);

		this.isDelta = isDelta;
	}

	// Erstellt aus beiden Lastprofilen Aggregiertes Lastprofil
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

	public double getMinPrice() {
		return minPrice;
	}

	public double[] getValues() {
		return values;
	}

	public GregorianCalendar getDate() {
		return date;
	}

	public void setMinPrice(double newPrice) {
		minPrice = newPrice;
	}

	public String toString() {
		return "{\"values\":" + Arrays.toString(values) + ",\"date\":\"" + DateTime.ToString(date) + "\",\"minPrice\":"
				+ minPrice + ",\"isDelta\":" + isDelta + "}";
	}

	// Berechnet die Abweichung des Lastprofils von seinem Mittelwert
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

	// Berechnet die Abweichung von einem anderen Lastprofil
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
}
