package Entity;

import java.net.URI;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import Event.IllegalDeviceState;
import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequest;
import Util.API;
import Util.DateTime;
import Util.DeviceStatus;
import Util.SimulationBHKW;
import start.Device;
import start.Loadprofile;

/**
 * Klasse fuer Blockheizkraftwerke (BHKW)
 *
 */
public class BHKW implements Device {
	/**
	 * Stromkoeffizient: Strom = Koeffizient * Waerme
	 */
	private double chpCoefficient;

	/**
	 * Verbrauch fuer Leistung einer zusaetzlichen kWh in Liter
	 */
	private double consFuelPerKWh;

	/**
	 * UUID des Consumers des BHKW
	 */
	private UUID consumerUUID;

	/**
	 * Maximale Last des BHKW
	 */
	private double maxLoad;

	/**
	 * Preis fuer 1l Brennstoff
	 */
	private double priceFuel;

	/**
	 * Fahrplan, der für die aktuelle ChangeRequest errechnet wurde
	 */
	private double[][] scheduleCurrentChangeRequest;

	/**
	 * Fahrplan in Minuten Rythmus, den der Consumer gerade aushandelt mit
	 * Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	private double[][] scheduleMinutes;

	/**
	 * Fahrplaene in Minuten Rythmus, die schon ausgehandelt sind und fest
	 * stehen mit Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	private TreeMap<String, double[][]> schedulesFixed = new TreeMap<String, double[][]>();

	/**
	 * Simulator fuer das BHKW
	 */
	private SimulationBHKW simulation;

	/**
	 * Groesse des Waermespeichers
	 */
	private double sizeHeatReservoir;

	/**
	 * Status des BHKW
	 */
	private DeviceStatus status;

	/**
	 * Zeitpunkt, ab dem scheduleMinutes gilt
	 */
	private GregorianCalendar timeFixed;

	/**
	 * UUID des BHKW
	 */
	private UUID uuid;

	/**
	 * waitForAnswerCR: Gibt an, ob aktuell auf die Antwort auf eine Change
	 * Request gewartet wird waitForChargeDeltaLoadprofile: Gibt an, ob während
	 * des Wartens auf die Antwort einer Change Request eine Termperaturänderung
	 * war
	 */
	private boolean waitForAnswerCR, waitToChargeDeltaLoadprofile;

	private BHKW() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}

	/**
	 * Erstellt ein neues Blockheizkraftwerk.
	 * 
	 * @param chpCoefficient
	 *            Stromkoeffizient: Strom = Koeffizient * Waerme
	 * @param priceFuel
	 *            Preis des Brennstoffs pro Liter
	 * @param consFuelPerKWh
	 *            Benoetigter Brennstoff in Liter, um eine kWh Strom zu
	 *            produzieren
	 * @param sizeHeatReservoir
	 *            Groesse des Waermespeichers
	 * @param maxLoad
	 *            Maximale Last des Blockheizkraftwerks
	 */
	public BHKW(double chpCoefficient, double priceFuel, double consFuelPerKWh, double sizeHeatReservoir,
			double maxLoad) {
		this();
		this.chpCoefficient = chpCoefficient;
		this.priceFuel = priceFuel;
		this.consFuelPerKWh = consFuelPerKWh;
		this.sizeHeatReservoir = sizeHeatReservoir;
		this.maxLoad = maxLoad;

		simulation = new SimulationBHKW(maxLoad);

		status = DeviceStatus.INITIALIZED;
	}

	/**
	 * Ueberprueft, ob die gewuentsche Aenderung des Lastprofils moeglich ist
	 * und sendet eine Antwort mit der moeglichen Aenderung und dem zugehoerigen
	 * Preis an den Consumer.
	 * 
	 * @param cr
	 *            Enthaelt Informationen, wie das Lastprofil geaendert werden
	 *            soll
	 * @return
	 */
	public AnswerChangeRequest changeLoadprofile(ChangeRequestSchedule cr) {
		double[] changesKWH = cr.getChangesLoadprofile();
		String dateCR = DateTime.ToString(cr.getStartLoadprofile());
		String dateCurrent = DateTime.ToString(cr.getStartLoadprofile());
		if (!dateCR.equals(dateCurrent)) {
			// TODO Fehler
			// ChangeRequest kann nur fuer scheduleMinutes mit Startzeit
			// timeFixed angefragt werden
		}

		// Berechne 15-Minuten Werte für Füllstand und Erzeugung
		double[] planned = createValuesLoadprofile(scheduleMinutes[1]);
		double[] oldLevelReservoir = createValuesLoadprofile(scheduleMinutes[0]);
		double[] changeReservoir = new double[numSlots];
		double[] newLevelReservoir = new double[numSlots];
		for (int i = 0; i < numSlots; i++) {
			oldLevelReservoir[i] = oldLevelReservoir[i] / 15;
			System.out.println("Level Reservoir: " + oldLevelReservoir[i]);
		}

		double price = 0;
		double currentLevel = oldLevelReservoir[0];

		for (int i = 0; i < numSlots; i++) {
			double value = changesKWH[i];
			System.out.println("\nZu erreichender Wert: " + value);
			double powerGained = 0;

			if (value < 0) {
				// Pruefe, dass produzierte Last nicht unter 0 faellt
				if (planned[i] + value < 0) {
					System.out.println("Last fällt unter 0");
					value = -planned[i];
				}
				// Berechne Waerme, die nun nicht mehr produziert,
				// aber benoetigt wird und daher vom Waermespeicher
				// bezogen werden muss
				double heat = Math.abs(value / chpCoefficient);
				System.out.println("Heat: " + heat);
				if (currentLevel - heat > 0) {
					System.out.println("Hole alle aus Wärmespeicher");
					currentLevel -= heat;
					powerGained = value;
				} else {
					// Fuelle Speicher so weit wie moeglich auf
					powerGained = -currentLevel;
					currentLevel = 0;
				}
				System.out.println("Current Level: " + currentLevel);
				System.out.println("Power Gained: " + powerGained);
			}
			if (value > 0) {
				// Pruefe, das maximale Last nicht ueberschritten wird
				if (planned[i] / 15 + value / 15 > maxLoad) {
					value = maxLoad - planned[i];
					System.out.println("Maximale Last wird überschritten " + maxLoad);
					System.out.println("Geplanter Wert: " + planned[i]);
					System.out.println("Neuer Value: " + value);
				}
				// Nehme Strom moeglichst vom Waermespeicher
				if (currentLevel > 0) {
					System.out.println("Nehme Strom vom Waermespeicher");
					if (currentLevel >= value) {
						// TODO Wie Verhaeltnis abgefuehrte Waerme - daraus
						// erzeugter Strom
						currentLevel = currentLevel - value;
						powerGained = value;
					} else {
						powerGained = currentLevel;
						currentLevel = 0;
					}
					System.out.println("Power Gained: " + powerGained);
				}

				if (powerGained != value) {
					System.out.println("Produziere zusätzlichen Strom");
					// Produziere restlichen Strom, speichere dabei
					// als Nebenprodukt produzierte Waerme und
					// berechne anfallende Kosten
					double powerProduced = value - powerGained;
					double heatProduced = powerProduced / chpCoefficient;
					if (currentLevel + heatProduced <= sizeHeatReservoir) {
						currentLevel += heatProduced;
					} else {
						powerProduced = sizeHeatReservoir - currentLevel;
						currentLevel = sizeHeatReservoir;
					}
					// Berechne, wie viel Energie tatsaechlich produziert werden
					// konnte
					powerGained += powerProduced;

					// Berechne Preis fuer zusaetzlich benoetigten Brennstoff
					price += Math.abs(powerProduced * consFuelPerKWh * priceFuel);
				}
				System.out.println("Current Level: " + currentLevel);
				System.out.println("Power Gained: " + powerGained);
			}
			changesKWH[i] = powerGained;
			newLevelReservoir[i] = currentLevel;
			if (i == 0) {
				changeReservoir[i] = currentLevel - oldLevelReservoir[i];
			} else {
				double status = newLevelReservoir[i - 1];
				changeReservoir[i] = currentLevel - status;
				System.out.println("\nAusgangspunkt: " + status);
				System.out.println("Neues Level: " + currentLevel);
				System.out.println("Ergebnis: " + changeReservoir[i]);
			}
		}
		double[][] changes = new double[2][numSlots];
		System.out.println("Mögliche vorgenommene Änderungen: ");
		for (int i = 0; i < numSlots; i++) {
			changes[0][i] = changeReservoir[i];
			changes[1][i] = changesKWH[i];
			System.out.println("Level: " + changes[0][i] + " Strom: " + changes[1][i]);
		}

		double[][] newSchedule = chargeChangedSchedule(changes);
		for (int i = 0; i < numSlots * 15; i++) {
			scheduleCurrentChangeRequest[0][i] = newSchedule[0][i];
			scheduleCurrentChangeRequest[1][i] = newSchedule[1][i];
		}

		// Schicke Info mit moeglichen Aenderungen und Preis dafuer an Consumer
		AnswerChangeRequest answer = new AnswerChangeRequest(cr.getUUID(), changesKWH, price);

		waitForAnswerCR = true;
		return answer;
	}

	private double[][] chargeChangedSchedule(double[][] changes) {
		double[][] planned = scheduleMinutes.clone();
		double[] plannedPowerLoadprofile = createValuesLoadprofile(planned[1]);

		// Verteile Änderungen auf ganze Viertelstunde
		double[][] changesPerMinute = new double[2][numSlots];
		double[][] valuesToAchieve = new double[2][numSlots];
		for (int i = 0; i < numSlots; i++) {
			System.out.println("Änderung Gesamt:     Speicher: " + changes[0][i] + ", Erzeugung: " + changes[1][i]);
			changesPerMinute[0][i] = Math.round(100.00 * changes[0][i] / 15) / 100.00;
			changesPerMinute[1][i] = Math.round(100.00 * changes[1][i] / 15) / 100.00;
			System.out.println("Änderung pro Minute: Speicher: " + changesPerMinute[0][i] + ", Erzeugung: "
					+ changesPerMinute[1][i]);

			if (i == 0) {
				valuesToAchieve[0][i] = planned[0][14] + changes[0][i];

			} else {
				valuesToAchieve[0][i] = valuesToAchieve[0][i - 1] + changes[0][i];
			}
			valuesToAchieve[1][i] = plannedPowerLoadprofile[i] + changes[1][i];

			System.out.println(
					"Zu Erreichen: Speicher: " + valuesToAchieve[0][i] + ", Erzeugung: " + valuesToAchieve[1][i]);
		}

		double achievedPower = 0;
		boolean repeat = false;
		double repeatPowerToChange = 0, repeatLevelToChange = 0, repeatPowerAchieved = 0, repeatLevelAchieved = 0;

		// Nehme Änderungen pro Viertel-Stunden-Slot vor
		for (int n = 1; n < numSlots + 1; n++) {
			System.out.println("Änderung pro 15 Minuten");
			int slotAnfang = (n - 1) * 15;
			int slotEnde = n * 15;
			for (int j = slotAnfang; j < slotEnde; j++) {
				// Berechne das neue Level des Wärmespeichers und die neue
				// Stromerzeugung,
				// wenn mit den Änderungen
				double levelPlanned = planned[0][j];
				double powerPlanned = planned[1][j];
				double newLevel;
				double restrictionChangePower = maxLoad;
				double newPower;

				// Prüfe ob geplante Werte bereits eine Grenze erreich haben,
				// dann ist keine Änderung für diese Minute möglich
				// und in den folgenden Minuten muss die Änderung größer sein
				if (levelPlanned == 0 || levelPlanned == sizeHeatReservoir || powerPlanned == 0
						|| powerPlanned == maxLoad) {
					System.out.println("Grenzwerte sind schon vor Änderung erreicht gewesen.");
					// Übernehme für das geplante Level das Level von der
					// vorherigen Minute
					// Die geplante Stromerzeugung bleibt
					if (j != 0) {
						newLevel = planned[0][j - 1];
						planned[0][j] = newLevel;
					} else {
						newLevel = planned[0][j];
					}

					// Erhöhe die benötigte Änderung
					changesPerMinute[0][n - 1] *= 1.07;
					changesPerMinute[1][n - 1] *= 1.07;
				} else {
					// Berechne den neuen Wert für das Level
					if (j == 0) {
						newLevel = planned[0][j] + Math.round(100.00 * changesPerMinute[0][n - 1]) / 100.00;
					} else {
						newLevel = Math.round(100.00 * (planned[0][j - 1] + changesPerMinute[0][n - 1])) / 100.00;
					}

					// Prüfe, ob der neu berechnete Wert die Grenezen über- oder
					// unterschreitet
					// Wenn ja, passe ihn an und lege die Beschränkung für die
					// Erzeugung fest
					if (newLevel < 0) {
						System.out.println("-----------------------Level wäre < 0");
						newLevel = 0;
						double changedLevel = planned[0][j - 1];
						restrictionChangePower = chpCoefficient * changedLevel;
					} else if (newLevel > sizeHeatReservoir) {
						System.out.println("-----------------------Level wäre zu hoch");
						newLevel = sizeHeatReservoir;
						double changedLevel = sizeHeatReservoir - planned[0][j];
						restrictionChangePower = chpCoefficient * changedLevel;
					}

					// Passe Power so an, dass die von newLevel kommenden
					// Restriktionen
					// eingehalten werden
					if (Math.abs(restrictionChangePower) < Math.abs(changesPerMinute[1][n - 1])) {
						newPower = planned[1][j] + restrictionChangePower;
					} else {
						newPower = planned[1][j] + changesPerMinute[1][n - 1];
					}

					// Prüfe, ob Power die festgesetzten Grenzen unter- oder
					// überschreitet
					// Falls ja, passe newPower und newLevel entsprechend an.
					if (newPower < 0) {
						System.out.println("------------------Power wäre < 0");
						newPower = 0;
						double changedPower = planned[1][j];
						newLevel = changedPower / chpCoefficient;
					} else if (newPower > maxLoad) {
						System.out.println("------------------Power wäre zu hoch");
						newPower = maxLoad;
						double changedPower = maxLoad - planned[1][j];
						newLevel = changedPower / chpCoefficient;
					}

					if (repeat) {
						if (j == 0) {
							repeatPowerAchieved += Math.round(100.00 * (newPower - planned[1][j])) / 100.00;
							repeatLevelAchieved += Math.round(100.00 * (newLevel - planned[0][j])) / 100.00;
						} else {
							repeatPowerAchieved += Math.round(100.00 * (newPower - planned[1][j])) / 100.00;
							repeatLevelAchieved += Math.round(100.00 * (newLevel - planned[0][j - 1])) / 100.00;
						}
						changesPerMinute[1][n - 1] = repeatPowerToChange - repeatPowerAchieved;
						changesPerMinute[0][n - 1] = repeatLevelToChange - repeatLevelAchieved;
						System.out.println("Neue Werte pro Minute: Level: " + changesPerMinute[0][n - 1]
								+ ", Erzeugung: " + changesPerMinute[0][n - 1]);
					}

					planned[0][j] = newLevel;
					planned[1][j] = newPower;
					achievedPower += newPower;
				}

				System.out.println(
						(j + 1) + ". Minute neu: Speicher: " + planned[0][j] + ", Erzeugung: " + planned[1][j]);

				if (repeat) {
					System.out.println("Power: Ziel: " + repeatPowerToChange + ", erreicht: " + repeatPowerAchieved);
					System.out.println("Level: Ziel: " + repeatLevelToChange + ", erreicht: " + repeatLevelAchieved);
					if (repeatPowerAchieved == repeatPowerToChange && repeatLevelAchieved == repeatLevelToChange) {
						System.out.println("Änderung erreicht.");
						repeat = false;
						achievedPower = 0;
						break;
					}
				}

				if (j == slotEnde - 1) {
					achievedPower = Math.round(100.00 * achievedPower) / 100.00;

					double deviationPower = Math.round(Math.abs(valuesToAchieve[1][n - 1] - achievedPower) * 100.00)
							/ 100.00;
					double deviationLevel = Math.round(Math.abs(valuesToAchieve[0][n - 1] - newLevel) * 100.00)
							/ 100.00;

					System.out.println("Slotende");
					System.out.println("Erreichter Strom: " + achievedPower + " Ziel: " + valuesToAchieve[1][n - 1]);
					System.out.println("Abweichung: " + deviationPower);
					System.out.println("Erreichtes Level: " + newLevel + " Ziel: " + valuesToAchieve[0][n - 1]);
					System.out.println("Abweichung: " + deviationLevel);
					if (!(deviationPower == 0 || deviationLevel == 0)) {
						System.out.println("-------------------Ziel insgesamt nicht erreicht, erneuter Durchlauf");
						System.out.println("Wegen Erzeugung: " + (deviationPower != 0));
						System.out.println("Wegen Speicher:  " + (deviationLevel != 0));

						repeat = true;
						repeatPowerToChange = deviationPower;
						repeatLevelToChange = deviationLevel;
						repeatPowerAchieved = 0;
						repeatLevelAchieved = 0;

						// Verkleinere die Höhe des angefragten Ausgleichs pro
						// Minute
						while (deviationPower > 0.5 || deviationLevel > 0.5) {
							deviationPower = Math.round(deviationPower / 5 * 100.00) / 100.00;
							deviationLevel = Math.round(deviationLevel / 5 * 100.00) / 100.00;
						}
						changesPerMinute[0][n - 1] = deviationLevel;
						changesPerMinute[1][n - 1] = deviationPower;
						System.out.println("Noch notwendige Änderungen: Level: " + changesPerMinute[0][n - 1]
								+ ", Erzeugung: " + changesPerMinute[1][n - 1]);

						n--;

					} else {
						repeat = false;
					}
					achievedPower = 0;
				}
			}
		}
		return changesPerMinute;
	}

	/**
	 * Das Device speichert das Lastprofil und den Fahrplan zur uebergebenen
	 * Zeit als fest ab.
	 * 
	 * @param time
	 *            Zeit, fuer die Lastprofil und Fahrplan bestaetigt werden
	 */
	public void confirmLoadprofile(GregorianCalendar time) {
		if (DateTime.ToString(timeFixed).equals(DateTime.ToString(time))) {
			saveSchedule(scheduleMinutes, timeFixed);
			sendNewLoadprofile();
		}
	}

	/**
	 * Berechnet Lastprofil auf Viertel-Stunden-Basis fuer uebergebenen
	 * Minutenfahrplan. Hierbei werden die Werte pro Viertelstunde aufsummiert
	 * und als Lastprofil gespeichert.
	 * 
	 * @param schedule
	 *            Minuetlicher Fahrplan, fuer den Lastprofil erstellt werden
	 *            soll
	 * @return Array mit den Werten des Lastprofils
	 */
	public double[] createValuesLoadprofile(double[] schedule) {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		int n = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + schedule[i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[n] = summeMin;
				n++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	/**
	 * Liefert den aktuellen Status des Kuehlschranks
	 * 
	 * @return Aktueller Status des Kuehlschranks
	 */
	public DeviceStatus getStatus() {
		return status;
	}

	/**
	 * Liefert die uuid des Devices
	 * 
	 * @return Uuid des Devices
	 */
	public UUID getUUID() {
		return uuid;
	}

	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub
	}

	@Override
	public void ping() {
		GregorianCalendar currentTime = DateTime.now();
		currentTime.set(Calendar.SECOND, 0);
		currentTime.set(Calendar.MILLISECOND, 0);

		double powerPlanned, powerScaled;
		int minute = currentTime.get(Calendar.MINUTE);
		powerPlanned = schedulesFixed.get(DateTime.ToString(currentTime))[1][minute];
		powerScaled = simulation.getPower(currentTime);

		if (powerPlanned != powerScaled) {
			// Schicke DeltaLastprofil, falls weitere aenderungen
			sendDeltaLoadprofile(currentTime, powerScaled);
		}
	}

	/**
	 * Methode wird aufgerufen, wenn die Bestätigung/ Absage für den
	 * scheduleCurrentChangeRequest eintrifft. War während des Wartens auf diese
	 * Antwort eine Änderung und muss evtl. ein Deltalastprofil erstellt werden,
	 * so wird das erledigt und an den Consumer gesendet
	 * 
	 * @param acceptChange
	 *            Gibt an, ob das Angebot vom Consumer bestätigt (true) oder
	 *            abgelehnt (false) wird
	 */
	public void receiveAnswerChangeRequest(boolean acceptChange) {
		if (acceptChange) {
			scheduleMinutes = scheduleCurrentChangeRequest;
		}
		// Erstelle das aktuelle Deltalastprofil, wenn während des Wartens auf
		// die Antwort eine Temperaturänderung war
		if (waitToChargeDeltaLoadprofile) {
			double[] oldPlan = scheduleMinutes[1];
			double[] newPlan = simulation.getNewSchedule(timeFixed)[1];
			;
			double[] deltaValues = new double[numSlots];

			double sumDeltaValues = 0;
			for (int i = 0; i < numSlots; i++) {
				deltaValues[i] = oldPlan[i] - newPlan[i];
				sumDeltaValues += Math.abs(deltaValues[i]);
			}

			if (sumDeltaValues != 0) {
				// Versende deltaValues als Delta-Lastprofil an den Consumer
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, timeFixed, 0.0, true);
				sendLoadprofileToConsumer(deltaLoadprofile);
			}
			waitToChargeDeltaLoadprofile = false;
		}
		scheduleCurrentChangeRequest = null;
		waitForAnswerCR = false;
	}

	/**
	 * Speichert den uebergebenen Fahrplan als festen Fahrplan ab
	 * 
	 * @param schedule
	 *            Fahrplan, der abgespeichert werden soll
	 * @param start
	 *            Zeitpunkt, zu dem schedule startet
	 */
	public void saveSchedule(double[][] schedule, GregorianCalendar start) {
		schedulesFixed.put(DateTime.ToString(start), schedule);
	}

	/**
	 * Erzeugt bei Bedarf ein Deltalastprofil und sendet es an den Consumer.
	 * Methode wird aufgerufen, wenn eine anderer Wert gemessen wurde, als in
	 * der Minute geplant war.
	 * 
	 * @param start
	 *            Zeitpunkt, wann die Abweichung gemessen wurde
	 * @param valueChanged
	 *            Wert, der gemessen wurde
	 */
	public void sendDeltaLoadprofile(GregorianCalendar start, double valueChanged) {
		int minute = start.get(Calendar.MINUTE);
		start.set(Calendar.MINUTE, 0);

		double[] oldPlan = schedulesFixed.get(start)[1];
		double[] newPlan = simulation.getNewSchedule(start)[1];

		newPlan[minute] = oldPlan[minute];

		// Pruefe, ob es weitere Abweichungen gibt
		if (!oldPlan.equals(newPlan)) {
			if (DateTime.ToString(start).equals(DateTime.ToString(timeFixed)) && waitForAnswerCR) {
				// Warte mit Versenden des Delta-Lastprofils auf die Antwort der
				// ChangeRequest
				waitToChargeDeltaLoadprofile = true;
			} else {
				double[] deltaValues = new double[numSlots];
				for (int i = 0; i < numSlots; i++) {
					deltaValues[i] = oldPlan[i] - newPlan[i];
				}

				// Versende deltaValues als Delta-Lastprofil an den Consumer
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, start, 0.0, true);
				sendLoadprofileToConsumer(deltaLoadprofile);
			}
		}
	}

	/**
	 * Sendet dem Consumer ein Lastprofil
	 * 
	 * @param loadprofile
	 *            Lastprofil, das an den Consumer gesendet werden soll
	 */
	private void sendLoadprofileToConsumer(Loadprofile loadprofile) {
		RestTemplate rest = new RestTemplate();

		String url = new API().consumers(consumerUUID).loadprofiles().toString();
		try {
			RequestEntity<Loadprofile> request = RequestEntity.post(new URI(url)).accept(MediaType.APPLICATION_JSON)
					.body(loadprofile);
			rest.exchange(request, Boolean.class);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile() {
		double[] valuesLoadprofile;

		/*
		 * Pruefe, ob dateFixed gesetzt, wenn nicht setze neu und erstelle
		 * initiales Lastprofil
		 */
		if (timeFixed == null) {
			/*
			 * Wenn noch nicht gesetzt, erstelle initialen Fahrplan fuer bis zur
			 * naechsten Stunde
			 */
			timeFixed = DateTime.now();
			timeFixed.set(Calendar.MINUTE, 0);
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);

			scheduleMinutes = simulation.getNewSchedule(timeFixed);
			valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[1]);

			timeFixed.set(Calendar.MINUTE, 0);

			saveSchedule(scheduleMinutes, timeFixed);
		}
		// Zaehle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		scheduleMinutes = simulation.getNewSchedule(timeFixed);
		valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[1]);

		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, 0.0, false);
		// sendLoadprofileToConsumer(loadprofile);
	}

	/**
	 * Legt den Consumer fuer das Device fest
	 * 
	 * @param consumerUUID
	 *            Uuid des Consumers
	 */
	public void setConsumer(UUID consumerUUID) {
		if (status == DeviceStatus.INITIALIZED) {
			status = DeviceStatus.READY;
			consumerUUID = uuid;
		} else {
			throw new IllegalDeviceState();
		}

		// sende an den eigenen Consumer das Lastprofil
		sendNewLoadprofile();
	}
}
