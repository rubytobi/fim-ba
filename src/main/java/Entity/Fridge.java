package Entity;

import java.util.*;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonView;
import Event.IllegalDeviceState;
import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequestSchedule;
import Util.API;
import Util.DateTime;
import Util.Log;
import Util.SimulationFridge;
import Util.View;

/**
 * Klasse fuer Kuehlschraenke
 */
public class Fridge implements Device {
	/**
	 * Fahrplan, den der Consumer gerade aushandelt mit Verbrauch (0) und
	 * Temperatur (1)
	 */
	@JsonView(View.Detail.class)
	private double[][] scheduleMinutes = new double[2][15 * numSlots];

	/**
	 * Zeitpunkt, ab dem scheduleMinutes gilt
	 */
	@JsonView(View.Summary.class)
	private String timeFixed;

	/**
	 * Fahrpläne, die schon ausgehandelt sind und fest stehen
	 */
	@JsonView(View.Detail.class)
	private TreeMap<String, double[]> schedulesFixed = new TreeMap<String, double[]>();

	/**
	 * Fahrplan, der für die aktuelle ChangeRequest errechnet wurde
	 */
	private double[][] scheduleCurrentChangeRequest = new double[2][numSlots * 15];

	/**
	 * Lastprofile, die schon ausgehandelt sind und fest stehen
	 */
	@JsonView(View.Detail.class)
	private TreeMap<String, double[]> loadprofilesFixed = new TreeMap<String, double[]>();

	/**
	 * Temperatur, bei der der nächste neue Fahrplan beginnen soll
	 */
	@JsonView(View.Summary.class)
	private double currTemp;

	/**
	 * Grenzen des Kühlschranks
	 */
	@JsonView(View.Detail.class)
	private double maxTemp1, minTemp1, maxTemp2, minTemp2;

	/**
	 * Gibt an, ob der nächste Fahrplan mit Kühlen beginnen soll
	 */
	@JsonView(View.Detail.class)
	private boolean currCooling;

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

	/**
	 * Wie viel Grad pro Minute kühlt der Kühlschrank?
	 */
	@JsonView(View.Detail.class)
	private double fallCooling;

	/**
	 * Gibt an, wie viel der Kühlschrank verlangt, wenn die Temperatur durch
	 * eine Änderung in die "rote Zone" kommt. (Rote Zone: zwischen min1 und
	 * min2 bzw. max1 und max2)
	 */
	@JsonView(View.Detail.class)
	private double penaltyPrice;

	/**
	 * Wie viel Grad pro Minute erwärmt der Kühlschrank?
	 */
	@JsonView(View.Detail.class)
	private double riseWarming;

	/**
	 * Verbrauch zum Kühlen pro Minute in kWh
	 */
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

	private double priceEex;

	private Fridge() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
		priceEex = Marketplace.getEEXPrice();
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
			double riseWarming, double consCooling, double currTemp, double penaltyPrice) {
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
		this.waitForAnswerCR = false;
		this.waitToChargeDeltaLoadprofile = false;
		this.penaltyPrice = penaltyPrice;

		simulationFridge = new SimulationFridge();

		System.out.println("Neuer Fridge: " + uuid);
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
	public AnswerChangeRequestSchedule receiveChangeRequestSchedule(ChangeRequestSchedule cr) {
		Log.d(uuid, "ChangeRequestSchedule erhalten: " + cr);

		if (!cr.getStartLoadprofile().equals(timeFixed)) {
			// ChangeRequest kann nur fuer scheduleMinutes mit Startzeit
			// timeFixed angefragt werden. Sende daher Antwort ohne Änderungen
			Log.e(uuid, "Änderungen sind für diesen Zeitslot nicht möglich.");
			double[] zero = { 0, 0, 0, 0 };
			AnswerChangeRequestSchedule answer = new AnswerChangeRequestSchedule(cr.getUUID(), zero, 0, 0);
			return answer;
		}

		try {

			double[] changesKWH = cr.getChangesLoadprofile();
			int[] changesMinute = new int[numSlots];
			double[][] plannedSchedule = scheduleMinutes.clone();
			double[] loadprofile = createValuesLoadprofile(scheduleMinutes[0]);
			int[] plannedMinutesCooling = new int[numSlots];

			// Minute Max(0), Temperatur Max(1)
			double[][] maxSlots = new double[2][numSlots], minSlots = new double[2][numSlots];

			/*
			 * 1 Berechne, wie viele Minuten zusätzlich bzw. weniger gekühlt
			 * werden muss und prüfe, ob genügend Minuten vorhanden sind, in
			 * welchen Änderung möglich ist. Wenn nicht, passe die Höhe der
			 * Änderungen an.
			 */
			for (int i = 0; i < numSlots; i++) {
				// Berechne die Anzahl von Minuten, die pro Slot aktuell gekühlt
				// wird
				plannedMinutesCooling[i] = (int) Math.ceil(-loadprofile[i] / consCooling);

				// Berechne die Anzahl an Minuten, die zusätzlich bzw. weniger
				// gekühlt werden soll
				changesMinute[i] = (int) Math.ceil(changesKWH[i] / consCooling);

				// Prüfe dass Anzahl der Minuten, in welchen Änderung möglich
				// ist,
				// groß genug ist
				// Falls nicht, setze Änderung auf maximale Anzahl von Minuten,
				// in
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
			}

			/*
			 * 2 Prüfe, ob durch die errechneten Änderungen des jeweilig
			 * vorhergehenden Slots das absolute Min/Max des aktuellen Slots
			 * unter- oder überschritten wird. Wenn ja, passe die Höhe der
			 * Änderungen an, sodass keien unter- und über- schreitungen mehr
			 * stattfinden.
			 */
			// Berechne für jeden Slot das aktuelle Maximum und Minimum (1) und
			// in
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
			}

			// Prüfe, ob die Änderungen der vorhergehenden Slots ein über-/
			// unter-
			// schreiten der maximalen/ minimalen Temperatur erzwingen. Passe
			// das
			// Minimum und das Maximum mit Beachtung der Änderungen in den
			// vorhergehenden Slots an
			int changesBefore = changesMinute[0];

			for (int i = 1; i < numSlots; i++) {
				if (changesBefore != 0) {
					if (changesBefore < 0) {
						minSlots[1][i] -= changesBefore * (fallCooling - riseWarming);
						if (minSlots[1][i] < minTemp2) {
							// Prüfe, um wie viel temp2 unterschritten wurde
							double tooLow = minTemp2 - minSlots[1][i];

							// Anzahl an Minuten, die weniger gekühlt werden
							// darf
							// (positive Zahl)
							int minutesLessCooling = (int) Math.ceil(tooLow / (-fallCooling + riseWarming));

							// Prüfe, ob in den vorhergehenden Slots zusätzlich
							// zum
							// Plan gekühlt wurde und wenn ja, kühle dann um
							// minutesLessCooling weniger
							int lastSlot = 1;
							while (i - lastSlot >= 0 && minutesLessCooling > 0) {
								double currentChange = 0;
								if (changesMinute[i - lastSlot] < 0) {
									if (Math.abs(changesMinute[i - lastSlot]) >= minutesLessCooling) {
										changesMinute[i - lastSlot] += minutesLessCooling;
										currentChange = minutesLessCooling;
										minutesLessCooling = 0;
									} else {
										currentChange = -changesMinute[i - lastSlot];
										minutesLessCooling += changesMinute[i - lastSlot];
										changesMinute[i - lastSlot] = 0;
									}

									// Passe alle Werte nach Slot (i-lastSlot)
									// an
									for (int j = i - lastSlot + 1; j <= i; j++) {
										minSlots[1][j] += currentChange * (riseWarming - fallCooling);
									}
									changesBefore += currentChange;
								}
								lastSlot++;
							}
						}
					} else {
						maxSlots[1][i] += changesBefore * (riseWarming - fallCooling);
						if (maxSlots[1][i] > maxTemp2) {

							// Prüfe, um wie viel temp2 überschritten wurde
							double tooHigh = maxTemp2 - maxSlots[1][i];

							// Anzahl an Minuten, die mehr gekühlt werden muss
							// (positive Zahl)
							int minutesMoreCooling = (int) Math.ceil(tooHigh / (fallCooling - riseWarming));

							// Prüfe, ob in den vorhergehenden Slots zusätzlich
							// zum
							// Plan ge-
							// kühlt wurde und wenn ja, kühle dann um
							// minutesMoreCooling mehr
							int lastSlot = 1;
							while (i - lastSlot >= 0 && minutesMoreCooling > 0) {
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

									// Passe alle Werte nach Slot (i-lastSlot)
									// an
									for (int j = i - lastSlot + 1; j <= i; j++) {
										maxSlots[1][j] -= currentChange * (fallCooling - riseWarming);
									}
									changesBefore += currentChange;

								}
								lastSlot++;
							}
						}
					}
				}
				changesBefore += changesMinute[i];
			}

			/*
			 * 3 Prüfe, ob alle Änderungen vor dem Extrema im jeweiligen Slot
			 * möglich sind. Wenn nein, prüfe, ob für die restlichen Änderungen
			 * nach dem Extrema genügend Zeit und Kapazität verfügbar ist. Wenn
			 * nein, passe die Höhe der Änderungen an.
			 */
			// Wie viele Changes (1) darf ich erst ab Minute (0) machen?
			int[][] minutePossibleChange = new int[2][numSlots];
			int change;

			double[][] newSchedule = new double[2][numSlots * 15];

			for (int slot = 0; slot < numSlots; slot++) {
				change = changesMinute[slot];
				minutePossibleChange[0][slot] = (int) minSlots[0][slot];
				if (change != 0) {
					if (change < 0) {
						minSlots[1][slot] += change * (fallCooling - riseWarming);
						if (minSlots[1][slot] < minTemp2) {
							double currentMin = minSlots[1][slot];
							int currentMinute = (int) minSlots[0][slot];

							// Berechne, wie viele Änderungsmöglichkeiten vor
							// und
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
							double amountPossibleBefore = Math
									.ceil((currentMin - minTemp2) / (fallCooling - riseWarming));
							if (amountPossibleBefore > amountBefore) {
								amountPossibleBefore = amountBefore;
							}

							// Berechne, wie viele Changes nun noch nach
							// Eintritt
							// des
							// Minimums nötig sind
							double amountRest = change - amountPossibleBefore;
							if (amountRest > amountAfter) {
								amountRest = amountAfter;
								double possibleChange = amountPossibleBefore + amountAfter;
								double less = change - possibleChange;

								// Passe alle nachfolgenden Minima an
								for (int j = slot + 1; j < numSlots; j++) {
									minSlots[1][j] = less * (riseWarming - fallCooling);
								}

								changesMinute[slot] = (int) possibleChange;
							}
							minutePossibleChange[0][slot] = (int) minSlots[0][slot] + 1;
							minutePossibleChange[1][slot] = (int) amountRest;
						}

						else {
							minutePossibleChange[0][slot] = slot * 15;
							minutePossibleChange[1][slot] = change;
						}

					} else {
						maxSlots[1][slot] += change * (riseWarming - fallCooling);
						if (maxSlots[1][slot] > maxTemp2) {
							double currentMax = maxSlots[1][slot];
							int currentMinute = (int) maxSlots[0][slot];

							// Berechne, wie viele Änderungsmöglichkeiten vor
							// und
							// nach dem Extremum vorliegen
							int amountBefore = 0;
							int amountAfter = 0;
							for (int j = slot * 15; j < (slot + 1) * 15; j++) {
								if (scheduleMinutes[0][j] == consCooling) {
									if (j < currentMinute) {
										amountBefore++;
									} else {
										amountAfter++;
									}
								}
							}

							// Berechne, wie viele der Changes vor dem
							// Eintritt des Maximums möglich sind
							double amountPossibleBefore = Math
									.ceil((currentMax - maxTemp2) / (riseWarming - fallCooling));
							if (amountPossibleBefore > amountBefore) {
								amountPossibleBefore = amountBefore;
							}

							// Berechne, wie viele Changes nun noch nach
							// Eintritt
							// des Maximums nötig sind
							double amountRest = change - amountPossibleBefore;
							if (amountRest > amountAfter) {
								amountRest = amountAfter;
								double possibleChange = amountPossibleBefore + amountAfter;
								double more = change - possibleChange;

								// Passe alle nachfolgenden Maxima an
								for (int j = slot + 1; j < numSlots; j++) {
									maxSlots[1][j] = more * (fallCooling - riseWarming);
								}

								changesMinute[slot] = (int) possibleChange;
							}

							minutePossibleChange[0][slot] = (int) maxSlots[0][slot] + 1;
							minutePossibleChange[1][slot] = (int) amountRest;
						}

						else {
							minutePossibleChange[0][slot] = slot * 15;
							minutePossibleChange[1][slot] = change;
						}
					}
				}

				double[][] changed = chargeChangedSchedule(changesMinute, minutePossibleChange, slot, false);

				if (changed == null) {
					// Gib Antwort ohne jegliche Änderungen zurück, wenn change
					// == null
					double[] newChangesKWH = { 0, 0, 0, 0 };
					double factorForPrice = 0;
					double sumPenalty = 0;
					AnswerChangeRequestSchedule answer = new AnswerChangeRequestSchedule(cr.getUUID(), newChangesKWH,
							factorForPrice, sumPenalty);
					return answer;
				}

				// Prüfe, dass Werte auch nach Minimum/ Maximum zu keiner Zeit
				// unter-/ überschritten werden
				boolean changesNecessary = false;

				// Prüfe, ob Werte nach Minimum/ Maximum noch unter-/
				// überschritten
				// werden
				// Wenn ja, berechne Höhe der Überschreitung und notwendige
				// Anpassung und berechne changedSchedule nocheinmal
				double[][] valuesToTest = changed;
				double adaptChange = 0;
				int[] secondChange = { 0, 0, 0, 0 };
				int[][] secondMinutePossibleChange = minutePossibleChange.clone();
				for (int n = minutePossibleChange[0][slot] - slot * 15; n < 15; n++) {
					if (valuesToTest[1][n] + adaptChange < minTemp2) {
						changesNecessary = true;
						changesMinute[slot]++;
						secondChange[slot]++;
						adaptChange += riseWarming - fallCooling;
					} else if (valuesToTest[1][n] + adaptChange > maxTemp2) {
						changesNecessary = true;
						changesMinute[slot]--;
						secondChange[slot]--;
						adaptChange += fallCooling - riseWarming;
					}
				}

				if (changesNecessary) {
					secondMinutePossibleChange[1][slot] = secondChange[slot];
					changed = chargeChangedSchedule(secondChange, minutePossibleChange, slot, true);
				}
				if (changed == null) {
					return null;
				}

				int start = slot * 15;
				for (int k = 0; k < 15; k++) {
					newSchedule[0][start] = changed[0][k];
					newSchedule[1][start] = changed[1][k];
				}

			}

			// Berechne, in wie vielen Minuten der Plan vor und nach den
			// Änderungen
			// in dem Bereich zwischen maxTemp1 und maxTemp2 oder zwischen
			// minTemp2
			// und minTemp1 war.
			// War die Temperatur in einer Minute bei maxTemp2 bzw. minTemp2, so
			// wird die volle Minute berechnet, war die Temperatur bei maxTemp1
			// bzw.
			// minTemp2, so wird die Minute gar nicht berechnet. In dem Bereich
			// dazwischen wird die Minute anteilig berechnet, je nachdem wie
			// hoch
			// die Temperatur genau war.
			double before = 0;
			double after = 0;
			double spanTooHigh = maxTemp2 - maxTemp1;
			double spanTooLow = minTemp1 - minTemp2;
			for (int i = 0; i < numSlots * 15; i++) {
				double currentTempBefore = scheduleMinutes[1][i];
				double currentTempAfter = scheduleCurrentChangeRequest[1][i];
				if (currentTempBefore < minTemp1) {
					currentTempBefore -= minTemp2;
					before += 1 - currentTempBefore / spanTooLow;
				} else if (currentTempBefore > maxTemp1) {
					currentTempBefore -= maxTemp1;
					before += currentTempBefore / spanTooHigh;
				}
				if (currentTempAfter < minTemp1) {
					currentTempAfter -= minTemp2;
					after += 1 - currentTempAfter / spanTooLow;
				} else if (currentTempAfter > maxTemp1) {
					currentTempAfter -= maxTemp1;
					after += currentTempAfter / spanTooHigh;
				}
			}

			// Berechne, um wie viele (anteilige) Minuten der Bereich zwischen
			// minTemp1 und maxTemp1 durch die Änderungen mehr verlassen wurde
			double worsening;
			if (after > before) {
				worsening = before - after;
			} else {
				worsening = 0;
			}

			// Berechne den Faktor für den Preis.
			// Hierbei wird berechnet, wie viel der Kühlschrank für die Anzahl
			// an
			// worsening Minuten beim Kühlen verbrauchen würde
			double sumPenalty = Math.abs(worsening * penaltyPrice);

			// Berechnet wie viele kWh nun pro Viertelstunde mehr oder weniger
			// verbraucht wird und summiert die Werte auf.
			double sumKWHAdditionalCooling = 0;
			double[] newChangesKWH = new double[numSlots];
			for (int i = 0; i < numSlots; i++) {
				newChangesKWH[i] = changesMinute[i] * consCooling;
				sumKWHAdditionalCooling += newChangesKWH[i];
			}

			// Jede kWh, die zusätzlich gekühlt werden muss, wird zum Faktor für
			// den
			// Preis hinzugerechnet
			double factorForPrice = 0;
			if (sumKWHAdditionalCooling > 0) {
				factorForPrice += sumKWHAdditionalCooling;
			}

			// Gibt mögliche Änderungen und den Faktor für deren Preis zurück
			AnswerChangeRequestSchedule answer = new AnswerChangeRequestSchedule(cr.getUUID(), newChangesKWH,
					factorForPrice, sumPenalty);
			return answer;
		} catch (IllegalArgumentException e) {
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
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
	private double[][] chargeChangedSchedule(int[] changesLoadprofile, int[][] minutePossibleChanges, int slotToChange,
			boolean secondCharge) {
		double[][] newSchedule = new double[2][15];
		for (int i = 0; i < 15; i++) {
			if (!secondCharge) {
				newSchedule[0][i] = scheduleMinutes[0][slotToChange * 15 + i];
				newSchedule[1][i] = scheduleMinutes[1][slotToChange * 15 + i];
			} else {
				newSchedule[0][i] = scheduleCurrentChangeRequest[0][slotToChange * 15 + i];
				newSchedule[1][i] = scheduleCurrentChangeRequest[1][slotToChange * 15 + i];
			}
		}

		double sumChangesDone = 0;
		for (int i = 0; i < slotToChange; i++) {
			sumChangesDone += changesLoadprofile[i];
		}

		int currentChange;
		boolean changed = sumChangesDone != 0;

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

		// Anzahl an Änderungen, die vor minuteExtreme erfolgen müssen
		int amountBefore = Math.abs(currentChange - minutePossibleChanges[1][slot]);
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
			} else {
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
				} else if (currentMinute == 0 && slot != 0) {
					newSchedule[1][currentMinute] = newSchedule[1][currentMinute] + localChange;

				} else {
					newSchedule[1][currentMinute] = newSchedule[1][currentMinute - 1] + localChange;
				}
			}

			currentMinute++;
			if (currentMinute == minuteLimit && amountBefore != 0 || currentMinute == slot * 15 && amountAfter != 0) {
				return null;
			}
			if (currentMinute == minuteLimit) {
				beforeLimit = false;
			}
		}

		for (int i = 0; i < 15; i++) {
			scheduleCurrentChangeRequest[0][slot * 15 + i] = Math.round(100.00 * newSchedule[0][i]) / 100.00;
			scheduleCurrentChangeRequest[1][slot * 15 + i] = Math.round(100.00 * newSchedule[1][i]) / 100.00;

		}

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
	private double[][] chargeDeltaSchedule(String aenderung, double newTemperature, boolean firstSchedule) {
		int change = 0;
		int minuteChange = DateTime.parse(aenderung).get(Calendar.MINUTE);
		double[][] deltaSchedule = new double[2][15 * numSlots];

		GregorianCalendar start = (GregorianCalendar) DateTime.parse(aenderung);
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

		int startMinute = DateTime.parse(timeFixed).get(Calendar.MINUTE);

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
	 * Berechnet wie viel Prozent des aktuellen eex-Preises für das Lastprofil
	 * zu scheduleMinutes maximal gezahlt werden soll. Das Ergebnis ist gleich
	 * 1% , wenn die aktuelle Temperatur <= minTemp1 und gleich 100 %, wenn die
	 * aktuelle Temperatur >= maxTemp1.
	 * 
	 * @return Wert, der angibt, wie viel Prozent des aktuellen eex-Preises für
	 *         das Lastprofil zu scheduleMinutes maximal gezahlt werden soll
	 */
	private double chargePriceScheduleMinutes() {
		double startTemp = scheduleMinutes[1][0];
		double maxPrice = Math.max(0, priceEex);
		double minPrice = Math.min(0, priceEex);
		if (startTemp <= minTemp1) {
			return priceEex * 0.01;
		} else if (startTemp >= maxTemp1) {
			return maxPrice;
		} else {
			double span = maxTemp1 - minTemp1;
			startTemp = startTemp - minTemp1;
			return (Math.round(100.00 * (startTemp / span)) / 100.00) * maxPrice + minPrice;
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
	private double[] createValuesLoadprofile(double[] schedule) {
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

	public void confirmLoadprofile(String time) {
		if (timeFixed.equals(time)) {
			saveSchedule(scheduleMinutes, timeFixed);
			sendNewLoadprofile();
		}
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
	 * Liefert den Fahrplan, der zur gewuenschten Zeit startet
	 * 
	 * @param start
	 *            Zeit, zu der der Fahrplan starten soll
	 * @return Fahrplan, der zur gewuenschten Zeit startet
	 */
	private double[][] getSchedule(GregorianCalendar start) {
		double[][] schedule = new double[2][15 * numSlots];
		/*
		 * Wenn start timeFixed entspricht, ergibt sich die Änderung für
		 * scheduleMinutes
		 */
		if (DateTime.ToString(start).equals(timeFixed)) {
			for (int i = 0; i < 15 * numSlots; i++) {
				double cons = scheduleMinutes[0][i];
				double temp = scheduleMinutes[1][i];
				schedule[0][i] = cons;
				schedule[1][i] = temp;
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
			}
			start.add(Calendar.HOUR_OF_DAY, -1);
		}
		return schedule;
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

	@Override
	public void receiveAnswerChangeRequest(boolean acceptChange) {
		if (acceptChange) {
			scheduleMinutes = scheduleCurrentChangeRequest;
		}

		// Erstelle das aktuelle Deltalastprofil, wenn während des Wartens auf
		// die Antwort eine Temperaturänderung war
		if (waitToChargeDeltaLoadprofile) {
			GregorianCalendar date = (GregorianCalendar) DateTime.parse(timeFixed);
			date.add(Calendar.HOUR_OF_DAY, 1);
			double newTemperature = schedulesFixed.get(date)[15 * numSlots - 1];

			double[][] deltaSchedule = chargeDeltaSchedule(timeFixed, newTemperature, false);
			double[] newValues = createValuesLoadprofile(deltaSchedule[0]);
			double[] oldValues = scheduleMinutes[0];
			double[] deltaValues = new double[numSlots * 15];

			boolean change = false;
			for (int i = 0; i < 4; i++) {
				deltaValues[i] = newValues[i] - oldValues[i];
				if (deltaValues[i] != 0) {
					change = true;
				}
			}
			if (change) {
				// Versende deltaValues als Delta-Lastprofil an den Consumer
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, timeFixed, Loadprofile.Type.DELTA);
				sendLoadprofileToConsumer(deltaLoadprofile);

				// Abspeichern des neuen Lastprofils
				loadprofilesFixed.put(timeFixed, newValues);
			} else {
				// keine änderung
			}
			waitToChargeDeltaLoadprofile = false;
		}
		scheduleCurrentChangeRequest = new double[2][15 * numSlots];

		waitForAnswerCR = false;
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
	 * Speichert den uebergebenen Fahrplan als festen Fahrplan und festes
	 * Lastprofil ab
	 * 
	 * @param schedule
	 *            Fahrplan, der abgespeichert werden soll
	 * @param start
	 *            Zeitpunkt, zu dem schedule startet
	 */
	private void saveSchedule(double[][] schedule, String start) {
		int size = 15 * numSlots;

		for (int i = 0; i < size; i++) {
			double[] values = { schedule[0][i], schedule[1][i] };
			schedulesFixed.put(start, values);
			simulationFridge.addNewValues(start, values);
			DateTime.add(Calendar.MINUTE, 1, start);
		}
		DateTime.add(Calendar.HOUR_OF_DAY, -1, start);

		double[] valuesLoadprofile = createValuesLoadprofile(schedule[0]);
		loadprofilesFixed.put(start, valuesLoadprofile);
	}

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
			timeFixed = DateTime.ToString(DateTime.now());
			timeFixed = DateTime.set(Calendar.SECOND, 0, timeFixed);
			timeFixed = DateTime.set(Calendar.MILLISECOND, 0, timeFixed);

			chargeNewSchedule();
			valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);

			timeFixed = DateTime.set(Calendar.MINUTE, 0, timeFixed);

			saveSchedule(scheduleMinutes, timeFixed);

			loadprofilesFixed.put(timeFixed, valuesLoadprofile);
		}
		// Zähle timeFixed um eine Stunde hoch
		timeFixed = DateTime.add(Calendar.HOUR_OF_DAY, 1, timeFixed);
		chargeNewSchedule();
		valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);
		double priceSugg = chargePriceScheduleMinutes();

		// Lege maxPrice fest
		double maxPrice = Math.max(0, priceEex);

		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, priceSugg, Double.NEGATIVE_INFINITY,
				maxPrice, Loadprofile.Type.INITIAL);
		sendLoadprofileToConsumer(loadprofile);
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
	public void sendDeltaLoadprofile(String aenderung, double newTemperature) {
		GregorianCalendar startLoadprofile = (GregorianCalendar) DateTime.parse(aenderung);
		startLoadprofile.set(Calendar.MINUTE, 0);
		GregorianCalendar compare = (GregorianCalendar) DateTime.parse(timeFixed);
		if (!waitForAnswerCR) {
			compare.add(Calendar.HOUR_OF_DAY, 1);
		} else {
			waitToChargeDeltaLoadprofile = true;
		}
		boolean change;
		boolean firstSchedule = true;

		aenderung = DateTime.set(Calendar.SECOND, 0, aenderung);
		aenderung = DateTime.set(Calendar.MILLISECOND, 0, aenderung);

		/*
		 * Berechne ab Zeitpunkt der Abweichung einschließlich des aktuellen
		 * Plans scheduleMinutes neue Fahrpläne, neue Lastprofile und
		 * Deltalastprofile
		 */
		while (!DateTime.ToString(startLoadprofile).equals(DateTime.ToString(compare))) {
			System.out.println("Berechne neuen Fahrplan");
			// Berechne neuen Fahrplan
			double[][] deltaSchedule = chargeDeltaSchedule(aenderung, newTemperature, firstSchedule);

			saveSchedule(deltaSchedule, DateTime.ToString(startLoadprofile));

			firstSchedule = false;
			newTemperature = deltaSchedule[1][15 * numSlots - 1];

			double[] newValues = createValuesLoadprofile(deltaSchedule[0]);
			aenderung = DateTime.set(Calendar.MINUTE, 0, aenderung);
			double[] oldValues;

			if (DateTime.ToString(startLoadprofile).equals(timeFixed)) {
				oldValues = createValuesLoadprofile(scheduleMinutes[0]);
			} else {
				oldValues = loadprofilesFixed.get(DateTime.ToString(startLoadprofile));
			}

			double[] deltaValues = new double[4];

			if (oldValues == null) {
				Log.e(this.uuid, "Start: " + DateTime.ToString(startLoadprofile) + " - Alle Loadprofiles Fixed:"
						+ loadprofilesFixed.keySet());
				return;
			}

			change = false;
			for (int i = 0; i < 4; i++) {
				deltaValues[i] = newValues[i] - oldValues[i];
				if (deltaValues[i] != 0) {
					change = true;
				}
			}
			if (change) {
				System.out.println("Versende Delta-LP");
				// Versende deltaValues als Delta-Lastprofil an den Consumer
				Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, DateTime.ToString(startLoadprofile),
						Loadprofile.Type.DELTA);
				sendLoadprofileToConsumer(deltaLoadprofile);

				// Abspeichern des neuen Lastprofils
				loadprofilesFixed.put(DateTime.ToString(startLoadprofile), newValues);
			} else {
				// keine änderung
			}
			startLoadprofile.add(Calendar.HOUR_OF_DAY, 1);
			aenderung = DateTime.add(Calendar.HOUR_OF_DAY, 1, aenderung);
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
		if (!status.equals(DeviceStatus.READY)) {
			return;
		}

		GregorianCalendar currentTime = DateTime.now();
		currentTime.set(Calendar.SECOND, 0);
		currentTime.set(Calendar.MILLISECOND, 0);

		double tempPlanned, tempScaled;

		double[] temps = schedulesFixed.get(DateTime.ToString(currentTime));
		if (temps == null) {
			return;
		}
		tempPlanned = temps[1];
		tempScaled = simulationFridge.getTemperature(currentTime);

		if (tempPlanned != tempScaled) {
			sendDeltaLoadprofile(DateTime.ToString(currentTime), tempScaled);
		}
	}
}