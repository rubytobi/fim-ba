package Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class Scorecard {
	private List<Score> allScores = new ArrayList<Score>();
	private TreeMap<Double, ArrayList<Score>> priceList = new TreeMap<Double, ArrayList<Score>>();
	private TreeMap<Double, ArrayList<Score>> loadprofileList = new TreeMap<Double, ArrayList<Score>>();

	private boolean sort = false;
	private boolean isEmpty = true;;

	public Scorecard() {

	}

	public void add(Score score) {
		allScores.add(score);

		if (!priceList.containsKey(score.getPriceDeviation())) {
			priceList.put(score.getPriceDeviation(), new ArrayList<Score>());
		}

		priceList.get(score.getPriceDeviation()).add(score);

		if (!loadprofileList.containsKey(score.getLoadprofileDeviation())) {
			loadprofileList.put(score.getLoadprofileDeviation(), new ArrayList<Score>());
		}

		loadprofileList.get(score.getLoadprofileDeviation()).add(score);

		isEmpty = false;
		sort = false;
	}

	public Score first() {
		if (isEmpty()) {
			return null;
		}

		if (!sort) {
			sort();
		}

		return allScores.get(0);
	}

	private HashMap<Score, Double> sort() {
		HashMap<Score, Double> list = new HashMap<Score, Double>();

		double i = 0.0;
		for (ArrayList<Score> a : priceList.values()) {
			i++;

			for (Score s : a) {
				list.put(s, list.getOrDefault(s, 0.0) + i);
			}

		}

		i = 0;
		for (ArrayList<Score> a : loadprofileList.values()) {
			i++;

			for (Score s : a) {
				list.put(s, list.getOrDefault(s, 0.0) + i);
			}
		}

		Collections.sort(allScores, new Comparator<Score>() {

			@Override
			public int compare(Score o1, Score o2) {
				int i = Double.compare(list.get(o1), list.get(o2));

				switch (i) {
				case 0:
					return Boolean.compare(o1.hasChangeRequest(), o2.hasChangeRequest());

				default:
					return i;
				}
			}
		});

		sort = true;

		return list;
	}

	public boolean isEmpty() {
		return isEmpty;
	}

	public String toString() {
		return "Scorecard [first=" + first() + ",map=" + sort() + "]";
	}

}
