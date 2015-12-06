package Util;

import javax.swing.*;

import Entity.Offer;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.awt.*;

import Util.MatchedOffers;
import Util.ConfirmedOffer;

public class FrameResults {
	private JFrame frame;
	private JTabbedPane tabs;
	private JPanel tab1, tab2;
	private JPanel kriteria1, kriteria2, kriteria3, kriteria4;
	private JTextArea head1, head2, head3, head4;
	private JTextArea fulfill1, fulfill2, fulfill3, fulfill4;
	private JTextArea testResult1, testResult2, testResult3, testResult4;
	private JPanel supplyPanel, demandPanel;
	private JTextArea headSupply, headDemand;
	private JTextArea offersSupply, offersDemand;

	private GregorianCalendar time;
	private ArrayList<MatchedOffers> justMatched;
	private ArrayList<ConfirmedOffer> lastConfirmed;
	private double maxDeviation;
	private boolean changes;
	private int countRemainingOffers;
	private double[] deviationWithoutChange;
	private double[] deviationWithChange;
	private ArrayList<double[]> allChanges;
	private HashMap<String, ArrayList<Offer>> demand, supply;

	public FrameResults(GregorianCalendar time, ArrayList<MatchedOffers> justMatched,
			ArrayList<ConfirmedOffer> lastConfirmed, double maxDeviation, boolean changes, int countRemainingOffers,
			double[] deviationWithoutChange, double[] deviationWithChange, ArrayList<double[]> allChanges,
			HashMap<String, ArrayList<Offer>> demand, HashMap<String, ArrayList<Offer>> supply) {
		// Setze alle Werte
		this.time = time;
		this.justMatched = justMatched;
		this.lastConfirmed = lastConfirmed;
		this.maxDeviation = maxDeviation;
		this.changes = changes;
		this.countRemainingOffers = countRemainingOffers;
		this.deviationWithoutChange = deviationWithoutChange;
		this.deviationWithChange = deviationWithChange;
		this.allChanges = allChanges;
		this.demand = demand;
		this.supply = supply;

		// Erstelle Fenster und Tabs
		frame = new JFrame("Ergebnisse für " + calendarToString(this.time, false));
		tabs = new JTabbedPane();

		fillTab1();
		fillTab2();

		// Füge Tabs zur Tableiste hinzu
		tabs.addTab("Ergebnisse der Kriterien", tab1);
		tabs.addTab("Angebote auf dem Marktplatz", tab2);

		// Füge Tableiste zum Frame hinzu
		frame.add(tabs);

		// Zeige den Frame an
		frame.pack();
		frame.setVisible(true);
	}

	private void fillTab1() {
		// Erstelle Panel für Tab1
		tab1 = new JPanel();
		tab1.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		// c.fill = GridBagConstraints.BOTH;

		// Lege Schrift für Kriterien und Ergebnisse fest
		Font fontHead = new Font("Verdana", Font.BOLD, 14);
		Font fontResults = new Font("Verdana", Font.PLAIN, 14);

		// Erstelle alle Informationen für Kriterium 1
		kriteria1 = new JPanel(new BorderLayout());
		head1 = new JTextArea();
		head1.append(
				"Kriterium 1: \nAngebot und Nachfrage werden so zusammengeführt, dass eine Annäherung an die Prognose erfolgt");
		head1.setBackground(Color.LIGHT_GRAY);
		head1.setFont(fontHead);
		// Schreibe alle Results in die TextArea
		setTextAreaKriteria1();
		testResult1.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane sp1 = new JScrollPane(testResult1);
		// Füge alles in das jeweilige Panel ein
		kriteria1.add(BorderLayout.NORTH, head1);
		kriteria1.add(sp1);
		kriteria1.add(BorderLayout.SOUTH, fulfill1);

		// Erstelle alle Informationen für Kriterium 1-2
		kriteria2 = new JPanel(new BorderLayout());
		head2 = new JTextArea();
		head2.append("Kriterium 2: \nZusammenführung führt zu Zahlungsfluss ohne Differenz");
		head2.setBackground(Color.LIGHT_GRAY);
		head2.setFont(fontHead);
		// Schreibe alle Ergebnisse in die TextArea
		setTextAreaKriteria2();
		testResult2.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane sp2 = new JScrollPane(testResult2);
		// Füge alles in das jeweilige Panel ein
		kriteria2.add(BorderLayout.NORTH, head2);
		kriteria2.add(sp2);
		kriteria2.add(BorderLayout.SOUTH, fulfill2);

		// Erstelle alle Informationen für Kriterium 3
		kriteria3 = new JPanel(new BorderLayout());
		head3 = new JTextArea();
		head3.append("Kriterium 3: \nJedes Angebot wird vor dessen Beginn bestätigt \n"
				+ "(Betrachtet werden alle Angebote, die seit der letzten Betrachtung bestätigt wurden");
		head3.setFont(fontHead);
		head3.setBackground(Color.LIGHT_GRAY);
		// Schreibe alle Results in die TextArea
		setTextAreaKriteria3();
		testResult3.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane sp3 = new JScrollPane(testResult3);
		// Füge alles in das jeweilige Panel ein
		kriteria3.add(BorderLayout.NORTH, head3);
		kriteria3.add(sp3);
		kriteria3.add(BorderLayout.SOUTH, fulfill3);

		// Erstelle alle Informationen für Kriterium 4
		kriteria4 = new JPanel(new BorderLayout());
		head4 = new JTextArea();
		head4.append(
				"Kriterium 4: \nDie Abweichung von der Prognose kann durch die Anpassung von Geräten verringert werden.");
		head4.setBackground(Color.LIGHT_GRAY);
		head4.setFont(fontHead);
		// Schreibe alle Results in die TextArea
		setTextAreaKriteria4();
		testResult4.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane sp4 = new JScrollPane(testResult4);
		// Füge alles in das jeweilige Panel ein
		kriteria4.add(BorderLayout.NORTH, head4);
		kriteria4.add(sp4);
		kriteria4.add(BorderLayout.SOUTH, fulfill4);

		// Füge alle Panels zum 1. Tab hinzu
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 0.2;
		// Kriterium 11
		c.gridx = 0;
		c.gridy = 0;
		tab1.add(kriteria1, c);
		// Kriterium 12
		c.gridx = 0;
		c.gridy = 1;
		tab1.add(kriteria2, c);
		c.fill = GridBagConstraints.BOTH;
		// Kriterium 2
		c.weighty = 1;
		c.gridx = 0;
		c.gridy = 2;
		tab1.add(kriteria3, c);
		// Kriterium 3
		c.gridx = 0;
		c.gridy = 3;
		tab1.add(kriteria4, c);

	}

	private void fillTab2() {
		// Erstelle Panel für Tab2
		tab2 = new JPanel();
		tab2.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		// Lege Schrift für Kriterien und Ergebnisse fest
		Font fontHead = new Font("Verdana", Font.BOLD, 14);
		Font fontResults = new Font("Verdana", Font.PLAIN, 14);

		// Erstelle Panel für Demand
		demandPanel = new JPanel(new BorderLayout());
		headDemand = new JTextArea();
		headDemand.append("Nachfrage");
		headDemand.setBackground(Color.LIGHT_GRAY);
		headDemand.setFont(fontHead);
		// Schreibe alle Results in die TextArea
		setTextAreaDemand();
		offersDemand.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane spDemand = new JScrollPane(offersDemand);
		// Füge alles in das jeweilige Panel ein
		demandPanel.add(BorderLayout.NORTH, headDemand);
		demandPanel.add(spDemand);

		// Erstelle Panel für Demand
		supplyPanel = new JPanel(new BorderLayout());
		headSupply = new JTextArea();
		headSupply.append("Angebot");
		headSupply.setBackground(Color.LIGHT_GRAY);
		headSupply.setFont(fontHead);
		// Schreibe alle Results in die TextArea
		setTextAreaSupply();
		offersSupply.setFont(fontResults);
		// Erstelle ein ScrollPane
		JScrollPane spSupply = new JScrollPane(offersSupply);
		// Füge alles in das jeweilige Panel ein
		supplyPanel.add(BorderLayout.NORTH, headSupply);
		supplyPanel.add(spSupply);

		// Füge Demand und Supply zum 2. Tab hinzu
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		// Demand
		c.gridx = 0;
		c.gridy = 0;
		tab2.add(demandPanel, c);
		// Supply
		c.gridx = 0;
		c.gridy = 1;
		tab2.add(supplyPanel, c);
	}

	private void setTextAreaDemand() {
		offersDemand = new JTextArea();
		Set<String> demandSet = demand.keySet();
		if (demandSet.size() == 0) {
			offersDemand.append("Keine Nachfrage");
		} else {
			for (String date : demandSet) {
				offersDemand.append(calendarToString(DateTime.stringToCalendar(date), true));
				ArrayList<Offer> offersAtDate = demand.get(date);
				if (offersAtDate.size() == 0) {
					offersDemand.append("\n	Keine Nachfrage für diese Zeit");
				}
				for (Offer offer : offersAtDate) {
					double[] values = offer.getAggLoadprofile().getValues();
					double price = Math.round(100.00 * offer.getPriceSugg()) / 100.00;
					offersDemand.append("\n	Price: " + price + "	Values: " + valuesToString(values));
				}
				offersDemand.append("\n");
			}
		}
	}

	private void setTextAreaSupply() {
		offersSupply = new JTextArea();
		Set<String> supplySet = supply.keySet();
		if (supplySet.size() == 0) {
			offersSupply.append("Kein Angebot");
		} else {
			for (String date : supplySet) {
				offersSupply.append(calendarToString(DateTime.stringToCalendar(date), true));
				ArrayList<Offer> offersAtDate = supply.get(date);
				if (offersAtDate.size() == 0) {
					offersSupply.append("\n	Kein Angebot für diese Zeit");
				}
				for (Offer offer : offersAtDate) {
					double[] values = offer.getAggLoadprofile().getValues();
					double price = Math.round(100.00 * offer.getPriceSugg()) / 100.00;
					offersSupply.append("\n	Price: " + price + "	Values: " + valuesToString(values));
				}
				offersSupply.append("\n");
			}
		}
	}

	private void setTextAreaKriteria1() {
		testResult1 = new JTextArea();
		int countAll;
		double result = 0;
		if (justMatched.size() == 0) {
			testResult1
					.append("Keine zusammengeführten Angebote für diesen Slot (" + calendarToString(time, false) + ")");
		} else {
			testResult1.setRows(justMatched.size() + 4);
			countAll = justMatched.size();
			int countSmaller = 0;
			int countOk = 0;
			for (MatchedOffers matched : justMatched) {
				double before = matched.getDeviationBefore();
				double after = matched.getDeviationAfter();
				boolean smaller = after < before;
				if (smaller) {
					countSmaller++;
					testResult1.append("Abweichung geringer. (Abweichung davor: " + matched.getDeviationBefore()
							+ ", danach: " + matched.getDeviationAfter() + ")\n");
				} else {
					boolean ok = after < before + maxDeviation;
					if (ok) {
						countOk++;
						testResult1.append("Abweichung ok.       (Abweichung davor: " + matched.getDeviationBefore()
								+ ", danach: " + matched.getDeviationAfter() + ")\n");
					} else {
						result++;
						testResult1.append("Abweichung höher.    (Abweichung davor: " + matched.getDeviationBefore()
								+ ", danach: " + matched.getDeviationAfter() + ")\n");
					}
				}
			}
			// Gib prozentuales Ergebnis aus
			double percentageSmaller = Math.round(100.00 * (countSmaller / countAll));
			double percentageOk = Math.round(100.00 * (countOk / countAll));

			testResult1.append("\n" + percentageSmaller + " % der Zusammenführungen: Geringere Abweichung");
			testResult1.append(percentageOk + " % der Zusammenführungen: Leicht höhere Abweichung\n");
			testResult1.append((1 - (percentageSmaller + percentageOk)) / countAll
					+ " % der Zusammenführungen: Höhere Abweichung");
		}

		// Zeige das Ergebnis an
		fulfill1 = new JTextArea();
		Font fontResults = new Font("Verdana", Font.BOLD, 14);
		fulfill1.setFont(fontResults);
		if (result == 0) {
			fulfill1.setBackground(Color.GREEN);
			fulfill1.append("Kriterium 1 erfüllt: 0 Zusammenführungen haben zu einer höheren Abweichung geführt.");
		} else {
			fulfill1.setBackground(Color.RED);
			fulfill1.append("Kriterium 1 nicht erfüllt: " +result+ " Zusammenführungen haben zu eienr höheren Abweichung geführt.");
		}
	}

	private void setTextAreaKriteria2() {
		testResult2 = new JTextArea();
		double countAll = 0;
		double result = 0;
		if (justMatched.size() == 0) {
			testResult2
					.append("Keine zusammengeführten Angebote für diesen Slot (" + calendarToString(time, false) + ")");
		} else {
			countAll = justMatched.size();
			for (MatchedOffers matched : justMatched) {
				// Hole die Gesamtpreise
				double allRoundPrice1 = matched.getAllRoundPrice1();
				double allRoundPrice2 = matched.getAllRoundPrice2();
	
				// Prüfe, ob Summe der Gesamtpreise = 0
				double sum = allRoundPrice1 + allRoundPrice2;
	
				// Gib das Ergebnis aus
				if (sum == 0) {
					result++;
					System.out.println("Gesamtpreise passen     : Summe = " + sum + " (GP1: " + allRoundPrice1
							+ ", GP2: " + allRoundPrice2 + "\n");
				} else {
					System.out.println("Geamtpreise passen nicht: Summe = " + sum + " (GP1: " + allRoundPrice1
							+ ", GP2: " + allRoundPrice2 + "\n");
				}
			}
	
			// Gib prozentuales Ergebnis aus
			double percentageGood = Math.round(100.00 * (result / countAll));
			testResult2.append("\n" + percentageGood + " % der Gesamtpreise haben zusammengepasst\n");
			testResult2.append((100 - percentageGood) + " % der Gesamtpreise haben nicht zusammengepasst");
		}
	
		// Zeige das Ergebnis an
		fulfill2 = new JTextArea();
		Font fontResults = new Font("Verdana", Font.BOLD, 14);
		fulfill2.setFont(fontResults);
		if (result == 0) {
			fulfill2.setBackground(Color.GREEN);
			fulfill2.append("Kriterium 2 erfüllt: Bei allen Zusammenführungen ist die Summe gleich 0.");
		} else {
			fulfill2.setBackground(Color.RED);
			fulfill2.append("Kriterium 2 nicht erfüllt: Bei " +result+ " Zusammenführungen ist die Summe nicht gleich 0.");
		}
	}

	private void setTextAreaKriteria3() {
		testResult3 = new JTextArea();
		double countAll = 0;
		double result = 0;
		if (lastConfirmed.size() == 0) {
			testResult3.append("Keine bestätigten Angebote für diesen Slot (" + calendarToString(time, true) + ")");
		} else {
			countAll = lastConfirmed.size();
			int countGood = 0;
			for (ConfirmedOffer confirmed : lastConfirmed) {
				// Hole, wann das Angebot bestätigt wurde und wann es beginnt
				GregorianCalendar timeConfirmed = DateTime.parse(confirmed.getTimeConfirmed());
				GregorianCalendar timeRealStart = DateTime.parse(confirmed.getRealStartOffer());

				// Prüfe, ob Angebot vor Start bestätigt wurde
				boolean good = timeConfirmed.before(timeRealStart);
				if (good) {
					countGood++;
					testResult3.append("Bestätigung vor Start: JA   (Bestätigung: "
							+ calendarToString(timeConfirmed, false) + ", Start: "
							+ calendarToString(timeRealStart, false) + ") " + confirmed.getTypeString() + "\n");
				} else {
					testResult3.append("Bestätigung vor Start: NEIN (Bestätigung: "
							+ calendarToString(timeConfirmed, false) + ", Start: "
							+ calendarToString(timeRealStart, false) + ") " + confirmed.getTypeString() + "\n");
					int differenceInMilliSeconds = (int) (timeConfirmed.getTimeInMillis()
							- timeRealStart.getTimeInMillis());
					result += Math.round(100.00 * (differenceInMilliSeconds * 0.001)) / 100.00 + 1;
				}
			}

			// Gib prozentuales Ergebnis aus
			double percentageGood = Math.round(100.00 * (countGood / countAll));
			testResult3.append(
					"\nVon den " + lastConfirmed.size() + " Bestätigungen waren " + countGood + " rechtzeitig.");
			testResult3.append("\n" + percentageGood + " % der Bestätigungen waren rechtzeitig\n");
			testResult3.append((100 - percentageGood) + " % der Bestätigungen waren zu spät");

			testResult3.append("\nDie Verspätung insgesamt betrug " + result + " sek, also im Durchschnitt "
					+ result / countAll + " sek pro Angebot.");
		}

		// Zeige das Ergebnis an
		fulfill3 = new JTextArea();
		Font fontResults = new Font("Verdana", Font.BOLD, 14);
		fulfill3.setFont(fontResults);
		if (result == 0) {
			fulfill3.setBackground(Color.GREEN);
			fulfill3.append("Kriterium 3 erfüllt: Die Verspätung betrug insgesamt 0 s");
		} else {
			fulfill3.setBackground(Color.RED);
			fulfill3.append("Kriterium 3 nicht erfüllt: Die Verspätung betrug insesamt " +result+ " s");
		}
	}

	private void setTextAreaKriteria4() {
		testResult4 = new JTextArea();
		double result = 0;
		if (changes) {
			testResult4.append("Anzahl an Verbliebenen Angeboten: " + countRemainingOffers);
			testResult4.append("\nAbweichung vor Anpassungen:\n	" + valuesToString(deviationWithoutChange));
			testResult4.append("\nAbweichungen nach Anpassungen:\n	" + valuesToString(deviationWithChange));
			testResult4.append("\nAlle " + allChanges.size() + " Anpassungen:");
			for (double[] currentChanges : allChanges) {
				testResult4.append("\n	" + valuesToString(currentChanges));
			}
			// Verringerung in Prozent
			testResult4.append("\nVerringerung in Prozent: ");
			double less1 = Math
					.round(100.00 * (deviationWithoutChange[0] - deviationWithChange[0]) / deviationWithoutChange[0]);
			double less2 = Math
					.round(100.00 * (deviationWithoutChange[1] - deviationWithChange[1]) / deviationWithoutChange[1]);
			double less3 = Math
					.round(100.00 * (deviationWithoutChange[2] - deviationWithChange[2]) / deviationWithoutChange[2]);
			double less4 = Math
					.round(100.00 * (deviationWithoutChange[3] - deviationWithChange[3]) / deviationWithoutChange[3]);
			testResult4.append("\n	Slot1: " + less1 + " %");
			testResult4.append("\n	Slot2: " + less2 + " %");
			testResult4.append("\n	Slot3: " + less3 + " %");
			testResult4.append("\n	Slot4: " + less4 + " %");
			double deviationWithSum = deviationWithChange[0] + deviationWithChange[1] + deviationWithChange[2]
					+ deviationWithChange[3];
			double deviationWithoutSum = deviationWithoutChange[0] + deviationWithoutChange[1]
					+ deviationWithoutChange[2] + deviationWithoutChange[3];
			result = Math.round(100.00 * ((deviationWithoutSum - deviationWithSum) / deviationWithoutSum));
			testResult4.append("\n	Gesamt: " + result + " %");
		} else {
			testResult4.append("Es gab keine Anpassungen in diesem Slot (" + calendarToString(time, false));
		}
	
		// Zeige das Ergebnis an
		fulfill4 = new JTextArea();
		Font fontResults = new Font("Verdana", Font.BOLD, 14);
		fulfill4.setFont(fontResults);
		if (result > 0) {
			fulfill4.setBackground(Color.GREEN);
			fulfill4.append("Kriterium 4 erfüllt: Die Abweichung konnte um " +result+ " % verringert werden.");
		} else {
			fulfill4.setBackground(Color.RED);
			fulfill4.append("Kriterium 4 nicht erfüllt: Die Abweichung konnte nicht verringert werden.");
		}
	}

	private String calendarToString(GregorianCalendar calendar, boolean lessHours) {
		int hour = calendar.get(Calendar.HOUR_OF_DAY);

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

	private String valuesToString(double[] values) {
		String s = "";
		for (int i = 0; i < values.length; i++) {
			s = s + "[" + values[i] + "]";
		}
		return s;
	}
}
