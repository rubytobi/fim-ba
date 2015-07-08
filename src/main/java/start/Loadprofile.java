package start;

import java.util.*;

import Entity.Consumer;
import Util.DateTime;

public class Loadprofile {
	// 4 Werte für 15 Minuten Slots
	private double[] values;

	// Zeitpunkt, ab wann das Lastprofil gelten soll
	private GregorianCalendar date;

	// Preis
	private double price;

	public Loadprofile() {
		price = 0.0;
	}

	// Erstellt ausübergebenem Array neues Lastprofil
	public Loadprofile(double[] values, GregorianCalendar date) {
		// Prüfe, dass der Consumer 4 15-Minuten-Slots will und dass das Profil
		// zur vollen Stunde startet
		if (values.length != 4 || date.get(Calendar.MINUTE) != 0) {
			return;
		}

		this.values = values;
		this.date = date;
	}

	public Loadprofile(double[] values, GregorianCalendar date, double price) {
		this(values, date);

		this.price = price;
	}

	// Erstellt aus beiden Lastprofilen Aggregiertes Lastprofil
	public Loadprofile(Loadprofile lp1, Loadprofile lp2) {
		if (lp1.getDate() != lp2.getDate()) {
			return;
		}
		date = lp1.getDate();

		for (int i = 0; i < 4; i++) {
			values[i] = lp1.getValues()[i] + lp2.getValues()[i];
		}
	}

	public double[] getValues() {
		return values;
	}

	public GregorianCalendar getDate() {
		return date;
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
}
