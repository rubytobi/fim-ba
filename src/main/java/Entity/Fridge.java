package Entity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;
import Event.IllegalDeviceState;
import Packet.ChangeRequestSchedule;
import Util.API;
import Util.DateTime;
import Util.DeviceStatus;
import Util.Log;
import Util.SimulationFridge;
import start.Device;
import start.Loadprofile;
import start.View;

/**
 * Klasse fuer Kuehlschraenke
 *
 */
public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	@JsonView(View.Detail.class)
	private double[][] scheduleMinutes = new double[2][15 * numSlots];

	// Zeitpunkt, ab dem scheduleMinutes gilt
	@JsonView(View.Summary.class)
	private GregorianCalendar timeFixed;

	// Fahrpläne, die schon ausgehandelt sind und fest stehen
	@JsonView(View.Detail.class)
	private TreeMap<String, double[]> schedulesFixed = new TreeMap<String, double[]>();

	// Fahrplan, der für die aktuelle ChangeRequest errechnet wurde
	private double[][] scheduleCurrentChangeRequest = new double[2][numSlots * 15];

	// Lastprofile, die schon ausgehandelt sind und fest stehen
	@JsonView(View.Detail.class)
	private TreeMap<String, double[]> loadprofilesFixed = new TreeMap<String, double[]>();

	// currTemp: Temperatur, bei der der nächste neue Fahrplan beginnen soll
	@JsonView(View.Detail.class)
	private double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;

	// currCooling: Gibt an, ob der nächste Fahrplan mit Kühlen beginnen soll
	@JsonView(View.Detail.class)
	private boolean currCooling;

	// Wie viel Grad pro Minute erwärmt bzw. kühlt der Kühlschrank?
	@JsonView(View.Detail.class)
	private double fallCooling, riseWarming;

	// Verbrauch zum Kühlen pro Minute in Wh
	@JsonView(View.Detail.class)
	private double consCooling;

	@JsonView(View.Summary.class)
	private DeviceStatus status;

	@JsonView(View.Summary.class)
	private UUID uuid;

	@JsonView(View.Summary.class)
	private UUID consumerUUID;

	@JsonView(View.Detail.class)
	private SimulationFridge simulationFridge;

	private Fridge() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}

	/**
	 * Erstellt einen neuen Kuehlschrank aus uebergebenen Werten
	 * 
	 * @param maxTemp1
	 *            maximale Temperatur, die im Regelbetrieb nicht ueberschritten
	 *            werden soll
	 * @param maxTemp2
	 *            maximale Temperatur, die auch bei Ausnahmen während des
	 *            Betriebs nie ueberschritten werden darf
	 * @param minTemp1
	 *            minimale Temperatur, die im Regelbetrieb nicht unterschritten
	 *            werden soll
	 * @param minTemp2
	 *            minimale Temperatur, die auch bei Ausnahmen wärehnd des
	 *            Betriebs nie unterschritten werden darf
	 * @param fallCooling
	 *            Grad, die der Kuehlschrank pro Minute kuehlen kann
	 * @param riseWarming
	 *            Grad, um die sich der Kuhelschrank pro Minute erwaermt
	 * @param consCooling
	 *            kWh, die der Kuhelschrank pro Minute fuer die Kuhelung
	 *            verbraucht
	 * @param currTemp
	 *            aktuelle Temperatur des Kuehlschranks
	 */
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

	/**
	 * Ueberprueft, ob die gewuentsche Aenderung des Lastprofils moeglich ist
	 * und sendet eine Bestaetigung bzw. Absage an den Consumer
	 * 
	 * @param cr
	 *            Enthaelt Informationen, wie das Lastprofil geaendert werden
	 *            soll
	 */
	public void changeLoadprofile(ChangeRequestSchedule cr) {
		double[] changesKWH = cr.getChangesLoadprofile();
		int[] changesMinute = new int[numSlots];
		double[][] plannedSchedule = scheduleMinutes.clone();
		double[] loadprofile = createValuesLoadprofile(scheduleMinutes[0]);
		int[] plannedMinutesCooling = new int[numSlots];

		// Minute Max(0), Temperatur Max(1)
		double[][] maxSlots = new double[2][numSlots], minSlots = new double[2][numSlots];

		/*
		 * 1 Berechne, wie viele Minuten zusätzlich bzw. weniger gekühlt werden
		 * muss und prüfe, ob genügend Minuten vorhanden sind, in welchen
		 * Änderung möglich ist. Wenn nicht, passe die Höhe der Änderungen an.
		 */
		System.out.println("Prüfe, ob genügend Minuten vorhanden sind");
		for (int i = 0; i < numSlots; i++) {
			// Berechne die Anzahl von Minuten, die pro Slot aktuell gekühlt
			// wird
			plannedMinutesCooling[i] = (int) Math.ceil(-loadprofile[i] / consCooling);

			// Berechne die Anzahl an Minuten, die zusätzlich bzw. weniger
			// gekühlt werden soll
			changesMinute[i] = (int) Math.ceil(changesKWH[i] / consCooling);

			// Prüfe dass Anzahl der Minuten, in welchen Änderung möglich ist,
			// groß genug ist
			// Falls nicht, setze Änderung auf maximale Anzahl von Minuten, in
			// den Änderung
			// möglich ist
			if (changesMinute[i] > 0) {
				// Weniger Kühlen
				int minutesCooling = plannedMinutesCooling[i];
				if (Math.abs(changesMinute[i]) > minutesCooling) {
					changesMinute[i] = minutesCooling;
				}
			} else {
				int minutesWarming = 15 - plannedMinutesCooling[i];
				if (Math.abs(changesMinute[i]) > minutesWarming) {
					changesMinute[i] = -minutesWarming;
				}
			}
			System.out.println("Neue Nachfrage Slot " +i+ ": " + changesMinute[i]);
		}

		/*
		 * 2 Prüfe, ob durch die errechneten Änderungen des jeweilig
		 * vorhergehenden Slots das absolute Min/Max des aktuellen Slots unter-
		 * oder überschritten wird. Wenn ja, passe die Höhe der Änderungen an,
		 * sodass keien unter- und über- schreitungen mehr stattfinden.
		 */
		// Berechne für jeden Slot das aktuelle Maximum und Minimum (1) und in
		// welcher Minute es eintritt (0)
		for (int i = 0; i < numSlots; i++) {
			maxSlots[1][i] = plannedSchedule[1][i * 15];
			minSlots[1][i] = maxSlots[1][i];
			maxSlots[0][i] = i * 15;
			minSlots[0][i] = i * 15;
			for (int j = i * 15; j < (i + 1) * 15; j++) {
				if (plannedSchedule[1][j] > maxSlots[1][i]) {
					maxSlots[1][i] = plannedSchedule[1][j];
					maxSlots[0][i] = j;
				}
				if (plannedSchedule[1][j] < minSlots[1][i]) {
					minSlots[1][i] = plannedSchedule[1][j];
					minSlots[0][i] = j;
				}
			}
			System.out.println("Max Slot " + (i + 1) + ": " + maxSlots[1][i] + " in Minute " + maxSlots[0][i]);
			System.out.println("Min Slot " + (i + 1) + ": " + minSlots[1][i] + " in Minute " + minSlots[0][i]);
		}

		// Prüfe, ob die Änderungen der vorhergehenden Slots ein über-/ unter-
		// schreiten der maximalen/ minimalen Temperatur erzwingen. Passe das
		// Minimum und das Maximum mit Beachtung der Änderungen in den
		// vorhergehenden Slots an
		int changesBefore = changesMinute[0];

		for (int i = 1; i < numSlots; i++) {
			if (changesBefore != 0) {
				if (changesBefore < 0) {
					minSlots[1][i] -= changesBefore * (fallCooling - riseWarming);
					if (minSlots[1][i] < minTemp2) {
						System.out.println("Temperatur wurde unterschritten in Slot " + i + " Wert: " + minSlots[1][i]
								+ " Minute: " + minSlots[0][i]);

						// Prüfe, um wie viel temp2 unterschritten wurde
						double tooLow = minTemp2 - minSlots[1][i];

						// Anzahl an Minuten, die weniger gekühlt werden darf
						// (positive Zahl)
						int minutesLessCooling = (int) Math.ceil(tooLow / (-fallCooling + riseWarming));

						// Prüfe, ob in den vorhergehenden Slots zusätzlich zum
						// Plan gekühlt wurde und wenn ja, kühle dann um
						// minutesLessCooling weniger
						System.out.println(minutesLessCooling + " Minuten weniger kühlen.");
						int lastSlot = 1;
						while (i - lastSlot >= 0 && minutesLessCooling > 0) {
							System.out.println("Slot " +(i-lastSlot));
							double currentChange = 0;
							if (changesMinute[i - lastSlot] < 0) {
								System.out.println("Änderung in Slot " +(i-lastSlot));
								if (Math.abs(changesMinute[i - lastSlot]) >= minutesLessCooling) {
									changesMinute[i - lastSlot] += minutesLessCooling;
									currentChange = minutesLessCooling;
									minutesLessCooling = 0;
								} else {
									currentChange = -changesMinute[i - lastSlot];
									minutesLessCooling += changesMinute[i - lastSlot];
									changesMinute[i - lastSlot] = 0;
								}

								// Passe alle Werte nach Slot (i-lastSlot) an
								for (int j = i - lastSlot + 1; j <= i; j++) {
									minSlots[1][j] += currentChange * (riseWarming - fallCooling);
								}
								changesBefore += currentChange;
							}
							System.out.println("Neuer Wert Slot " +(i-lastSlot)+ ": " +changesMinute[i-lastSlot]+ 
									", currentChange: " +currentChange);
							lastSlot++;
						}
						if (minutesLessCooling > 0) {
							return;
						}
					}
				} else {
					maxSlots[1][i] += changesBefore * (riseWarming - fallCooling);
					if (maxSlots[1][i] > maxTemp2) {
						System.out.println("Temperatur wurde überschritten in Slot " + i + " Wert: " + maxSlots[1][i]
								+ " Minute: " + maxSlots[0][i]);

						// Prüfe, um wie viel temp2 überschritten wurde
						double tooHigh = maxTemp2 - maxSlots[1][i];
						System.out.println("Um " + tooHigh + "°C");

						// Anzahl an Minuten, die weniger gekühlt werden darf
						// (positive Zahl)
						int minutesMoreCooling = (int) Math.ceil(tooHigh / (-riseWarming + fallCooling));
						System.out.println("Es muss " + minutesMoreCooling + " Minuten mehr gekühlt werden.");

						// Prüfe, ob in den vorhergehenden Slots zusätzlich zum
						// Plan ge-
						// kühlt wurde und wenn ja, kühle dann um
						// minutesLessCooling weniger
						int lastSlot = 1;
						while (i - lastSlot >= 0 && minutesMoreCooling > 0) {
							System.out
									.println("Alter Wert Slot " + (i - lastSlot) + ": " + changesMinute[i - lastSlot]);
							double currentChange;
							if (changesMinute[i - lastSlot] > 0) {
								if (changesMinute[i - lastSlot] >= minutesMoreCooling) {
									changesMinute[i - lastSlot] = changesMinute[i - lastSlot] - minutesMoreCooling;
									currentChange = -minutesMoreCooling;
									minutesMoreCooling = 0;
								} else {
									currentChange = -changesMinute[i - lastSlot];
									minutesMoreCooling -= changesMinute[i - 1];
									changesMinute[i - lastSlot] = 0;
								}
								System.out.println("CurrentChange: " + currentChange);
								System.out.println(
										"Neuer Wert Slot " + (i - lastSlot) + ": " + changesMinute[i - lastSlot]);

								// Passe alle Werte nach Slot (i-lastSlot) an
								for (int j = i - lastSlot + 1; j <= i; j++) {
									maxSlots[1][j] += currentChange * (fallCooling - riseWarming);
									System.out.println("Neues Maximum Slot " + j + ": " + maxSlots[1][j]);
								}
								changesBefore += currentChange;

							}
							lastSlot++;
						}
						if (minutesMoreCooling > 0) {
							System.out.println("Fehler schon zuvor: Änderung gar nicht möglich");
							return;
						}
					}
				}
			}
			changesBefore += changesMinute[i];
		}

		/*
		 * 3 Prüfe, ob alle Änderungen vor dem Extrema im jeweiligen Slot
		 * möglich sind. Wenn nein, prüfe, ob für die restlichen Änderungen nach
		 * dem Extrema genügend Zeit und Kapazität verfügbar ist. Wenn nein,
		 * passe die Höhe der Änderungen an.
		 */
		// Wie viele Changes (1) darf ich erst ab Minute (0) machen?
		int[][] minutePossibleChange = new int[2][numSlots];
		int change;

		double[][] newSchedule = new double[2][numSlots * 15];

		for (int i = 0; i < numSlots; i++) {
			change = changesMinute[i];
			minutePossibleChange[0][i] = (int) minSlots[0][i];
			System.out.println("minutePossibleChange: " +minutePossibleChange[0][i]);
			if (change != 0) {
				if (change < 0) {
					minSlots[1][i] += change * (fallCooling - riseWarming);
					System.out.println("Neues Min Slot " + i);

					System.out.println("Neues Minimum: " + minSlots[1][i]);

					if (minSlots[1][i] < minTemp2) {
						System.out.println("Es sind nicht alle Änderungen vor dem Minimumm in Minute " + minSlots[0][i]
								+ " möglich.");
						double currentMin = minSlots[1][i];
						int currentMinute = (int) minSlots[0][i];

						// Berechne, wie viele Änderungsmöglichkeiten vor und
						// nach dem Extremum vorliegen
						int amountBefore = 0;
						int amountAfter = 0;
						for (int j = 0; j < numSlots * 15; j++) {
							if (scheduleMinutes[0][j] == 0) {
								if (j < currentMinute) {
									amountBefore++;
								} else {
									amountAfter++;
								}
							}
						}

						// Berechne, wie viele Changes vor dem Eintritt des
						// Minimums möglich sind
						double amountPossibleBefore = Math.ceil((currentMin - minTemp2) / (fallCooling - riseWarming));
						if (amountPossibleBefore > amountBefore) {
							amountPossibleBefore = amountBefore;
						}

						// Berechne, wie viele Changes nun noch nach Eintritt
						// des
						// Minimums nötig sind
						double amountRest = change - amountPossibleBefore;
						if (amountRest > amountAfter) {
							System.out.println("Übergebene Änderungen funktionieren wegen Zeit nicht.");
							amountRest = amountAfter;
							double possibleChange = amountPossibleBefore + amountAfter;
							double less = change - possibleChange;

							// Passe alle nachfolgenden Minima an
							for (int j = i + 1; j < numSlots; j++) {
								minSlots[1][j] = less * (riseWarming - fallCooling);
							}

							changesMinute[i] = (int) possibleChange;
						}

						System.out.println("Änderungen davor: " + amountBefore);
						System.out.println("Änderungen danach: " + amountRest);

						minutePossibleChange[0][i] = (int) minSlots[0][i] +1;
						minutePossibleChange[1][i] = (int) amountRest;
					}

					else {
						minutePossibleChange[0][i] = i * 15;
						minutePossibleChange[1][i] = change;
					}

				} else {
					System.out.println("------------------FALSCH-------------------");
					maxSlots[1][i] -= change * riseWarming;
					if (maxSlots[1][i] > maxTemp2) {
						System.out.println("Änderungen vor Extremum nicht möglich");
						minutePossibleChange[0][i] = (int) maxSlots[0][i] + 1;

						// Berechne, wie viele Changes vor dem Eintritt des
						// Minimums möglich sind
						double currentMax = minSlots[1][i];
						int amountChanges = 0;
						while (currentMax <= maxTemp2 && change < 0) {
							currentMax += riseWarming;
							change++;
							amountChanges++;
						}
						minutePossibleChange[1][i] = change - amountChanges;

						System.out.println("Summe: " + (-minutePossibleChange[1][i] + minutePossibleChange[0][i]));
						if (-minutePossibleChange[1][i] + minutePossibleChange[0][i] > 59) {
							System.out.println("Übergebene Änderungen funktionieren wegen Zeit nicht.");

							// Berechne Anzahl an möglichen Änderungen und
							// aktualisiere changesMinute
							int possibleChange = 59 - minutePossibleChange[0][i];
							changesMinute[i] = -possibleChange;

							// Berechne differenz zu ursprünglicher Planung
							int less = Math.abs(possibleChange + minutePossibleChange[1][i]);

							System.out.println("Neue Änderung: " + possibleChange);

							// Passe alle folgenden Mins an
							for (int j = i + 1; j < numSlots; j++) {
								minSlots[1][j] += less * (riseWarming - fallCooling);
							}
							return;
						}
					}
				}
			}
			
			for (int f = 0; f < numSlots; f++) {
				System.out.println("Neue Änderungen Slot " + f + ": " + changesMinute[f] + "; " + minutePossibleChange[1][f]
						+ " Änderungen ab Minute " + minutePossibleChange[0][f]);
			}

			double[][] changed = chargeChangedSchedule(changesMinute, minutePossibleChange, i, false);

			// Prüfe, dass Werte auch nach Minimum zu keiner Zeit
			// unterschritten werden
			boolean changesNecessary = false;
			
			// TODO Prüfe, ob Unterschreitung der folgenden Werte durch einmalige Anpassung verhindert werden kann
			double[][] valuesToTest = changed;
			double adaptChange = 0;
			int[] secondChange = {0, 0, 0, 0};
			int[][] secondMinutePossibleChange = minutePossibleChange.clone();
			System.out.println("Slot " +i+ " Minute Possible Change: " +minutePossibleChange[0][i]);
			for (int n = minutePossibleChange[0][i] - i*15; n < 15; n++) {
				if (valuesToTest[1][n]+adaptChange < minTemp2) {
					System.out.println("Wert unter minTemp2 in Minute " +n+ " : " +valuesToTest[1][n]+adaptChange);
					changesNecessary = true;
					changesMinute[i] ++; 
					secondChange[i] ++;
					adaptChange += riseWarming - fallCooling;
					System.out.println("Adapt Change: " +adaptChange);
				}
				else if (valuesToTest[1][n]+adaptChange > maxTemp2) {
					System.out.println("Wert über maxTemp2 in Minute " +n+ " : " +valuesToTest[1][n]+adaptChange);
					changesNecessary = true;
					changesMinute[i] --;
					secondChange[i] ++;
					adaptChange += fallCooling - riseWarming;
					System.out.println("Adapt Change: " +adaptChange);
				}
			}
			
			if (changesNecessary) {
				for (int f = 0; f < numSlots; f++) {
					System.out.println("Restliche Änderungen Slot " + f + ": " + changesMinute[f] + "; " +secondChange[i]
							+ " Änderungen ab Minute " + minutePossibleChange[0][f]);
				}
				secondMinutePossibleChange[1][i] = secondChange[i];
				changed = chargeChangedSchedule(secondChange, minutePossibleChange, i, true);
			}
			
			int start = i * 15;
			for (int k = 0; k < 15; k++) {
				newSchedule[0][start] = changed[0][k];
				newSchedule[1][start] = changed[1][k];
			}
				
		}
		
		// double[][] newSchedule = chargeChangedSchedule(changesMinute,
		// minutePossibleChange);

		/*
		 * TODO ChangeRequestAnswer if (newSchedule != null) {
		 * confirmChangedLoadprofile(cr); } else {
		 * declineChangedLoadprofile(cr); }
		 */
	}

	/**
	 * Berechnet einen veränderten Fahrplan mit Beachtung der übergebenen
	 * Änderungen und der Information, wie viele Änderungen erst ab einer
	 * bestimmten Minute erfolgen dürfen
	 * 
	 * @param changesLoadprofile
	 *            Array mit Änderungen, mit der Anzahl an Minuten pro Slot, die
	 *            geändert werden müssen. Sind die Werte positiv, so muss für
	 *            diese Anzahl von Minuten zusätzlich gekühlt werden, sind sie
	 *            negativ muss für diese Anzahl von Minuten weniger gekühlt
	 *            werden.
	 * @param minutePossibleChanges
	 *            Gibt an, wie viele Changes (1) darf ich erst ab einer
	 *            bestimmten Minute (0) gemacht werden dürfen.
	 * @return Array
	 */
	private double[][] chargeChangedSchedule(int[] changesLoadprofile, int[][] minutePossibleChanges,
			int slotToChange, boolean secondCharge) {
		// System.out.println("Slot: " +slotToChange);
		double[][] newSchedule = new double[2][15];
		for (int i = 0; i < 15; i++) {
			if (!secondCharge) {
				newSchedule[0][i] = scheduleMinutes[0][slotToChange * 15 + i];
				newSchedule[1][i] = scheduleMinutes[1][slotToChange * 15 + i];
			}
			else {
				newSchedule[0][i] = scheduleCurrentChangeRequest[0][slotToChange * 15 + i];
				newSchedule[1][i] = scheduleCurrentChangeRequest[1][slotToChange * 15 + i];
			}
		}

		double sumChangesDone = 0;
		for (int i = 0; i < numSlots; i++) {
			sumChangesDone += changesLoadprofile[i];
		}

		int currentChange;
		boolean changed = sumChangesDone != 0;
		// System.out.println("Changed: " +changed);

		int slot = slotToChange;

		// Berechne für Slot neuen Plan
		currentChange = changesLoadprofile[slot];
		double searchFor, change;
		if (currentChange < 0) {
			searchFor = 0.0;
			change = consCooling;
		} else {
			searchFor = consCooling;
			change = 0.0;
		}

		int minuteLimit = minutePossibleChanges[0][slot] / 15;
		// System.out.println("Minute Limit: " +minuteLimit);

		// Anzahl an Änderungen, die vor minuteExtreme erfolgen müssen
		int amountBefore = Math.abs(currentChange - minutePossibleChanges[1][slot]);
		// int currentMinute = slot * 15;
		int currentMinute = 0;

		// Anzahl an Änderungen, die nach minuteExtreme erfolgen müssen
		int amountAfter = Math.abs(minutePossibleChanges[1][slot]);
		
		boolean beforeLimit = currentMinute < minuteLimit;

		// Mache alle Änderungen
		while (currentMinute < 15) {
			// Prüfe, ob man sich vor oder nach Limit befindet
			if (beforeLimit) {
				// Passe so bald wie möglich Plan an und berechne pro Änderung
				// amountBefore--
				if (newSchedule[0][currentMinute] == searchFor && amountBefore != 0) {
					newSchedule[0][currentMinute] = change;
					amountBefore--;
					changed = true;
				}
			}
			else {
				// Passe so bald wie möglich Plan an und berechne pro Änderung
				// amountAfter--
				if (newSchedule[0][currentMinute] == searchFor && amountAfter != 0) {
					newSchedule[0][currentMinute] = change;
					amountAfter--;
					changed = true;
				}
			}
			
			if (changed) {
				boolean currentWarming = newSchedule[0][currentMinute] == 0;
				double localChange, other;
				if (currentWarming) {
					localChange = riseWarming;
					other = fallCooling;
				} else {
					localChange = fallCooling;
					other = riseWarming;
				}

				if (currentMinute == 0 && slot == 0) {
					newSchedule[1][currentMinute] = newSchedule[1][currentMinute] - other + localChange;
					// System.out.println("Minute 0, Slot 0");
				} else if (currentMinute == 0 && slot != 0) {
					newSchedule[1][currentMinute] = scheduleCurrentChangeRequest[1][slot * 15 - 1] + localChange;
					// System.out.println("Minute 0, hole Temperatur von Minute
					// " +(slot*15-1)+ " : " +newSchedule[1][currentMinute]);
				} else {
					newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + localChange;
					// System.out.println("Minute nicht 0: " +currentMinute);
				}
			}

			currentMinute++;
			if (currentMinute == minuteLimit && amountBefore != 0
					|| currentMinute == slot * 15 && amountAfter != 0) {
				System.out.println("NULL :(");
				return null;
			}
			if (currentMinute == minuteLimit) {
				beforeLimit = false;
			}
		}


		for (int i = 0; i < 15; i++) {
			scheduleCurrentChangeRequest[0][slot * 15 + i] = Math.round(100.00 * newSchedule[0][i]) /100.00;
			scheduleCurrentChangeRequest[1][slot * 15 + i] = Math.round(100.00 * newSchedule[1][i]) / 100.00;
			System.out.println("SCCR Minute " + (slot * 15 + i) + " Temperatur " + scheduleCurrentChangeRequest[1][slot * 15 + i] + " Verbrauch: "
					+ scheduleCurrentChangeRequest[0][slot * 15 + i]);
		}

		return newSchedule;

	}

	/**
	 * Berechnet einen veränderten Fahrplan mit Beachtung der übergebenen
	 * Änderungen und der Information, wie viele Änderungen erst ab einer
	 * bestimmten Minute erfolgen dürfen
	 * 
	 * @param changesLoadprofile
	 *            Array mit Änderungen, mit der Anzahl an Minuten pro Slot, die
	 *            geändert werden müssen. Sind die Werte positiv, so muss für
	 *            diese Anzahl von Minuten zusätzlich gekühlt werden, sind sie
	 *            negativ muss für diese Anzahl von Minuten weniger gekühlt
	 *            werden.
	 * @param minutePossibleChanges
	 *            Gibt an, wie viele Changes (1) darf ich erst ab einer
	 *            bestimmten Minute (0) gemacht werden dürfen.
	 * @return Array
	 */
	private double[][] chargeChangedSchedule(int[] changesLoadprofile, int[][] minutePossibleChanges) {
		double[][] newSchedule = scheduleMinutes.clone();

		int currentChange;
		boolean changed = false;

		// Berechne Slotsweise neuen Plan
		for (int slot = 0; slot < numSlots; slot++) {
			currentChange = changesLoadprofile[slot];
			double searchFor, change;
			if (currentChange < 0) {
				searchFor = 0.0;
				change = consCooling;
			} else {
				searchFor = consCooling;
				change = 0.0;
			}

			int minuteLimit = minutePossibleChanges[0][slot];

			// Anzahl an Änderungen, die vor minuteExtreme erfolgen müssen
			int amountBefore = Math.abs(currentChange - minutePossibleChanges[1][slot]);
			int currentMinute = slot * 15;

			// Anzahl an Änderungen, die nach minuteExtreme erfolgen müssen
			int amountAfter = Math.abs(minutePossibleChanges[1][slot]);

			// Mache die mögliche Anzahl an Änderungen vor dem Extremum
			while (currentMinute < minuteLimit) {
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
				if (changed && currentMinute > 0) {
					if (newSchedule[0][currentMinute] == 0) {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + riseWarming;
					} else {
						newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + fallCooling;
					}
				}

				currentMinute++;

				if (currentMinute == slot * 15 && amountBefore != 0) {
					return null;
				}
			}

			// Mache die restliche Anzahl an Änderungen nach dem Extremum
			while (currentMinute < (slot + 1) * 15) {
				// Passe so bald wie möglich Plan an und berechne pro Änderung
				// amountAfter--
				if (newSchedule[0][currentMinute] == searchFor && amountAfter != 0) {
					newSchedule[0][currentMinute] = change;
					amountAfter--;
					changed = true;
				}
				if (changed) {
					if (newSchedule[0][currentMinute] == 0) {
						if (currentMinute == 0) {
							newSchedule[1][currentMinute] = newSchedule[1][currentMinute] - fallCooling + riseWarming;
						} else {
							newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + riseWarming;
						}
					} else {
						if (currentMinute == 0) {
							newSchedule[1][currentMinute] = newSchedule[1][currentMinute] + fallCooling - riseWarming;
						} else {
							newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + fallCooling;
						}
					}
				}

				currentMinute++;

				if (currentMinute == slot * 15 && amountAfter != 0) {
					return null;
				}
			}
		}
		roundSchedule(newSchedule);
		for (int i = 0; i < numSlots * 15; i++) {
			System.out
					.println("Minute: " + i + " Temperatur: " + newSchedule[1][i] + " Verbrauch: " + newSchedule[0][i]);
		}

		scheduleMinutes = newSchedule;
		return newSchedule;
	}

	/**
	 * Berechnet den neuen Fahrplan bei Temperaturabweichungen
	 * 
	 * @param aenderung
	 *            Zeitpunkt, bei dem die Temperaturabweichung eingetreten ist
	 * @param newTemperature
	 *            Temperatur, die gemessen wurde
	 * @param firstSchedule
	 *            Information, ob das der erste Fahrplan ist, den die
	 *            Temperaturabweichung beeinflusst
	 * @return Array mit Verbrauch[0] und Temperatur[1] des neuen Fahrplans
	 */
	private double[][] chargeDeltaSchedule(GregorianCalendar aenderung, double newTemperature, boolean firstSchedule) {
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

		return roundSchedule(deltaSchedule);
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
			scheduleMinutes[0][startMinute] = consCooling;
		} else {
			scheduleMinutes[0][startMinute] = 0.0;
		}
		System.out.println("Minute: " + startMinute + " Temperatur: " + scheduleMinutes[1][startMinute] + " Verbrauch: "
				+ scheduleMinutes[0][startMinute]);

		/*
		 * Berechne alle weiteren Verbrauchs- und Temperatur- werte ab dem
		 * Zeitpuntk timeFixed + 1 Minute
		 */
		for (int i = startMinute + 1; i < numSlots * 15; i++) {
			if (currCooling) {
				scheduleMinutes[0][i] = consCooling;
				scheduleMinutes[1][i] = Math.round(100.00 * (fallCooling + scheduleMinutes[1][i - 1])) / 100.00;
				if (scheduleMinutes[1][i] <= minTemp1) {
					currCooling = false;
				}
			} else {
				scheduleMinutes[0][i] = 0.0;
				scheduleMinutes[1][i] = Math.round(100.00 * (riseWarming + scheduleMinutes[1][i - 1])) / 100.00;
				if (scheduleMinutes[1][i] >= maxTemp1) {
					currCooling = true;
				}
			}
			System.out.println(
					"Minute: " + i + " Temperatur: " + scheduleMinutes[1][i] + " Verbrauch: " + scheduleMinutes[0][i]);
		}

		/*
		 * Prüfe, ob zu Beginn des nächsten Plans gekühlt werden soll und mit
		 * welcher Temperatur dann gestartet wird
		 */
		if (currCooling) {
			currTemp = scheduleMinutes[1][numSlots * 15 - 1] + fallCooling;
		} else {
			currTemp = scheduleMinutes[1][numSlots * 15 - 1] + riseWarming;
		}

		scheduleMinutes = roundSchedule(scheduleMinutes);
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
				valuesLoadprofile[n] = -summeMin;
				n++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	private void confirmChangedLoadprofile(ChangeRequestSchedule cr) {
		double[] changes = cr.getChangesLoadprofile();
		double sum = 0;

		for (int i = 0; i < numSlots; i++) {
			sum += changes[i];
		}

		double costs;
		costs = sum * consCooling;
		if (costs < 0) {
			costs = 0;
		}
		// TODO Sende Bestätigung, dass in ChangeRequest gesendete Änderung
		// möglich ist
		// Teile Kosten für diese Änderung mit. Der Consumer muss dann das
		// Angebot so ändern,
		// dass die zusätzlichen Kosten ausgeglichen werden
	}

	public void confirmLoadprofile(GregorianCalendar time) {
		if (DateTime.ToString(timeFixed).equals(DateTime.ToString(time))) {
			saveSchedule(scheduleMinutes, timeFixed);
			// TODO speichere Lastprofil?
			sendNewLoadprofile();
		}
	}

	private void declineChangedLoadprofile(ChangeRequestSchedule cr) {
		// TODO Sende Absage, dass in ChangeRequest gesendete Änderung nicht
		// möglich ist
	}

	/**
	 * Liefert die bereits festen Fahrplaene des Kuehlschranks
	 * 
	 * @return Alle festen Fahrplaene als TreeMap, mit dem
	 */
	public TreeMap<String, double[]> getSchedulesFixed() {
		return schedulesFixed;
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
	 * Liefert die bereits festen Lastprofile des Kuhelschranks
	 * 
	 * @return TreeMap mit allen festen Lastprofilen
	 */
	public TreeMap<String, double[]> getLoadprofilesFixed() {
		return loadprofilesFixed;
	}

	/**
	 * Liefert den Fahrplan, der zur gewuenschten Zeit startet
	 * 
	 * @param start
	 *            Zeit, zu der der Fahrplan starten soll
	 * @return Fahrplan, der zur gewuenschten Zeit startet
	 */
	public double[][] getSchedule(GregorianCalendar start) {
		double[][] schedule = new double[2][15 * numSlots];
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

	/**
	 * Liefert die uuid des Kuehlschranks
	 * 
	 * @return Uuid des Kuehlschranks
	 */
	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub
	}

	private double[][] roundSchedule(double[][] schedule) {
		/*
		 * Werte runden
		 */
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 60; j++) {
				schedule[i][j] = Math.round(schedule[i][j] * 100.00) / 100.00;
			}
		}
		return schedule;
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
		int size = 15 * numSlots;

		for (int i = 0; i < size; i++) {
			double[] values = { schedule[0][i], schedule[1][i] };
			schedulesFixed.put(DateTime.ToString(start), values);
			simulationFridge.addNewValues(DateTime.ToString(start), values);
			start.add(Calendar.MINUTE, 1);
		}
		start.add(Calendar.HOUR_OF_DAY, -1);
	}

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

		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, 0.0, false);
		// sendLoadprofileToConsumer(loadprofile);
	}

	/**
	 * Legt den Consumer fuer den Kuehlschrank fest
	 * 
	 * @param uuid
	 *            Uuid des Consumers
	 */
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

	/**
	 * Erzeugt ein Deltalastprofil und sendet es an den Consumer. Methode wird
	 * aufgerufen, wenn eine andere Temperatur gemessen wurde, als in der Minute
	 * geplant war.
	 * 
	 * @param aenderung
	 *            Zeitpunkt, wann die Temperaturabweichung gemessen wurde
	 * @param newTemperature
	 *            Temperatur, die gemessen wurde
	 */
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
				Log.e(this.uuid, DateTime.ToString(startLoadprofile) + " - " + loadprofilesFixed.keySet());
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
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, startLoadprofile, 0.0, true);
				sendLoadprofileToConsumer(deltaLoadprofile);

				// Abspeichern des neuen Lastprofils
				loadprofilesFixed.put(DateTime.ToString(startLoadprofile), newValues);
			} else {
				// keine änderung
			}
			startLoadprofile.add(Calendar.HOUR_OF_DAY, 1);
			aenderung.add(Calendar.HOUR_OF_DAY, 1);
		}
	}

	// Prueft, ob der uebergebene Schedule maxTemp2 oder minTemp2
	// ueber-/unterschreitet
	// und gibt die Minute zurueck, in welcher die Temperatur unterschritten
	// wird
	private int testDeltaSchedule(double[][] schedule, double newTemperature, int minuteChange, boolean firstSchedule) {
		if (!firstSchedule) {
			if (schedule[0][minuteChange] == consCooling) {
				schedule[1][minuteChange] = newTemperature + fallCooling;
			} else {
				schedule[1][minuteChange] = newTemperature + riseWarming;
			}
		} else {
			schedule[1][minuteChange] = newTemperature;
		}

		for (int i = minuteChange + 1; i < schedule[1].length; i++) {
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
}