package Requests;

import java.io.Serializable;

public class DevicePutRequest implements Serializable {
	private static final long serialVersionUID = 1L;
	private String name;

	public String getName() {
		return name;
	}
}
