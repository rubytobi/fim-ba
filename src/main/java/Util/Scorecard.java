package Util;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

public class Scorecard {
	private Score first = null;
	private boolean isEmpty = true;

	Comparator<Score> priceComparator = new Comparator<Score>() {
		public int compare(Score arg0, Score arg1) {
			return Double.compare(arg0.getPriceDeviation(), arg1.getPriceDeviation());
		};
	};
	TreeSet<Score> priceScore = new TreeSet<Score>(priceComparator);

	Comparator<Score> loadprofileComparator = new Comparator<Score>() {
		public int compare(Score arg0, Score arg1) {
			return Double.compare(arg0.getLoadprofileDeviation(), arg1.getLoadprofileDeviation());
		};
	};
	TreeSet<Score> loadprofileScore = new TreeSet<Score>(loadprofileComparator);

	public Scorecard() {

	}

	public void add(Score score) {
		first = null;

		priceScore.add(score);
		loadprofileScore.add(score);

		isEmpty = false;
	}

	public Score first() {
		if (first == null) {
			calculateFirst();
		}

		return first;
	}

	private HashMap<Score, Double> calculateMap() {
		HashMap<Score, Double> map = new HashMap<Score, Double>();

		int i = 0;
		for (Score s : loadprofileScore) {
			i++;

			map.put(s, map.getOrDefault(s, 0.0) + i);
		}

		i = 0;
		for (Score s : priceScore) {
			i++;

			map.put(s, map.getOrDefault(s, 0.0) + i);
		}

		return map;
	}

	private void calculateFirst() {
		HashMap<Score, Double> map = calculateMap();

		if (map.isEmpty()) {
			return;
		}

		double min = Collections.min(map.values());
		for (Score s : map.keySet()) {
			if (min == map.get(s)) {
				first = s;
			}
		}
	}

	public boolean isEmpty() {
		return isEmpty;
	}

	public String toString() {
		return "Scorecard [map=" + calculateMap() + "]";
	}

}
