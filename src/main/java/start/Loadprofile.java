package start;

import java.util.*;

import Entity.Consumer;
import Packet.DeviceLoadprofile;
import Util.DateTime;

public class Loadprofile {
	// 4 Werte für 15 Minuten Slots
	double value1, value2, value3, value4;

	// Zeitpunkt, ab wann das Lastprofil gelten soll
	Date date;

	// Erstellt ausübergebenem Array neues Lastprofil
	protected Loadprofile(double[] values, Consumer consumer, Date date) {
		// Prüfe, dass der Consumer 4 15-Minuten-Slots will und dass das Profil
		// zur vollen Stunde startet
		if (consumer.getNumSlots() != 4 || values.length != 4 || date.getMinutes() != 0) {
			return;
		}

		value1 = values[0];
		value2 = values[1];
		value3 = values[2];
		value4 = values[3];
		this.date = date;
	}

	// Erstellt aus beiden Lastprofilen Aggregiertes Lastprofil
	public Loadprofile(Loadprofile loadprofile1, Loadprofile loadprofile2) {
		if (loadprofile1.getDate() != loadprofile2.getDate()) {
			return;
		}
		date = loadprofile1.getDate();

		value1 = loadprofile1.getValue1() + loadprofile2.getValue1();
		value2 = loadprofile1.getValue2() + loadprofile2.getValue2();
		value3 = loadprofile1.getValue3() + loadprofile2.getValue3();
		value4 = loadprofile1.getValue4() + loadprofile2.getValue4();
	}

	public Loadprofile(DeviceLoadprofile device) {
		value1 = device.getValues()[0];
		value2 = device.getValues()[1];
		value3 = device.getValues()[2];
		value4 = device.getValues()[3];
		date = device.getDate();
	}

	public double getValue1() {
		return value1;
	}

	public double getValue2() {
		return value2;
	}

	public double getValue3() {
		return value3;
	}

	public double getValue4() {
		return value4;
	}

	public Date getDate() {
		return date;
	}

	// Berechnet die Abweichung des Lastprofils von seinem Mittelwert
	public double chargeDeviationAverage() {
		double average;
		double deviationAverage = 0;

		average = (value1 + value2 + value3 + value4) / 4;
		deviationAverage = deviationAverage + Math.abs((value1 - average));
		deviationAverage = deviationAverage + Math.abs((value2 - average));
		deviationAverage = deviationAverage + Math.abs((value3 - average));
		deviationAverage = deviationAverage + Math.abs((value4 - average));

		return deviationAverage;
	}

	// Berechnet die Abweichung von einem anderen Lastprofil
	public double chargeDeviationOtherProfile(Loadprofile otherProfile) {
		double deviationOtherProfile = 0;

		deviationOtherProfile = deviationOtherProfile + Math.abs((value1 - otherProfile.getValue1()));
		deviationOtherProfile = deviationOtherProfile + Math.abs((value2 - otherProfile.getValue2()));
		deviationOtherProfile = deviationOtherProfile + Math.abs((value3 - otherProfile.getValue3()));
		deviationOtherProfile = deviationOtherProfile + Math.abs((value4 - otherProfile.getValue4()));

		return deviationOtherProfile;
	}
}
