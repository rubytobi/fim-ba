package Requests;

import java.io.Serializable;

public class DevicePutRequest implements Serializable {
	private static final long serialVersionUID = 1L;
	private String name;
	private String className;

	public String getName() {
		return name;
	}

	public String getClassName() {
		return className;
	}
}
