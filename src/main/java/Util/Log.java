package Util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Log {

	private static Log instance;
	private boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
			.indexOf("jdwp") >= 0;

	public boolean isDebug() {
		return isDebug;
	}

	private Log() {
		// dummy
	}

	public static Log instance() {
		if (instance == null) {
			instance = new Log();
		}

		return instance;
	}

	public static void d(UUID uuid, String s) {
		if (!new Log().isDebug()) {
			return;
		}

		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		ErrorLog errorLog = new ErrorLog(uuid, s, string);

		apendToSystemOut(errorLog, false);

		ObjectMapper mapper = new ObjectMapper();
		try {
			s = mapper.writeValueAsString(errorLog);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		apendToFile(s);
	}

	private static void apendToSystemOut(ErrorLog errorLog, boolean error) {
		String username = System.getProperty("user.name"); // platform
															// independent

		if (!username.equals("Tobias")) {
			if (error)
				System.err.println(errorLog.toString());
			else
				System.out.println(errorLog.toString());
			return;
		}
	}

	private static void apendToFile(String s) {
		String username = System.getProperty("user.name"); // platform
															// independent

		if (!username.equals("Tobias")) {
			return;
		}

		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("c:/fim-ba/logfile.log", true)))) {
			out.println(s);
		} catch (IOException e) {
			// exception handling left as an exercise for the reader
		}
	}

	public static void e(UUID uuid, String s) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		String string = stack[2].toString();

		ErrorLog errorLog = new ErrorLog(uuid, s, string);

		apendToSystemOut(errorLog, true);

		ObjectMapper mapper = new ObjectMapper();
		try {
			s = mapper.writeValueAsString(errorLog);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		apendToFile(s);
	}

	public static void evaluate(String a, String b) {
		String username = System.getProperty("user.name"); // platform
		// independent

		if (!username.equals("Tobias")) {
			// nur für Tobi machen
			return;
		}

		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("c:/fim-ba/evaluate.log", true)))) {
			out.println(DateTime.ToString(DateTime.now()) + "\t" + a + "\t" + b);
		} catch (IOException e) {
			// exception handling left as an exercise for the reader
		}
	}
}
