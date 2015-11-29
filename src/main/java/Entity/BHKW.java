package Entity;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonView;

import Event.IllegalDeviceState;
import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequestSchedule;
import Util.API;
import Util.DateTime;
import Util.Log;
import Util.SimulationBHKW;
import Util.View;

/**
 * Klasse fuer Blockheizkraftwerke (BHKW)
 *
 */
public class BHKW implements Device {
	/**
	 * Stromkoeffizient: Strom = Koeffizient * Waerme
	 */
	@JsonView(View.Detail.class)
	private double chpCoefficient;

	/**
	 * Verbrauch fuer Leistung einer zusaetzlichen kWh in Liter
	 */
	@JsonView(View.Detail.class)
	private double consFuelPerKWh;

	/**
	 * UUID des Consumers des BHKW
	 */
	@JsonView(View.Summary.class)
	private UUID consumerUUID;

	/**
	 * Maximale Last des BHKW
	 */
	@JsonView(View.Summary.class)
	private double maxLoad;

	/**
	 * Preis fuer 1l Brennstoff
	 */
	@JsonView(View.Detail.class)
	private double priceFuel;

	private double priceEex;

	/**
	 * Fahrplan, der für die aktuelle ChangeRequest errechnet wurde
	 */
	@JsonView(View.Detail.class)
	private double[][] scheduleCurrentChangeRequest;

	/**
	 * Fahrplan in Minuten Rythmus, den der Consumer gerade aushandelt mit
	 * Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	@JsonView(View.Detail.class)
	private double[][] scheduleMinutes;

	/**
	 * Fahrplaene in Minuten Rythmus, die schon ausgehandelt sind und fest
	 * stehen mit Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	@JsonView(View.Detail.class)
	private TreeMap<String, double[][]> schedulesFixed = new TreeMap<String, double[][]>();

	/**
	 * Simulator fuer das BHKW
	 */
	@JsonView(View.Detail.class)
	private SimulationBHKW simulation;

	/**
	 * Groesse des Waermespeichers
	 */
	@JsonView(View.Summary.class)
	private double sizeHeatReservoir;

	/**
	 * Status des BHKW
	 */
	@JsonView(View.Summary.class)
	private DeviceStatus status;

	/**
	 * Zeitpunkt, ab dem scheduleMinutes gilt
	 */
	@JsonView(View.Summary.class)
	private GregorianCalendar timeFixed;

	/**
	 * UUID des BHKW
	 */
	@JsonView(View.Summary.class)
	private UUID uuid;

	/**
	 * Gibt an, ob aktuell auf die Antwort auf eine Change Request gewartet wird
	 */
	@JsonView(View.Detail.class)
	private boolean waitForAnswerCR;

	/**
	 * Gibt an, ob während des Wartens auf die Antwort einer Change Request eine
	 * Termperaturänderung war
	 */
	@JsonView(View.Detail.class)
	private boolean waitToChargeDeltaLoadprofile;

	private BHKW() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
		this.priceEex = Marketplace.getEEXPrice();
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

		simulation = new SimulationBHKW(maxLoad, sizeHeatReservoir);

		System.out.println("Neues BHKW: " + uuid);
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
	 * @return Antwort auf den CR
	 */
	public AnswerChangeRequestSchedule receiveChangeRequestSchedule(ChangeRequestSchedule cr) {
		System.out.println("Device erhält Änderungsanfrage");
		double[] changesKWH = cr.getChangesLoadprofile();
		String dateCR = DateTime.ToString(cr.getStartLoadprofile());
		String dateCurrent = DateTime.ToString(timeFixed);
		if (!dateCR.equals(dateCurrent)) {
			// ChangeRequest kann nur fuer scheduleMinutes mit Startzeit
			// timeFixed angefragt werden. Sende daher Antwort ohne Änderungen
			Log.e(uuid, "Änderungen sind für diesen Zeitslot nicht möglich.");
			double[] zero = { 0, 0, 0, 0 };
			AnswerChangeRequestSchedule answer = new AnswerChangeRequestSchedule(cr.getUUID(), zero, 0, 0);
			return answer;
		}

		// Berechne 15-Minuten Werte für Füllstand und Erzeugung
		double[] planned = createValuesLoadprofile(scheduleMinutes[1]);
		double[] oldLevelReservoir = createValuesLoadprofile(scheduleMinutes[0]);
		double[] changeReservoir = new double[numSlots];
		double[] newLevelReservoir = new double[numSlots];
		for (int i = 0; i < numSlots; i++) {
			oldLevelReservoir[i] = oldLevelReservoir[i] / 15;
		}

		double price = 0;
		double currentLevel = oldLevelReservoir[0];

		for (int i = 0; i < numSlots; i++) {
			double value = changesKWH[i];
			double powerGained = 0;

			if (value < 0) {
				// Pruefe, dass produzierte Last nicht unter 0 faellt
				if (planned[i] + value < 0) {
					value = -planned[i];
				}
				// Berechne Waerme, die nun nicht mehr produziert,
				// aber benoetigt wird und daher vom Waermespeicher
				// bezogen werden muss
				double heat = Math.abs(value / chpCoefficient);
				if (currentLevel - heat > 0) {
					currentLevel -= heat;
					powerGained = value;
				} else {
					// Fuelle Speicher so weit wie moeglich auf
					powerGained = -currentLevel;
					currentLevel = 0;
				}
			}
			if (value > 0) {
				// Pruefe, das maximale Last nicht ueberschritten wird
				if (planned[i] / 15 + value / 15 > maxLoad) {
					value = maxLoad - planned[i];
				}
				// Nehme Strom moeglichst vom Waermespeicher
				if (currentLevel > 0) {
					if (currentLevel >= value) {
						// TODO Wie Verhaeltnis abgefuehrte Waerme - daraus
						// erzeugter Strom
						currentLevel = currentLevel - value;
						powerGained = value;
					} else {
						powerGained = currentLevel;
						currentLevel = 0;
					}
				}

				if (powerGained != value) {
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

					// Berechne Preis fuer zusaetzlich benoetigten Brennstoffs
					price += Math.abs(powerProduced * consFuelPerKWh * priceFuel);
				}
			}
			changesKWH[i] = powerGained;
			newLevelReservoir[i] = currentLevel;
			if (i == 0) {
				changeReservoir[i] = currentLevel - oldLevelReservoir[i];
			} else {
				double status = newLevelReservoir[i - 1];
				changeReservoir[i] = currentLevel - status;
			}
		}
		double[][] changes = new double[2][numSlots];
		for (int i = 0; i < numSlots; i++) {
			changes[0][i] = changeReservoir[i];
			changes[1][i] = changesKWH[i];
		}

		double[][] newSchedule = chargeChangedSchedule(changes);
		if (scheduleCurrentChangeRequest == null) {
			scheduleCurrentChangeRequest = new double[2][numSlots * 15];
		}

		for (int i = 0; i < numSlots * 15; i++) {
			try {
				scheduleCurrentChangeRequest[0][i] = newSchedule[0][i];
				scheduleCurrentChangeRequest[1][i] = newSchedule[1][i];
			} catch (NullPointerException e) {
				Log.e(uuid, "....................");
			}
		}

		// Schicke Info mit moeglichen Aenderungen und Preis dafuer an Consumer
		AnswerChangeRequestSchedule answer = new AnswerChangeRequestSchedule(cr.getUUID(), changesKWH, 0, price);

		waitForAnswerCR = true;
		return answer;

	}

	/**
	 * Berechnet den neuen Fahrplan zu den übergebenen Änderungen
	 * 
	 * @param changes
	 *            Geplante Änderungen mit Änderung des Reservoirlevels[0] und
	 *            Änderung der Stromerzeugung [1]
	 * @return Minütlicher plan mit neuen Reservoirlevels [0] und neuer
	 *         Stromerzeugung [1]
	 */
	private double[][] chargeChangedSchedule(double[][] changes) {
		System.out.println("ChargeChangedSchedule");
		double[][] planned = scheduleMinutes.clone();
		double[] plannedPowerLoadprofile = createValuesLoadprofile(planned[1]);

		// Verteile Änderungen auf ganze Viertelstunde
		double[][] changesPerMinute = new double[2][numSlots];
		double[][] valuesToAchieve = new double[2][numSlots];
		for (int i = 0; i < numSlots; i++) {
			changesPerMinute[0][i] = Math.round(100.00 * changes[0][i] / 15) / 100.00;
			changesPerMinute[1][i] = Math.round(100.00 * changes[1][i] / 15) / 100.00;

			if (i == 0) {
				valuesToAchieve[0][i] = Math.round(100.00 * (planned[0][14] + changes[0][i])) / 100.00;

			} else {
				valuesToAchieve[0][i] = Math.round(100.00 * (valuesToAchieve[0][i - 1] + changes[0][i])) / 100.00;
			}
			valuesToAchieve[1][i] = Math.round(100.00 * (plannedPowerLoadprofile[i] + changes[1][i])) / 100.00;
		}

		double achievedPower = 0;
		boolean repeat = false;
		double repeatPowerToChange = 0, repeatLevelToChange = 0, repeatPowerAchieved = 0, repeatLevelAchieved = 0,
				totalPower = 0;

		// Nehme Änderungen pro Viertel-Stunden-Slot vor
		for (int n = 1; n < numSlots + 1; n++) {
			int slotAnfang = (n - 1) * 15;
			int slotEnde = n * 15;
			totalPower = 0;

			// Wenn für diesen Slot keine Änderungen notwendig sind,
			// aktualisiere lediglich die Werte des Füllstandes, falls das nicht
			// Slot 0 ist
			if (changesPerMinute[0][n - 1] == 0) {
				if (n - 1 != 0) {
					for (int j = slotAnfang; j < slotEnde; j++) {
						planned[0][j] = planned[0][slotAnfang - 1];
					}
				}
				continue;
			}

			for (int j = slotAnfang; j < slotEnde; j++) {
				// Berechne das neue Level des Wärmespeichers und die neue
				// Stromerzeugung, wenn mit den Änderungen
				double levelPlanned = planned[0][j];
				double powerPlanned = planned[1][j];
				double newLevel;
				double restrictionChangePower = maxLoad;
				double newPower;

				// Prüfe ob geplante Werte bereits eine Grenze erreich haben,
				// dann ist keine Änderung für diese Minute möglich
				// und in den folgenden Minuten muss die Änderung größer sein
				if ((changesPerMinute[0][n - 1] > 0) && (levelPlanned == sizeHeatReservoir || powerPlanned == maxLoad)
						|| (changesPerMinute[0][n - 1] < 0) && (levelPlanned == 0 || powerPlanned == 0)) {
					// Übernehme für das geplante Level das Level von der
					// vorherigen Minute
					// Die geplante Stromerzeugung bleibt
					if (j != 0) {
						newLevel = planned[0][j - 1];
						planned[0][j] = newLevel;
					} else {
						newLevel = planned[0][j];
					}
					newPower = planned[1][j];

					// Erhöhe die benötigte Änderung
					changesPerMinute[0][n - 1] *= 1.07;
					changesPerMinute[1][n - 1] *= 1.07;
				} else {
					// Berechne den neuen Wert für das Level nach
					// changesPerMinute
					if (j == 0 || repeat) {
						newLevel = Math.round(100.00 * (changesPerMinute[0][n - 1] + planned[0][j])) / 100.00;
					} else {
						newLevel = Math.round(100.00 * (planned[0][j - 1] + changesPerMinute[0][n - 1])) / 100.00;
					}

					// Prüfe, ob der neu berechnete Wert die Grenzen über- oder
					// unterschreitet.
					// Wenn ja, passe ihn an und lege die Beschränkung für die
					// Erzeugung fest.
					// Hierbei wird so viel an Änderung wie möglich durchgeführt
					if (newLevel < 0) {
						newLevel = 0;
						double changedLevel = planned[0][j - 1];
						restrictionChangePower = chpCoefficient * changedLevel;
					} else if (newLevel > sizeHeatReservoir) {
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
						newPower = 0;
						double changedPower = planned[1][j];
						newLevel = changedPower / chpCoefficient;
					} else if (newPower > maxLoad) {
						newPower = maxLoad;
						double changedPower = maxLoad - planned[1][j];
						newLevel = Math.abs(100.00 * changedPower / chpCoefficient) / 100.00;
					}

					if (repeat) {
						repeatPowerAchieved += Math.round(100.00 * (newPower - planned[1][j])) / 100.00;
						repeatLevelAchieved += Math.round(100.00 * (newLevel - planned[0][j])) / 100.00;

						changesPerMinute[1][n - 1] = repeatPowerToChange - repeatPowerAchieved;
						changesPerMinute[0][n - 1] = repeatLevelToChange - repeatLevelAchieved;
					}

					planned[0][j] = newLevel;
					planned[1][j] = newPower;
					achievedPower += newPower;
				}
				totalPower += newPower;

				if (repeat) {
					if (repeatPowerAchieved == repeatPowerToChange || repeatLevelAchieved == repeatLevelToChange) {
						repeat = false;
						achievedPower = 0;
						break;
					}
				}

				if (j == slotEnde - 1) {
					if (repeat) {
						repeatPowerToChange = Math.round(100.00 * repeatPowerToChange - repeatPowerAchieved) / 100.00;
						repeatLevelToChange = Math.round(100.00 * repeatLevelToChange - repeatLevelAchieved) / 100.00;
						repeatPowerAchieved = 0;
						repeatLevelAchieved = 0;

						changesPerMinute[0][n - 1] = Math.round(100.00 * repeatPowerToChange / 5) / 100.00;
						changesPerMinute[1][n - 1] = Math.round(100.00 * repeatPowerToChange / 5) / 100.00;

						if (changesPerMinute[0][n - 1] == 0 || changesPerMinute[1][n - 1] == 0) {
							repeat = true;
							totalPower = valuesToAchieve[1][n - 1];
							newLevel = valuesToAchieve[1][n - 1];
						}
					} else if (repeat) {
						achievedPower = Math.round(100.00 * achievedPower) / 100.00;

						double deviationPower = Math.round(Math.abs(valuesToAchieve[1][n - 1] - totalPower) * 100.00)
								/ 100.00;
						double deviationLevel = Math.round(Math.abs(valuesToAchieve[0][n - 1] - newLevel) * 100.00)
								/ 100.00;

						if (!(deviationPower == 0 || deviationLevel == 0)) {
							if (deviationPower != deviationLevel) {
								deviationPower = (deviationPower + deviationLevel) / 2;
								deviationLevel = deviationPower;
							}

							repeat = true;
							repeatPowerToChange = deviationPower;
							repeatLevelToChange = deviationLevel;
							repeatPowerAchieved = 0;
							repeatLevelAchieved = 0;

							// Verkleinere die Höhe des angefragten Ausgleichs
							// pro
							// Minute
							while (deviationPower > 0.5 || deviationLevel > 0.5) {
								deviationPower = Math.round(deviationPower / 5 * 100.00) / 100.00;
								deviationLevel = Math.round(deviationLevel / 5 * 100.00) / 100.00;
							}
							changesPerMinute[0][n - 1] = deviationLevel;
							changesPerMinute[1][n - 1] = deviationPower;

							n--;

						} else {
							repeat = false;
						}
						achievedPower = 0;
						totalPower = 0;
					}
				}
			}
		}
		return planned;
	}

	/**
	 * Das Device speichert das Lastprofil und den Fahrplan zur uebergebenen
	 * Zeit als fest ab.
	 * 
	 * @param time
	 *            Zeit, fuer die Lastprofil und Fahrplan bestaetigt werden
	 */
	public void confirmLoadprofile(String time) {
		System.out.println("Das BHKW erhält die Bestätigung des Angebots");
		if (DateTime.ToString(timeFixed).equals(time)) {
			saveSchedule(scheduleMinutes, timeFixed);
			System.out.println("scheduleFixed gespeichert für: " + DateTime.ToString(timeFixed));
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
				valuesLoadprofile[n] = Math.round(100.00 * summeMin) / 100.00;
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
	 * Gibt den SimpleName der Klasse zurück
	 * 
	 * @return SimpleClassName
	 */
	public String getType() {
		return getClass().getSimpleName();
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

		double powerPlanned = 0, powerScaled;
		int minute = currentTime.get(Calendar.MINUTE);
		currentTime.set(Calendar.MINUTE, 0);
		System.out.println("Zeit: " + DateTime.ToString(currentTime));

		double[][] plan = schedulesFixed.get(DateTime.ToString(currentTime));
		if (plan == null) {
			System.out.println("Es liegt kein schedulesFixed für " + DateTime.ToString(currentTime) + " vor.");
			System.out.println("ScheduleMintues gilt ab: " + DateTime.ToString(timeFixed));
			return;
		}
		powerPlanned = plan[1][minute];

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
		System.out.println("Device enthält Zusage für Laständerungen");
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
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, timeFixed, Loadprofile.Type.DELTA);
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

		double[] oldPlan = schedulesFixed.get(DateTime.ToString(start))[1];
		double[] newPlan = simulation.getNewSchedule(start)[1];

		newPlan[minute] = oldPlan[minute];

		// Pruefe, ob es weitere Abweichungen gibt
		if (!oldPlan.equals(newPlan)) {
			if (DateTime.ToString(start).equals(DateTime.ToString(timeFixed)) && waitForAnswerCR) {
				waitToChargeDeltaLoadprofile = true;
				Log.d(uuid, "Warten mit Versenden des Deltalastprofils auf die Antwort der Änderungsanfrage.");
			} else {
				double[] deltaValues = new double[numSlots];
				boolean change = false;
				for (int i = 0; i < numSlots; i++) {
					deltaValues[i] = oldPlan[i] - newPlan[i];
					if (deltaValues[i] != 0) {
						change = true;
					}
				}
				
				if (change) {
					Log.d(uuid, "Versende Deltalastprofil an den Consumer.");
					Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, start, Loadprofile.Type.DELTA);
					sendLoadprofileToConsumer(deltaLoadprofile);
				}
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
		API<Loadprofile, Void> api2 = new API<Loadprofile, Void>(Void.class);
		api2.consumers(consumerUUID).loadprofiles();
		api2.call(this, HttpMethod.POST, loadprofile);
	}

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile() {
		System.out.println("BHKW " + uuid + " sendet neues Lastprofil.");
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

		System.out.println("Neuer scheduleMinutes wird geholt für: " + DateTime.ToString(timeFixed));
		scheduleMinutes = simulation.getNewSchedule(timeFixed);
		valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[1]);

		// Berechne die entstehenden Selbstkosten
		double sumLoadprofile = 0;
		for (int i = 0; i < numSlots; i++) {
			sumLoadprofile += valuesLoadprofile[i];
		}
		double netCosts = sumLoadprofile * consFuelPerKWh * priceFuel;

		// Lege minPrice und priceSugg fest.

		double priceSugg = Math.max(priceEex, netCosts);
		double minPrice = Math.min(priceEex, netCosts);

		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, priceSugg, minPrice,
				Double.POSITIVE_INFINITY, Loadprofile.Type.INITIAL);
		sendLoadprofileToConsumer(loadprofile);
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
			this.consumerUUID = consumerUUID;
		} else {
			throw new IllegalDeviceState();
		}

		// sende an den eigenen Consumer das Lastprofil
		sendNewLoadprofile();
	}
}
