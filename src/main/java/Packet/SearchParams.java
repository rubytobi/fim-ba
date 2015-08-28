package Packet;

import java.util.GregorianCalendar;

import Util.DateTime;

public class SearchParams implements Cloneable {
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

	public SearchParams clone() {
		return new SearchParams((GregorianCalendar) date.clone(), minPrice, maxPrice);
	}

}
