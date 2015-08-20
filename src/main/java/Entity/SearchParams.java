package Entity;

import java.util.GregorianCalendar;

import Util.DateTime;

public class SearchParams {
	private double maxPrice;
	private double minPrice;
	private GregorianCalendar date;

	public SearchParams() {
		// dummy
	}

	public SearchParams(GregorianCalendar date, double minPrice, double maxPrice) {
		this.date = date;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
	}

	public double getMaxPrice() {
		return maxPrice;
	}

	public double getMinPrice() {
		return minPrice;
	}

	public GregorianCalendar getDate() {
		return date;
	}

	public String toString() {
		return "SearchParams [date=" + DateTime.ToString(date) + ",minPrice=" + minPrice + ",maxPrice=" + maxPrice
				+ "]";
	}

}
