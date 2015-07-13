package Entity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;
import Event.IllegalDeviceState;
import Packet.ChangeRequest;
import Util.DateTime;
import Util.DeviceStatus;
import Util.Log;
import Util.SimulationFridge;
import start.Application;
import start.Device;
import start.Loadprofile;
import start.View;

public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	private double[][] scheduleMinutes = new double[2][15 * numSlots];

	// Zeitpunkt, ab dem scheduleMinutes gilt
	@JsonView(View.Summary.class)
	private GregorianCalendar timeFixed;
	// Fahrpläne und Lastprofile, die schon ausgehandelt sind und fest stehen
	private TreeMap<String, double[]> schedulesFixed = new TreeMap<String, double[]>();
	private TreeMap<String, double[]> loadprofilesFixed = new TreeMap<String, double[]>();

	// currTemp: Temperatur, bei der der nächste neue Fahrplan beginnen soll
	private double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// currCooling: Gibt an, ob der nächste Fahrplan mit Kühlen beginnen soll
	private boolean currCooling;
	// Wie viel Grad pro Minute erwärmt bzw. kühlt der Kühlschrank?
	private double fallCooling, riseWarming;
	// Verbrauch zum Kühlen pro Minute in kWh
	private double consCooling;
	@JsonView(View.Summary.class)
	private DeviceStatus status;
	@JsonView(View.Summary.class)
	private UUID uuid;
	@JsonView(View.Summary.class)
	private UUID consumerUUID;
	private SimulationFridge simulationFridge;

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

		simulationFridge = new SimulationFridge();

		status = DeviceStatus.INITIALIZED;
	}

	public DeviceStatus getStatus() {
		return status;
	}

	public double[][] getScheduleMinutes() {
		return scheduleMinutes;
	}

	public TreeMap<String, double[]> getSchedulesFixed() {
		return schedulesFixed;
	}

	public TreeMap<String, double[]> getLoadprofilesFixed() {
		return loadprofilesFixed;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	/*
	 * Erzeugt einen neuen Fahrplan und das zugehörige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile() {
		double[] valuesLoadprofile;

		/*
		 * Prüfe, ob dateFixed gesetzt, wenn nicht setze neu und erstelle
		 * initiales Lastprofil
		 */
		if (timeFixed == null) {
			/*
			 * Wenn noch nicht gesetzt, erstelle initialen Fahrplan für bis zur
			 * nächsten Stunde
			 */
			timeFixed = DateTime.now();
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);

			chargeNewSchedule();
			valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);

			timeFixed.set(Calendar.MINUTE, 0);

			saveSchedule(scheduleMinutes, timeFixed);
			loadprofilesFixed.put(DateTime.ToString(timeFixed), valuesLoadprofile);
		}
		// Zähle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		chargeNewSchedule();
		valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);

		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, 0.0);
		sendLoadprofileToConsumer(loadprofile, false);
	}

	/*
	 * Berechnet einen neuen Fahrplan im Minutentakt für die volle Stunde, in
	 * welcher timeFixed liegt und mit Temperatur currTempt zum Zeitpunkt
	 * timeFixed
	 */
	private void chargeNewSchedule() {
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
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int n = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + schedule[i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[n] = summeMin;
				summeHour = summeHour + valuesLoadprofile[n];
				n++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	public void sendDeltaLoadprofile(GregorianCalendar aenderung, double newTemperature) {
		GregorianCalendar startLoadprofile = (GregorianCalendar) aenderung.clone();
		startLoadprofile.set(Calendar.MINUTE, 0);
		GregorianCalendar compare = (GregorianCalendar) timeFixed.clone();
		compare.add(Calendar.HOUR_OF_DAY, 1);
		boolean change;
		boolean firstSchedule = true;

		aenderung.set(Calendar.SECOND, 0);
		aenderung.set(Calendar.MILLISECOND, 0);

		/*
		 * Berechne ab Zeitpunkt der Abweichung einschließlich des aktuellen
		 * Plans scheduleMinutes neue Fahrpläne, neue Lastprofile und
		 * Deltalastprofile
		 */
		while (!DateTime.ToString(startLoadprofile).equals(DateTime.ToString(compare))) {
			// Berechne neuen Fahrplan
			double[][] deltaSchedule = chargeDeltaSchedule(aenderung, newTemperature, firstSchedule);

			saveSchedule(deltaSchedule, startLoadprofile);

			firstSchedule = false;
			newTemperature = deltaSchedule[1][15 * numSlots - 1];

			double[] newValues = createValuesLoadprofile(deltaSchedule[0]);
			aenderung.set(Calendar.MINUTE, 0);
			double[] oldValues;
			if (DateTime.ToString(startLoadprofile).equals(DateTime.ToString(timeFixed))) {
				oldValues = createValuesLoadprofile(scheduleMinutes[0]);
			} else {
				oldValues = loadprofilesFixed.get(DateTime.ToString(startLoadprofile));
			}

			double[] deltaValues = new double[4];

			if (oldValues == null) {
				Log.e(DateTime.ToString(startLoadprofile) + " - " + loadprofilesFixed.keySet());
			}

			change = false;
			for (int i = 0; i < 4; i++) {
				deltaValues[i] = newValues[i] - oldValues[i];
				if (deltaValues[i] != 0) {
					change = true;
				}
			}
			if (change) {
				// Versende deltaValues als Delta-Lastprofil an den Consumer
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, startLoadprofile, 0.0);
				sendLoadprofileToConsumer(deltaLoadprofile, true);

				// Abspeichern des neuen Lastprofils
				loadprofilesFixed.put(DateTime.ToString(startLoadprofile), newValues);
			} else {
				// keine änderung
			}
			startLoadprofile.add(Calendar.HOUR_OF_DAY, 1);
			aenderung.add(Calendar.HOUR_OF_DAY, 1);
		}
	}

	/*
	 * Generiert einen neuen Fahrplan bei Temperaturabweichungen
	 * 
	 */
	public double[][] chargeDeltaSchedule(GregorianCalendar aenderung, double newTemperature, boolean firstSchedule) {
		int change = 0;
		int minuteChange = aenderung.get(Calendar.MINUTE);
		double[][] deltaSchedule = new double[2][15 * numSlots];

		GregorianCalendar start = (GregorianCalendar) aenderung.clone();
		start.set(Calendar.MINUTE, 0);
		
		// Lade aktuellen Plan für die Stunde ab start
		deltaSchedule = getSchedule(start);		

		// Prüf mit testSchedule, in welcher Minute der Wert zu hoch/ zu niedrig
		// ist
		change = testDeltaSchedule(deltaSchedule, newTemperature, minuteChange, firstSchedule);
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
				deltaSchedule[0][change] = consCooling;
				deltaSchedule[1][change] = deltaSchedule[1][change] + fallCooling;
				newTemperature = deltaSchedule[1][change];

				minuteChange = change;
			}
			/*
			 * Hoere in Minuten mit zu niedriger Temperatur auf zu Kuehlen
			 */
			else {
				deltaSchedule[0][change] = 0.0;
				deltaSchedule[1][change] = deltaSchedule[1][change] + riseWarming;
				newTemperature = deltaSchedule[1][change];

				minuteChange = change;
			}
			change = testDeltaSchedule(deltaSchedule, newTemperature, minuteChange, false);
			tooWarm = (change > 0);
			change = Math.abs(change);
		}
		return deltaSchedule;
	}
	
	public double[][] getSchedule (GregorianCalendar start) {
		double[][] schedule = new double[2][15*numSlots];
		/*
		 * Wenn start timeFixed entspricht, ergibt sich die Änderung für
		 * scheduleMinutes
		 */
		if (DateTime.ToString(start).equals(DateTime.ToString(timeFixed))) {
			for (int i = 0; i < 15 * numSlots; i++) {
				double cons = scheduleMinutes[0][i];
				double temp = scheduleMinutes[1][i];
				schedule[0][i] = cons;
				schedule[1][i] = temp;
				// System.out.println("Minute " + i + " Verbrauch: " +
				// deltaSchedule[0][i] + " Temperatur: " + deltaSchedule[1][i]);
			}
		}
		/*
		 * Ansonsten muss der entsprechende Fahrplan aus schedulesFixed geändert
		 * werden
		 */
		else {
			for (int i = 0; i < 15 * numSlots; i++) {
				if (schedulesFixed.get(DateTime.ToString(start)) == null) {
					schedule[0][i] = 0.0;
					schedule[1][i] = 0;
				} else {
					schedule[0][i] = schedulesFixed.get(DateTime.ToString(start))[0];
					schedule[1][i] = schedulesFixed.get(DateTime.ToString(start))[1];
				}
				start.add(Calendar.MINUTE, 1);
				// System.out.println("Minute " + i + " Verbrauch: " +
				// deltaSchedule[0][i] + " Temperatur: " + deltaSchedule[1][i]);
			}
			start.add(Calendar.HOUR_OF_DAY, -1);
		}
		return schedule;
	}

	public int testDeltaSchedule(double[][] schedule, double newTemperature, int minuteChange, boolean firstSchedule) {
		if (!firstSchedule) {
			if (schedule[0][minuteChange] == consCooling) {
				schedule[1][minuteChange] = newTemperature + fallCooling;
			} else {
				schedule[1][minuteChange] = newTemperature + riseWarming;
			}
		} else {
			schedule[1][minuteChange] = newTemperature;
		}

		for (int i = minuteChange + 1; i <schedule[1].length; i++) {
			if (schedule[0][i] > 0) {
				schedule[1][i] = schedule[1][i - 1] + fallCooling;
			} else {
				schedule[1][i] = schedule[1][i - 1] + riseWarming;
			}
			if (schedule[1][i] > maxTemp2) {
				// Wenn Änderung notwendig ist, wird die Minute, in welcher
				// Temperatur zu hoch zurückgegeben
				return i;
			} else if (schedule[1][i] < minTemp2) {
				// Wenn Änderung notwendig ist, wird die -Minute, in welcher
				// Temperatur zu niedrig zurückgegeben
				return -i;
			}

		}
		// Wenn keine Änderung notwendig ist, wird volle Anzahl an Minuten
		// zurückgegeben
		return 15 * numSlots;
	}

	public void saveSchedule(double[][] schedule, GregorianCalendar start) {
		int size = 15 * numSlots;

		for (int i = 0; i < size; i++) {
			double[] values = { schedule[0][i], schedule[1][i] };
			schedulesFixed.put(DateTime.ToString(start), values);
			simulationFridge.addNewValues(DateTime.ToString(start), values);
			start.add(Calendar.MINUTE, 1);
		}
		start.add(Calendar.HOUR_OF_DAY, -1);
	}

	@Override
	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub
	}

	@Override
	public void ping() {
		GregorianCalendar currentTime = DateTime.now();
		currentTime.set(Calendar.SECOND, 0);
		currentTime.set(Calendar.MILLISECOND, 0);

		double tempPlanned, tempScaled;
		tempPlanned = schedulesFixed.get(DateTime.ToString(currentTime))[1];
		// tempScaled = simulationFridge.getTemperature(currentTime);
		tempScaled = 7.5;

		if (tempPlanned != tempScaled) {
			sendDeltaLoadprofile(currentTime, tempScaled);
		}
	}

	public void setConsumer(UUID uuid) {
		if (status == DeviceStatus.INITIALIZED) {
			status = DeviceStatus.READY;
			consumerUUID = uuid;
		} else {
			throw new IllegalDeviceState();
		}

		// sende an den eigenen consumer das lastprofil
		sendNewLoadprofile();
	}
	
	public void changeLoadprofile (ChangeRequest cr) {
		double[] changesKWH = cr.getChangesLoadprofile();
		int[] changesMinute = new int[numSlots];
		double[][] plannedSchedule = scheduleMinutes;
		// Minute Max(0), Temperatur Max(1)
		double[][] maxSlots = new double[2][numSlots]
				, minSlots = new double[2][numSlots];
		
		// Berechne, wie viele Minuten zusätzlich bzw. weniger
		// gekühlt werden muss
		for (int i=0; i<numSlots; i++) {
			changesMinute[i] = (int) Math.ceil(changesKWH[i]/consCooling);
		}
		
		
		// Berechne für jeden Slot das aktuelle Maximum und Minimum und in welcher Minute es eintritt
		for (int i=0; i<numSlots; i++) {
			maxSlots[1][i] = plannedSchedule[1][i*15];
			minSlots[1][i] = maxSlots[1][i];
			for (int j=i*15; j<(i+1)*15; j++) {
				if (plannedSchedule[1][j] > maxSlots[1][i]) {
					maxSlots[1][i] = plannedSchedule[1][j];
					maxSlots[0][i] = j;
				}
				if (plannedSchedule[1][j] < minSlots[1][i]) {
					minSlots[1][i] = plannedSchedule[1][j];
					minSlots[0][i] = j;
				}
			}
		}
		
		/*
		 * Prüfe, ob die Änderungen der vorhergehenden Slots ein über-/ unter-
		 * schreiten der maximalen/ minimalen Temperatur erzwingen.
		 * Passe das Minimum und das Maximum mit Beachtung der Änderungen
		 * in den vorhergehenden Slots an
		 */
		int changesBefore = changesMinute[0];

		for (int i=1; i<numSlots; i++) {
			if (changesBefore != 0) {
				if (changesBefore < 0) {
					minSlots[1][i] -= changesBefore*fallCooling;
					if (minSlots[1][i] < minTemp2) {
						declineChangedLoadprofile(cr);
						return;
					}
				}
				else {
					maxSlots[1][i] += changesBefore*riseWarming;
					if (maxSlots[1][i] > maxTemp2) {
						declineChangedLoadprofile(cr);
						return;
					}
				}
			}
			changesBefore += changesMinute[i];
		}
		
		// Wie viele Changes (1) darf ich erst ab Minute (0) machen?
		int[][] minutePossibleChange = new int[2][numSlots];
		int change;

		for (int i=0; i<numSlots; i++) {
			change = changesMinute[i];
			if (change != 0) {
				if (change > 0) {
					minSlots[1][i] -= change*fallCooling;
					if (minSlots[1][i] < minTemp2) {
						minutePossibleChange[0][i] = (int) minSlots[0][i]+1;
						
						// Berechne, wie viele Changes vor dem Eintritt des Minimums möglich sind
						double currentMin = minSlots[1][i];
						int amountChanges = 0;
						while (currentMin >= minTemp2 && change > 0) {
							currentMin -= fallCooling;
							change--;
							amountChanges++;
						}
						minutePossibleChange[1][i] = change-amountChanges;
						
						if (minutePossibleChange[1][i] + minutePossibleChange[0][i] > 59) {
							declineChangedLoadprofile(cr);
							return;
						}
					}
					
					else {
						minutePossibleChange[0][i] = 0;
						minutePossibleChange[1][i] = change;
					}
				}
				else {
					maxSlots[1][i] -= change*riseWarming;
					if (maxSlots[1][i] > maxTemp2) {
						minutePossibleChange[0][i] = (int) maxSlots[0][i]+1;
						
						// Berechne, wie viele Changes vor dem Eintritt des Minimums möglich sind
						double currentMax = minSlots[1][i];
						int amountChanges = 0;
						while (currentMax <= maxTemp2 && change < 0 ) {
							currentMax += riseWarming;
							change++;
							amountChanges++;
						}
						minutePossibleChange[1][i] = change-amountChanges;
						
						if (minutePossibleChange[1][i] + minutePossibleChange[0][i] > 59) {
							declineChangedLoadprofile(cr);
							return;
						}
					}
					else {
						minutePossibleChange[0][i] = 0;
						minutePossibleChange[1][i] = change;
					}
				}
			}
		}		
		double[][] newSchedule = chargeChangedSchedule(changesMinute, minutePossibleChange);
		
		if (newSchedule != null) {
			confirmChangedLoadprofile(cr);
		}
		else {
			declineChangedLoadprofile(cr);
		}
	}
	
	private double[][] chargeChangedSchedule (int[] changesLoadprofile, int[][] minutePossibleChanges) {
		double[][] newSchedule =scheduleMinutes;
		int currentChange;
		boolean changed = false;
		
		// Berechne Slotsweise neuen Plan
		for (int slot=0; slot<numSlots; slot++) {
			currentChange = changesLoadprofile[slot];
			double searchFor, change;
			if (currentChange > 0) {
				searchFor = 0.0;
				change = consCooling;
			}
			else {
				searchFor = consCooling;
				change = 0.0;
			}
			
			int minuteExtreme = minutePossibleChanges[0][slot];
			int amountBefore = minutePossibleChanges[1][slot];
			int amountAfter = Math.abs(currentChange) - amountBefore;
			int currentMinute = slot*15;
			
			// Mache die mögliche Anzahl an Änderungen vor dem Extremum
			while (currentMinute<=minuteExtreme) {
				if (currentMinute == 0) {
					currentMinute = 1;
				}
				// Passe so bald wie möglich Plan an und berechne pro Änderung
				// amountBefore--
				if (newSchedule[0][currentMinute] == searchFor && amountBefore != 0) {
					newSchedule[0][currentMinute] = change;
					amountBefore--;
					changed = true;
				}
				if (changed && currentMinute>0) {
					if (newSchedule[0][currentMinute] == 0) {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute-1] + riseWarming;
					}
					else {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute-1] + fallCooling;
					}
				}
				
				currentMinute++;
				if (currentMinute == slot*15 && amountBefore != 0) {
					return null;
				}
			}
			
			// Mache die restliche Anzahl an Änderungen nach dem Extremum
			while (currentMinute<(slot+1)*15) {
				// Passe so bald wie möglich Plan an und berechne pro Änderung
				// amountAfter--
				if (newSchedule[0][currentMinute] == searchFor && amountAfter != 0) {
					newSchedule[0][currentMinute] = change;
					amountAfter--;
					changed = true;
				}
				if (changed && currentMinute>0) {
					if (newSchedule[0][currentMinute] == 0) {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute-1] + riseWarming;
					}
					else {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute-1] + fallCooling;
					}
				}
				
				currentMinute++;
				if (currentMinute == slot*15 && amountAfter != 0) {
					return null;
				}
			}			
		}
		scheduleMinutes = newSchedule;
		return newSchedule;
	}

	private void sendLoadprofileToConsumer(Loadprofile loadprofile, boolean isDeltaLoadprofile) {
		RestTemplate rest = new RestTemplate();

		String url = "http://localhost:8080/consumers/" + consumerUUID + "/loadprofiles";
		try {
			RequestEntity<Loadprofile> request = RequestEntity.post(new URI(url)).accept(MediaType.APPLICATION_JSON)
					.body(loadprofile);
			rest.exchange(request, Boolean.class);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void confirmLoadprofile(GregorianCalendar time) {
		if (DateTime.ToString(timeFixed).equals(DateTime.ToString(time))) {
			saveSchedule(scheduleMinutes, timeFixed);
			sendNewLoadprofile();
		}
	}
	
	public void confirmChangedLoadprofile(ChangeRequest cr) {
		// TODO Sende bestätigung, dass in ChangeRequest gesendete Änderung möglich ist
	}
	
	public void declineChangedLoadprofile(ChangeRequest cr) {
		// TODO Sende Absage, dass in ChangeRequest gesendete Änderung nicht möglich ist
	}
}