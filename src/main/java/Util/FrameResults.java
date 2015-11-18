package Util;

import javax.swing.*;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.awt.*;

import Util.MatchedOffers;
import Util.ConfirmedOffer;

public class FrameResults {
	JFrame frame;
	JPanel kriteria11, kriteria12, kriteria2, kriteria3;
	JPanel results11, results12, results2, results3;
	JTextArea head11, head12, head2, head3;

	public FrameResults(GregorianCalendar time, ArrayList<MatchedOffers> justMatched,
			ArrayList<ConfirmedOffer> justConfirmed, ArrayList<ConfirmedOffer> lastConfirmed, double maxDeviation,
			boolean changes, int countRemainingOffers, double[] deviationWithoutChange, double[] deviationWithChange,
			ArrayList<double[]> allChanges) {
		frame = new JFrame("Ergebnisse für " + calendarToString(time, false));

		// frame.setLocation(300, 300);
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;

		// Erstelle alle Informationen für Kriterium 1-1
		kriteria11 = new JPanel(new BorderLayout());
		head11 = new JTextArea();
		head11.append(
				"Kriterium 1-1: \nAngebot und Nachfrage werden so zusammengeführt, dass eine Annäherung an die Prognose erfolgt");
		head11.setBackground(Color.LIGHT_GRAY);
		Font fontHead = new Font("Verdana", Font.BOLD, 14);
		head11.setFont(fontHead);
		// Schreibe alle Results in die TextArea
		JTextArea testResult11 = new JTextArea();
		Font font = new Font("Verdana", Font.PLAIN, 14);
		testResult11.setFont(font);
		int countAll;
		if (justMatched.size() == 0) {
			testResult11
					.append("Keine zusammengeführten Angebote für diesen Slot (" + calendarToString(time, false) + ")");
		} else {
			testResult11.setRows(justMatched.size() + 4);
			countAll = justMatched.size();
			int countSmaller = 0;
			int countOk = 0;
			for (MatchedOffers matched : justMatched) {
				double before = matched.getDeviationBefore();
				double after = matched.getDeviationAfter();
				boolean smaller = after < before;
				if (smaller) {
					countSmaller++;
					testResult11.append("Abweichung geringer. (Abweichung davor: " + matched.getDeviationBefore()
							+ ", danach: " + matched.getDeviationAfter() + ")\n");
				} else {
					boolean ok = after < before + maxDeviation;
					if (ok) {
						countOk++;
						testResult11.append("Abweichung ok.       (Abweichung davor: " + matched.getDeviationBefore()
								+ ", danach: " + matched.getDeviationAfter() + ")\n");
					} else {
						testResult11.append("Abweichung höher.    (Abweichung davor: " + matched.getDeviationBefore()
								+ ", danach: " + matched.getDeviationAfter() + ")\n");
					}
				}
			}
			// Gib prozentuales Ergebnis aus
			double percentageSmaller = Math.round(100.00 * (countSmaller / countAll));
			double percentageOk = Math.round(100.00 * (countOk / countAll));

			testResult11.append("\n" + percentageSmaller + " % der Zusammenführungen: Geringere Abweichung");
			testResult11.append(percentageOk + " % der Zusammenführungen: Leicht höhere Abweichung\n");
			testResult11.append((1 - (percentageSmaller + percentageOk)) / countAll
					+ " % der Zusammenführungen: Höhere Abweichung");
		}
		// Erstelle ein ScrollPane
		JScrollPane sp11 = new JScrollPane(testResult11);
		// Füge alles in das jeweilige Panel ein
		kriteria11.add(BorderLayout.NORTH, head11);
		kriteria11.add(sp11);

		// Erstelle alle Informationen für Kriterium 1-2
		kriteria12 = new JPanel(new BorderLayout());
		head12 = new JTextArea();
		head12.append("Kriterium 1-2: \nZusammenführung führt zu Zahlungsfluss ohne Differenz");
		head12.setBackground(Color.LIGHT_GRAY);
		head12.setFont(fontHead);
		// Schreibe alle Ergebnisse in die TextArea
		JTextArea testResult12 = new JTextArea();
		font = new Font("Verdana", Font.PLAIN, 14);
		testResult12.setFont(font);
		if (justMatched.size() == 0) {
			testResult12
					.append("Keine zusammengeführten Angebote für diesen Slot (" + calendarToString(time, false) + ")");
		} else {
			countAll = justMatched.size();
			double countGood = 0;
			for (MatchedOffers matched : justMatched) {
				// Hole die Gesamtpreise
				double allRoundPrice1 = matched.getAllRoundPrice1();
				double allRoundPrice2 = matched.getAllRoundPrice2();

				// Prüfe, ob Summe der Gesamtpreise = 0
				double sum = allRoundPrice1 + allRoundPrice2;

				// Gib das Ergebnis aus
				if (sum == 0) {
					countGood++;
					System.out.println("Gesamtpreise passen     : Summe = " + sum + " (GP1: " + allRoundPrice1
							+ ", GP2: " + allRoundPrice2 + "\n");
				} else {
					System.out.println("Geamtpreise passen nicht: Summe = " + sum + " (GP1: " + allRoundPrice1
							+ ", GP2: " + allRoundPrice2 + "\n");
				}
			}

			// Gib prozentuales Ergebnis aus
			double percentageGood = Math.round(100.00 * (countGood / countAll));
			testResult12.append("\n" + percentageGood + " % der Gesamtpreise haben zusammengepasst\n");
			testResult12.append((100 - percentageGood) + " % der Gesamtpreise haben nicht zusammengepasst");
		}
		// Erstelle ein ScrollPane
		JScrollPane sp12 = new JScrollPane(testResult12);
		// Füge alles in das jeweilige Panel ein
		kriteria12.add(BorderLayout.NORTH, head12);
		kriteria12.add(sp12);

		// Erstelle alle Informationen für Kriterium 2
		kriteria2 = new JPanel(new BorderLayout());
		head2 = new JTextArea();
		head2.append("Kriterium 2: \nJedes Angebot wird vor dessen Beginn bestätigt");
		head2.setFont(fontHead);
		head2.setBackground(Color.LIGHT_GRAY);
		// TODO Schreibe alle Results in die TextArea
		JTextArea testResult2 = new JTextArea();
		font = new Font("Verdana", Font.PLAIN, 14);
		testResult2.setFont(font);
		if (lastConfirmed.size() == 0) {
			testResult2.append("Keine bestätigten Angebote für diesen Slot (" + calendarToString(time, true) + ")");
		} else {
			countAll = lastConfirmed.size();
			int countGood = 0;
			int countSecondsTooLate = 0;
			for (ConfirmedOffer confirmed : lastConfirmed) {
				// Hole, wann das Angebot bestätigt wurde und wann es beginnt
				GregorianCalendar timeConfirmed = confirmed.getTimeConfirmed();
				GregorianCalendar timeRealStart = confirmed.getRealStartOffer();

				// Prüfe, ob Angebot vor Start bestätigt wurde
				boolean good = timeConfirmed.before(timeRealStart);
				if (good) {
					countGood++;
					testResult2.append("Bestätigung vor Start  (Bestätigung: " + calendarToString(timeConfirmed, false)
							+ ", Start: " + calendarToString(timeRealStart, false) + ")\n");
				} else {
					testResult2.append("Bestätigung nach Start (Bestätigung: " + calendarToString(timeConfirmed, false)
							+ ", Start: " + calendarToString(timeRealStart, false) + ")\n");
					int differenceInSeconds = (int) (timeRealStart.getTimeInMillis() - timeConfirmed.getTimeInMillis());
					countSecondsTooLate += differenceInSeconds;
				}
			}

			// Gib prozentuales Ergebnis aus
			double percentageGood = Math.round(100.00 * (countGood / countAll));
			testResult2.append("\n" + percentageGood + " % der Bestätigungen waren rechtzeitig\n");
			testResult2.append((100 - percentageGood) + " % der Bestätigungen waren zu spät");

			testResult2.append("\n Die Verspätung insgesamt betrug " + countSecondsTooLate
					+ " sek, also im Durchschnitt " + countSecondsTooLate / countAll + " sek pro Angebot.");
		}
		// Erstelle ein ScrollPane
		JScrollPane sp2 = new JScrollPane(testResult2);
		// Füge alles in das jeweilige Panel ein
		kriteria2.add(BorderLayout.NORTH, head2);
		kriteria2.add(sp2);

		// Erstelle alle Informationen für Kriterium 3
		kriteria3 = new JPanel(new BorderLayout());
		head3 = new JTextArea();
		head3.append(
				"Kriterium 3: \nDie Abweichung von der Prognose kann durch die Anpassung von Geräten verringert werden.");
		head3.setBackground(Color.LIGHT_GRAY);
		head3.setFont(fontHead);
		// TODO Schreibe alle Results in die TextArea
		JTextArea testResult3 = new JTextArea();
		font = new Font("Verdana", Font.PLAIN, 14);
		testResult3.setFont(font);
		if (changes) {
			testResult3.append("Anzahl an Verbliebenen Angeboten: " + countRemainingOffers);
			testResult3.append("\nAbweichung vor Anpassungen:\n" + valuesToString(deviationWithoutChange));
			testResult3.append("\nAbweichungen nach Anpassungen:\n" + valuesToString(deviationWithChange));
			testResult3.append("\nAlle " + allChanges.size() + " Änderungen:");
			for (double[] currentChanges : allChanges) {
				testResult3.append("\n" + valuesToString(currentChanges));
			}
		} else {
			testResult3.append("Es gab keine Anpassungen in diesem Slot (" +calendarToString(time, false));
		}
		// Verringerung in Prozent
		testResult3.append("\nVerringerung in Prozent: ");
		double less1 = Math.round(100.00 * (deviationWithoutChange[0] - deviationWithChange[0]) / deviationWithoutChange[0]);
		double less2 = Math.round(100.00 * (deviationWithoutChange[1] - deviationWithChange[1]) / deviationWithoutChange[1]);
		double less3 = Math.round(100.00 * (deviationWithoutChange[2] - deviationWithChange[2]) / deviationWithoutChange[2]);
		double less4 = Math.round(100.00 * (deviationWithoutChange[3] - deviationWithChange[3]) / deviationWithoutChange[3]);
		testResult3.append("\n	Slot1: " + less1 + " %");
		testResult3.append("\n	Slot2: " + less2 + " %");
		testResult3.append("\n	Slot3: " + less3 + " %");
		testResult3.append("\n	Slot4: " + less4 + " %");
		double deviationWithSum = deviationWithChange[0] + deviationWithChange[1] + deviationWithChange[2] + deviationWithChange[3];
		double deviationWithoutSum = deviationWithoutChange[0] + deviationWithoutChange[1] + deviationWithoutChange[2] + deviationWithoutChange[3];
		testResult3.append("\nGesamt: " + Math.round(100.00 * ((deviationWithoutSum-deviationWithSum)/deviationWithoutSum)) + " %");
		// Erstelle ein ScrollPane
		JScrollPane sp3 = new JScrollPane(testResult3);
		// Füge alles in das jeweilige Panel ein
		kriteria3.add(BorderLayout.NORTH, head3);
		kriteria3.add(sp3);

		// Füge alle Panels zum Fenster hinzu
		c.gridx = 0;
		c.gridy = 0;
		frame.add(kriteria11, c);
		c.gridx = 0;
		c.gridy = 1;
		frame.add(kriteria12, c);
		c.gridx = 0;
		c.gridy = 2;
		frame.add(kriteria2, c);
		c.gridx = 0;
		c.gridy = 3;
		frame.add(kriteria3, c);

		// Zeige den Frame an
		frame.pack();
		frame.setVisible(true);

	}

	private String valuesToString(double[] values) {
		String s = "	";
		for (int i = 0; i < values.length; i++) {
			s = s + "[" + values[i] + "]";
		}
		return s;
	}

	private String calendarToString(GregorianCalendar calendar, boolean lessHours) {
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		if (!lessHours) {
			hour++;
		}
		int minutes = calendar.get(Calendar.MINUTE);
		int seconds = calendar.get(Calendar.SECOND);
		int month = calendar.get(Calendar.MONTH) + 1;
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int year = calendar.get(Calendar.YEAR);

		String s = "";
		if (hour < 10) {
			s += "0";
		}
		s += hour + ":";
		if (minutes < 10) {
			s += "0";
		}
		s += minutes + ":";
		if (seconds < 10) {
			s += "0";
		}
		s += seconds + " Uhr - ";
		if (day < 10) {
			s += "0";
		}
		s += day + ".";
		if (month < 10) {
			s += "0";
		}
		s += month + "." + year;
		return s;
	}
}
