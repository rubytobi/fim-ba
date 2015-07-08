package Util;

public class Log {
	public static void i(String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.out.println("#info\t" + string + "\t" + s);
	}

	public static void d(String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.out.println("#debug\t" + string + "\t" + s);
	}

	public static void e(String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		// remove package name
		string = string.substring(string.indexOf(".") + 1);

		// remove class name
		string = string.substring(string.indexOf(".") + 1);

		System.err.println("#error\t" + string + "\t" + s);
	}
}
