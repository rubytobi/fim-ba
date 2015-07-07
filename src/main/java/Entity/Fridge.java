package Entity;

import java.util.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonView;
import Packet.ChangeRequest;
import Packet.DeviceLoadprofile;
import Util.DateTime;
import Util.DeviceStatus;
import start.Application;
import start.Device;
import start.View;

public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	public double[][] scheduleMinutes = new double[2][60];

	// Zeitpunkt, ab dem scheduleMinutes gilt
	@JsonView(View.Summary.class)
	@JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss")
	public GregorianCalendar timeFixed;
	// Fahrpläne und Lastprofile, die schon ausgehandelt sind und fest stehen
	public Hashtable<String, double[]> schedulesFixed = new Hashtable<String, double[]>();
	public Hashtable<String, double[]> loadprofilesFixed = new Hashtable<String, double[]>();

	// currTemp: Temperatur, bei der der nächste neue Fahrplan beginnen soll
	double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// currCooling: Gibt an, ob der nächste Fahrplan mit Kühlen beginnen soll
	boolean currCooling;
	// Wie viel Grad pro Minute erwärmt bzw. kühlt der Kühlschrank?
	double fallCooling, riseWarming;
	// Verbrauch zum Kühlen pro Minute in Wh
	double consCooling;
	@JsonView(View.Summary.class)
	private DeviceStatus status;
	@JsonView(View.Summary.class)
	private UUID uuid;
	@JsonView(View.Summary.class)
	private UUID consumerUUID;
	private boolean send;

	private Fridge() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}

	public Fridge(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling,
			double riseWarming, double consCooling, double currTemp) {
		this();
		// Prüfe Angaben auf Korrektheit
		boolean correct = maxTemp1 <= maxTemp2;
		correct = correct && minTemp1 >= minTemp2;
		correct = correct && maxTemp1 > minTemp1;
		correct = correct && fallCooling < 0;
		correct = correct && riseWarming > 0;
		correct = correct && consCooling > 0;
		if (!correct) {
			// TODO throw new IllegalDeviceCreation
		}
		this.maxTemp1 = maxTemp1;
		this.minTemp1 = minTemp1;
		this.maxTemp2 = maxTemp2;
		this.minTemp2 = minTemp2;
		this.fallCooling = fallCooling;
		this.riseWarming = riseWarming;
		this.consCooling = consCooling;
		this.currTemp = currTemp;
		this.currCooling = false;
		
		sendLoadprofile();

		status = DeviceStatus.INITIALIZED;
	}

	public double getCurrTemp() {
		return currTemp;
	}

	public double getMaxTemp1() {
		return maxTemp1;
	}

	public double getMaxTemp2() {
		return maxTemp2;
	}

	public double getMinTemp1() {
		return minTemp1;
	}

	public double getMinTemp2() {
		return minTemp2;
	}

	public double getFallCooling() {
		return fallCooling;
	}

	public double getRiseWarming() {
		return riseWarming;
	}

	public double getConsCooling() {
		return consCooling;
	}

	/*
	 * Berechnet ein neues Lastprofil im Minutentakt für die volle Stunde, in
	 * welcher timeFixed liegt und mit Temperatur currTempt zum Zeitpunkt
	 * timeFixed
	 */
	private void chargeNewScheduleMinutes() {
		System.out.println("chargeScheduleMinutes, Start: " + DateTime.ToString(timeFixed));
		// Erstelle Array mit geplantem Verbrauch (0) und geplanter Temperatur
		// (1) pro Minute
		scheduleMinutes = new double[2][15 * numSlots];

		/*
		 * Starte in der Minute nach timeFixed mit Kühlen, wenn die aktuelle
		 * Temperatur über der maximalen Temperatur liegt
		 */
		boolean cooling = currTemp >= maxTemp1;

		int startMinute = timeFixed.get(Calendar.MINUTE);

		// Setze alle Werte der Stunde vor timeFixed = 0
		for (int i = 0; i < startMinute; i++) {
			scheduleMinutes[0][0] = 0.0;
			scheduleMinutes[1][0] = 0;
		}

		/*
		 * Setze Temperatur zum Zeitpunkt timeFixed = currTemp, wenn currCooling
		 * gesetzt ist, ist der Verbrauch = consCooling, ansonsten = 0.0
		 */
		scheduleMinutes[1][startMinute] = currTemp;
		if (currCooling) {
			scheduleMinutes[0][startMinute] = 0.0;
		} else {
			scheduleMinutes[0][startMinute] = consCooling;
		}

		/*
		 * Berechne alle weiteren Verbrauchs- und Temperatur- werte ab dem
		 * Zeitpuntk timeFixed + 1 Minute
		 */
		for (int i = startMinute + 1; i < numSlots * 15; i++) {
			if (cooling) {
				scheduleMinutes[0][i] = consCooling;
				scheduleMinutes[1][i] = fallCooling + scheduleMinutes[1][i - 1];
				if (scheduleMinutes[1][i] <= minTemp1) {
					cooling = false;
				}
			} else {
				scheduleMinutes[0][i] = 0.0;
				scheduleMinutes[1][i] = riseWarming + scheduleMinutes[1][i - 1];
				if (scheduleMinutes[1][i] >= maxTemp1) {
					cooling = true;
				}
			}
		}
		/*
		 * Prüfe, ob zu Beginn des nächsten Plans gekühlt werden soll und mit
		 * welcher Temperatur dann gestartet wird
		 */
		if (cooling) {
			currCooling = true;
			currTemp = scheduleMinutes[1][numSlots * 15 - 1] + fallCooling;
		} else {
			currCooling = false;
			currTemp = scheduleMinutes[1][numSlots * 15 - 1] + riseWarming;
		}
	}

	// Berechnet viertelstündlich die aufsummierten Verbrauchswerte für den
	// übergebenen Minutenplan
	public double[] createValuesLoadprofile(double[] schedule) {
		System.out.println("createValuesLoadprofile");

		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int n = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + schedule[i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[n] = summeMin;
				System.out.println("Slot " + n + " : " + valuesLoadprofile[n]);
				summeHour = summeHour + valuesLoadprofile[n];
				n++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	public DeviceStatus getStatus() {
		return status;
	}

	/*
	 * Erzeugt einen neuen Fahrplan und das zugehörige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
	 */
	public void sendLoadprofile() {
		double[] valuesLoadprofile;

		System.out.println("###sendLoadprofile");

		/*
		 * Prüfe, ob dateFixed gesetzt, wenn nicht setze neu und erstelle
		 * initiales Lastprofil
		 */
		if (timeFixed == null) {
			System.out.println("timeFixed: null");
			/*
			 * Wenn noch nicht gesetzt, erstelle initialen Fahrplan für bis zur
			 * nächsten Stunde
			 */
			timeFixed = new GregorianCalendar();
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);

			chargeNewScheduleMinutes();
			valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);

			timeFixed.set(Calendar.MINUTE, 0);

			saveSchedule(scheduleMinutes, timeFixed, false);
			loadprofilesFixed.put(DateTime.ToString(timeFixed), valuesLoadprofile);
		}
		// Zähle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		chargeNewScheduleMinutes();
		valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);
		// TODO Schicke values + Startzeit (timeFixed) an Consumer
	}

	public GregorianCalendar generateStartLoadprofile(int hour, int date, int month, int year) {
		GregorianCalendar start = new GregorianCalendar();
		start.set(Calendar.HOUR_OF_DAY, hour);
		start.set(Calendar.DATE, date);
		start.set(Calendar.MONTH, month);
		start.set(Calendar.YEAR, year);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MILLISECOND, 0);
		return start;
	}

	/*
	 * Generiert einen neuen Fahrplan bei Temperaturabweichungen
	 * 
	 */
	public double[][] generateNewSchedule(GregorianCalendar aenderung, double newTemperature) {
		int change = 0;
		int minuteChange = aenderung.get(Calendar.MINUTE);
		double[][] newSchedule = new double[2][15 * numSlots];

		System.out.println("generateNewSchedule, Aenderung: " + DateTime.ToString(aenderung) + " newTemperature: "
				+ newTemperature + " Minute Änderung: " + minuteChange);
		System.out.println("timeFixed: " + DateTime.ToString(timeFixed));

		GregorianCalendar start = generateStartLoadprofile(aenderung.get(Calendar.HOUR_OF_DAY),
				aenderung.get(Calendar.DATE), aenderung.get(Calendar.MONTH), aenderung.get(Calendar.YEAR));

		/*
		 * Wenn start timeFixed entspricht, ergibt sich die Änderung für
		 * scheduleMinutes
		 */
		System.out.println(DateTime.ToString(start).equals(DateTime.ToString(timeFixed)));
		if (DateTime.ToString(start).equals(DateTime.ToString(timeFixed))) {
			for (int i = 0; i < 15 * numSlots; i++) {
				double cons = scheduleMinutes[0][i];
				double temp = scheduleMinutes[1][i];
				newSchedule[0][i] = cons;
				newSchedule[1][i] = temp;
				System.out.println(
						"Minute " + i + " Verbrauch: " + newSchedule[0][i] + " Temperatur: " + newSchedule[1][i]);
			}
		}
		/*
		 * Ansonsten muss der entsprechende Fahrplan aus schedulesFixed geändert
		 * werden
		 */
		else {
			for (int i = 0; i < 15 * numSlots; i++) {
				if (schedulesFixed.get(DateTime.ToString(start)) == null) {
					newSchedule[0][i] = 0.0;
					newSchedule[1][i] = 0;
				} else {
					newSchedule[0][i] = schedulesFixed.get(DateTime.ToString(start))[0];
					newSchedule[1][i] = schedulesFixed.get(DateTime.ToString(start))[1];
				}
				start.add(Calendar.MINUTE, 1);
				System.out.println(
						"Minute " + i + " Verbrauch: " + newSchedule[0][i] + " Temperatur: " + newSchedule[1][i]);
			}
			start.add(Calendar.HOUR_OF_DAY, -1);
		}

		// Prüf mit testSchedule, in welcher Minute der Wert zu hoch/ zu niedrig
		// ist
		change = testSchedule(newSchedule, newTemperature, minuteChange);
		boolean tooWarm = (change > 0);
		change = Math.abs(change);

		// Passe den Fahrplan so lange an, bis testOldSchedule die volle Anzahl
		// der
		// Minuten zurückgibt
		while (change != 15 * numSlots) {
			// Wenn Änderung notwendig ist, passe Plan an
			if (tooWarm) {
				/*
				 * Kuehle in Minuten mit zu hoher Temperatur
				 */
				newSchedule[0][change] = consCooling;
				newSchedule[1][change] = newSchedule[1][change] + fallCooling;
				newTemperature = newSchedule[1][change];

				minuteChange = change;
			}
			/*
			 * Hoere in Minuten mit zu niedriger Temperatur auf zu Kuehlen
			 */
			else {
				newSchedule[0][change] = 0.0;
				newSchedule[1][change] = newSchedule[1][change] + riseWarming;
				newTemperature = newSchedule[1][change];

				minuteChange = change;
			}
			change = testSchedule(newSchedule, newTemperature, minuteChange);
			tooWarm = (change > 0);
			change = Math.abs(change);
		}
		return newSchedule;
	}

	public int testSchedule(double[][] schedule, double newTemperature, int minuteChange) {
		schedule[1][minuteChange] = newTemperature;
		System.out.println("testSchedule: " + minuteChange);

		System.out.println("Neu Minute " + minuteChange + " : Verbrauch: " + schedule[0][minuteChange] + " Temperatur: "
				+ schedule[1][minuteChange]);

		for (int i = minuteChange + 1; i < 15 * numSlots; i++) {
			if (schedule[0][i] > 0) {
				schedule[1][i] = schedule[1][i - 1] + fallCooling;
			} else {
				schedule[1][i] = schedule[1][i - 1] + riseWarming;
			}
			System.out
					.println("Neu Minute " + i + " : Verbrauch: " + schedule[0][i] + " Temepratur: " + schedule[1][i]);
			if (schedule[1][i] > maxTemp2) {
				System.out.println("Änderung notwendig in Minute " + i);
				// Wenn Änderung notwendig ist, wird die Minute, in welcher
				// Temperatur zu hoch zurückgegeben
				return i;
			} else if (schedule[1][i] < minTemp2) {
				System.out.println("Änderung notwendig in Minute " + (-i));
				// Wenn Änderung notwendig ist, wird die -Minute, in welcher
				// Temperatur zu niedrig zurückgegeben
				return -i;
			}

		}
		// Wenn keine Änderung notwendig ist, wird volle Anzahl an Minuten
		// zurückgegeben
		return 15 * numSlots;
	}

	public void sendDeltaLoadprofile(GregorianCalendar aenderung, double newTemperature) {
		System.out.println("sendDelatLoadprofile aufgerufen. ");
		GregorianCalendar startLoadprofile = generateStartLoadprofile(aenderung.get(Calendar.HOUR_OF_DAY),
				aenderung.get(Calendar.DATE), aenderung.get(Calendar.MONTH), aenderung.get(Calendar.YEAR));
		aenderung.set(Calendar.SECOND, 0);
		aenderung.set(Calendar.MILLISECOND, 0);
		boolean change;
		boolean nextCooling = false;
		double nextTemp = newTemperature;
		
		// +3 wegen +1: nach timeFixed und +2 wegen Zeitzone
		GregorianCalendar compare = generateStartLoadprofile(timeFixed.get(Calendar.HOUR_OF_DAY) + 3,
				timeFixed.get(Calendar.DATE), timeFixed.get(Calendar.MONTH), timeFixed.get(Calendar.YEAR));
		
		System.out.println("Hour Time Fixed: " +timeFixed.get(Calendar.HOUR_OF_DAY));
		System.out.println("startLoadprofile: " +DateTime.ToString(startLoadprofile));
		System.out.println("compare: " +DateTime.ToString(compare));

		/*
		 * Berechne ab Zeitpunkt der Abweichung einschließlich des aktuellen
		 * Plans scheduleMinutes neue Fahrpläne, neue Lastprofile und
		 * Deltalastprofile
		 */
		while (!DateTime.ToString(startLoadprofile).equals(DateTime.ToString(compare))) {
			// Berechne neuen Fahrplan
			double[][] newSchedule = generateNewSchedule(aenderung, nextTemp);
			saveSchedule(newSchedule, startLoadprofile, true);

			// Setze nextTemp und nextCooling für nächsten Fahrplan
			nextCooling = newSchedule[1][15 * numSlots - 1] >= maxTemp1;
			if (nextCooling) {
				nextTemp = scheduleMinutes[1][numSlots * 15 - 1] + fallCooling;
			} else {
				nextTemp = scheduleMinutes[1][numSlots * 15 - 1] + riseWarming;
			}

			double[] newValues = createValuesLoadprofile(newSchedule[0]);
			aenderung.set(Calendar.MINUTE, 0);
			double[] oldValues;
			System.out.println("timeFixed: " + DateTime.ToString(timeFixed));
			if (DateTime.ToString(startLoadprofile).equals(DateTime.ToString(timeFixed))) {
				System.out.println("=");
				oldValues = createValuesLoadprofile(scheduleMinutes[0]);
			} else {
				oldValues = loadprofilesFixed.get(DateTime.ToString(startLoadprofile));
			}
			System.out.println("Start: " + DateTime.ToString(aenderung));
			System.out.println("Loadprofiles Fixed: " + loadprofilesFixed.toString());
			double[] deltaValues = new double[4];
			change = false;
			for (int i = 0; i < 4; i++) {
				deltaValues[i] = newValues[i] - oldValues[i];
				System.out.println("Delta Values: " + i + " " + deltaValues[i]);
				if (deltaValues[i] != 0) {
					change = true;
				}
			}
			if (change) {
				System.out.println("Versende deltaLastprofil und speichere Neues Lastprofil");
				// TODO Versende deltaValues als Delta-Lastprofil an den
				// Consumer

				// Löschen des alten Lastprofils des Zeitraums und Abspeichern
				// des neuen Lastprofils
				loadprofilesFixed.remove(DateTime.ToString(aenderung));
				loadprofilesFixed.put(DateTime.ToString(aenderung), newValues);
			}
			startLoadprofile.add(Calendar.HOUR_OF_DAY, 1);
			aenderung.add(Calendar.HOUR_OF_DAY, 1);
		}
	}

	public void saveSchedule(double[][] schedule, GregorianCalendar start, boolean delete) {
		int size = 15 * numSlots;

		System.out.println("####################################Schedule gespeichert ab: " + DateTime.ToString(start));

		for (int i = 0; i < size; i++) {
			if (delete) {
				schedulesFixed.remove(DateTime.ToString(start));
			}
			double[] values = { schedule[0][i], schedule[1][i] };
			schedulesFixed.put(DateTime.ToString(start), values);
			start.add(Calendar.MINUTE, 1);
		}
		start.add(Calendar.HOUR_OF_DAY, -1);
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public double[] chargeValuesLoadprofile(double[] toBeReduced) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub
	}

	@Override
	public void ping() {
		GregorianCalendar currentTime = new GregorianCalendar();
		currentTime.set(Calendar.SECOND, 0);
		currentTime.set(Calendar.MILLISECOND, 0);

		double tempPlanned, tempScaled;
		tempPlanned = schedulesFixed.get(DateTime.ToString(currentTime))[1];
		// TODO Temperatur messen und =tempScaled setzen
		tempScaled = 5.5;
		System.out.println("ping: @" + uuid + " " + DateTime.timestamp() + " Temperatur geplant: "
				+ schedulesFixed.get(DateTime.ToString(currentTime))[1] + " Temperatur gemessen: " + tempScaled);

		if (tempPlanned != tempScaled) {
			System.out.println("Rufe sendDeltaLoadprofile auf:");
			System.out.println("Time Fixed: " + DateTime.ToString(timeFixed));
			sendDeltaLoadprofile(currentTime, tempScaled);
		}
	}

	private static void mapToString(Hashtable<String, double[]> map) {
		Set<String> set = map.keySet();

		for (String s : set) {
			System.out.println("##" + s + " " + Arrays.toString(map.get(s)));
		}
	}

	public void setConsumer(UUID uuid) {
		consumerUUID = uuid;
	}

	@Override
	public void changeLoadprofile(ChangeRequest cr) {
		// TODO Auto-generated method stub
	}

	private void sendInitialLoadprofile() {
		RestTemplate rest = new RestTemplate();
		DeviceLoadprofile lp = new DeviceLoadprofile(null, createValuesLoadprofile(scheduleMinutes[0]));
		HttpEntity<DeviceLoadprofile> entity = new HttpEntity<DeviceLoadprofile>(lp, Application.getRestHeader());
		System.out.println(entity.toString());
		rest.exchange("http://localhost:8080/consumers/" + consumerUUID + "/offers", HttpMethod.POST, entity,
				String.class);
	}

}