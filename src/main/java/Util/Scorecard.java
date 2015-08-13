package Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

public class Scorecard {
	private Score first = null;

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
	Comparator<Object[]> paretoComparator = new Comparator<Object[]>() {
		public int compare(Object[] arg0, Object[] arg1) {
			return Double.compare((double) arg0[0], (double) arg1[0]);
		};
	};
	TreeSet<Score> loadprofileScore = new TreeSet<Score>(loadprofileComparator);

	public Scorecard() {

	}

	public void add(Score score) {
		first = null;

		priceScore.add(score);
		loadprofileScore.add(score);
	}

	public Score first() {
		if (first == null) {
			calculateFirst();
		}

		return first;
	}

	private void calculateFirst() {
		HashMap<Score, Double> map = new HashMap<Score, Double>();

		int i = 0;
		for (Score s : loadprofileScore) {
			i++;

			map.put(s, map.getOrDefault(s, 0.0) + i);
		}
	}

}
